package com.anvisics;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;

import org.apache.tools.bzip2.CBZip2InputStream;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.core.xml.v0_6.impl.OsmHandler;
import org.xml.sax.SAXException;

import com.anvisics.MapPanel.IMapLocationListener;
import com.anvisics.NodeUtil.LatLon;
import com.anvisics.data.City;
import com.anvisics.data.Region;
import com.anvisics.data.Street;
import com.anvisics.data.City.CityType;


// TO implement
// 1. Full structured search for town/street/building.

/**
 * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing#Is_inside.2Foutside
 * http://wiki.openstreetmap.org/wiki/Relations/Proposed/Postal_Addresses
 * http://wiki.openstreetmap.org/wiki/Proposed_features/House_numbers/Karlsruhe_Schema#Tags (node, way)
 * 
 * 1. node  - place : country, state, region, county, city, town, village, hamlet, suburb
 *    That node means label for place ! It is widely used in OSM.
 *   
 * 2. way  - highway : primary, secondary, service. 
 *    That node means label for street if it is in city (primary, secondary, residential, tertiary, living_street), 
 *    beware sometimes that roads could outside city. Usage : often 
 *    
 *    outside city : trunk, motorway, motorway_link...
 *    special tags : lanes - 3, maxspeed - 90,  bridge
 * 
 * 3. relation - type = address. address:type : country, a1, a2, a3, a4, a5, a6, ... hno.
 *    member:node 		role=label :
 *    member:relation 	role=border :
 *    member:node		role=a1,a2... :
 * 
 * 4. node, way - addr:housenumber(+), addr:street(+), addr:country(+/-), addr:city(-) 
 * 	        building=yes
 * 
 * 5. relation - boundary=administrative, admin_level : 1, 2, ....
 * 
 * 6. node, way - addr:postcode =?
 *    relation  - type=postal_code (members way, node), postal_code=?
 *    
 * 7. node, way - amenity=?    
 *
 */
public class DataExtraction implements IMapLocationListener {
	
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException, XMLStreamException {
		new DataExtraction().testReadingOsmFile();
	}
	
	
	private static boolean parseMinsk = true;
	private static boolean parseOSM = true;

	///////////////////////////////////////////
	// 1. Reading data - preparing data for UI
	public void testReadingOsmFile() throws ParserConfigurationException, SAXException, IOException, XMLStreamException {
		
		InputStream stream ;
		if(parseMinsk){
			stream = new FileInputStream(Constants.pathToTestDataDir+"minsk_old.osm");
		} else {
//			stream = new FileInputStream(Constants.pathToTestDataDir+"belarus_2010_04_01.osm.bz2");
//			stream = new FileInputStream(Constants.pathToTestDataDir+"minsk_old.osm");
			stream = new FileInputStream(Constants.pathToTestDataDir+"minsk_2010_04_26.osm.bz2");
			if (stream.read() != 66 || stream.read() != 90)
				throw new RuntimeException(
						"The source stream must start with the characters BZ if it is to be read as a BZip2 stream.");
			else
				stream = new CBZip2InputStream(stream);
		}
		
		
		System.out.println("USED Memory " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1e6);
		long st = System.currentTimeMillis();
		

		// preloaded data
		final List<Node> places = new ArrayList<Node>();
		final Map<Long, LatLon> nodes = new HashMap<Long, LatLon>();
		final List<Entity> buildings = new ArrayList<Entity>();
		final List<Node> amenities = new ArrayList<Node>();
		
		
		// highways count
		final Map<String, Integer> mapWays = new LinkedHashMap<String, Integer>();

		if (parseOSM) {
			SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
			parser.parse(stream, new OsmHandler(new Sink() {
				@Override
				public void process(EntityContainer entityContainer) {
					if (entityContainer instanceof NodeContainer) {
						NodeContainer rc = (NodeContainer) entityContainer;
						if (NodeUtil.getTag(rc.getEntity(), "place") != null) {
							places.add(rc.getEntity());
							if (places.size() % 500 == 0) {
								System.out.println();
							}
							System.out.print("-");
						}
						nodes.put(rc.getEntity().getId(), new LatLon(rc.getEntity().getLatitude(), 
								rc.getEntity().getLongitude()));
						if (NodeUtil.getTag(entityContainer.getEntity(), "amenity") != null) {
							amenities.add((Node) entityContainer.getEntity());
						} else if (NodeUtil.getTag(entityContainer.getEntity(), "shop") != null) {
							Entity n = entityContainer.getEntity();
							n.getTags().add(new Tag("amenity", "shop"));
							amenities.add((Node) n);
						}
							
						
					} else {
						if (NodeUtil.tag(entityContainer.getEntity(), "building", "yes")) {
							Entity e = entityContainer.getEntity();
							if (NodeUtil.getTag(e, Constants.ADDR_HOUSE_NUMBER) != null
									&& NodeUtil.getTag(e, Constants.ADDR_STREET) != null) {
								buildings.add(e);
							}
						}
						if (NodeUtil.getTag(entityContainer.getEntity(), "highway") != null) {
							String h = NodeUtil.getTag(entityContainer.getEntity(), "highway");
							if(!mapWays.containsKey(h)){
								mapWays.put(h, 0);
							} 
							mapWays.put(h, mapWays.get(h) + 1);
							
						}
					}
				}

				@Override
				public void complete() {
				}

				@Override
				public void release() {
				}
			}, false));
		}
        
		System.out.println("\n"+mapWays);
        System.out.println(System.currentTimeMillis() - st);
        
        // 1. found towns !
        Region country = new Region(null);
        for (Node s : places) {
        	String place = NodeUtil.getTag(s, "place");
        	if(place == null){
        		continue;
        	}
        	if("country".equals(place)){
        		country.setEntity(s);
        	} else {
        		City registerCity = country.registerCity(s);
        		if(registerCity == null){
        			System.out.println(place + " - " + NodeUtil.getTag(s, "name"));
        		}
        	}
		}
        
        // 2. found buildings (index addresses)
        for(Entity b : buildings){
        	LatLon center ;
        	if(b instanceof Node){
        	 	center = NodeUtil.getLatLon((Node) b);
        	} else {
        		center = NodeUtil.getWeightCenter((Way) b, nodes);
        	}
        	// TODO first of all tag could be checked NodeUtil.getTag(e, "addr:city")
        	City city = country.getClosestCity(center);
        	if(city != null){
        		city.registerBuilding(center, b);
        	}
        }
        
        for(Node node : amenities){
        	country.registerAmenity(node);
        }
       
   
        runUI(country);
        
        System.out.println();
		System.out.println("USED Memory " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1e6);
		System.out.println("TIME : " + (System.currentTimeMillis() - st));
	}

	
	///////////////////////////////////////////
	// 2. Showing UI
	
	protected City selectedCity;
	
	private MapPanel mapPanel = new MapPanel(new File(Constants.pathToTestDataDir+"MinskTiles"));
	
	private DefaultMutableTreeNode amenitiesTree;
	private JTree treePlaces; 
	
	public void runUI(final Region r){
		JFrame frame = new JFrame("Tree of choose");
	    try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		DefaultMutableTreeNode root = new DataExtractionTreeNode(r.getName(), r);
		amenitiesTree = new DataExtractionTreeNode("Amenities", r);
		amenitiesTree.add(new DataExtractionTreeNode("closest", r));
		root.add(amenitiesTree);
		for(CityType t : CityType.values()){
			DefaultMutableTreeNode cityTree = new DataExtractionTreeNode(t.toString(), t);
			root.add(cityTree);
			for(City ct : r.getCitiesByType(t)){
				DefaultMutableTreeNode cityNodeTree = new DataExtractionTreeNode(ct.getName(), ct);
				cityTree.add(cityNodeTree);
				
				for(Street str : ct.getStreets()){
					DefaultMutableTreeNode strTree = new DataExtractionTreeNode(str.getName(), str);
					cityNodeTree.add(strTree);
					for(Entity e : str.getBuildings()){
						DefaultMutableTreeNode building = new DataExtractionTreeNode(NodeUtil.getTag(e, Constants.ADDR_HOUSE_NUMBER), e);
						strTree.add(building);
						
					}
				}
			}
		}
		
		
		
	    frame.addWindowListener(new ExitListener());
	    Container content = frame.getContentPane();
	    frame.setFocusable(true);
	    
	    
	    treePlaces = new JTree(root);
	    final JList jList = new JList();
	    jList.setCellRenderer(new DefaultListCellRenderer(){
			private static final long serialVersionUID = 4661949460526837891L;

			@Override
	    	public Component getListCellRendererComponent(JList list,
	    			Object value, int index, boolean isSelected,
	    			boolean cellHasFocus) {
	    		super.getListCellRendererComponent(list, value, index, isSelected,
	    				cellHasFocus);
	    		if(value instanceof City){
	    			setText(((City)value).getName());
	    		}
	    		return this;
	    	}
	    });
	    
	    JSplitPane panelForTreeAndImage = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(treePlaces), mapPanel);
	    panelForTreeAndImage.setResizeWeight(0.2);
	    mapPanel.setFocusable(true);
	    mapPanel.addMapLocationListener(this);
	    
	    
	    
	    JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(jList), panelForTreeAndImage);
	    pane.setResizeWeight(0.2);
	    content.add(pane, BorderLayout.CENTER);
	    
	    final JLabel label = new JLabel();
	    content.add(label, BorderLayout.SOUTH);

	    JPanel panel = new JPanel(new BorderLayout());
	    final JTextField textField = new JTextField();
	    final JButton button = new JButton();
	    button.setText("Set town");
	    panel.add(textField, BorderLayout.CENTER);
	    panel.add(button, BorderLayout.EAST);
	    
	    content.add(panel, BorderLayout.NORTH);
	    
	    
	    updateListCities(r, textField.getText(), jList);
	    textField.getDocument().addUndoableEditListener(new UndoableEditListener(){
			@Override
			public void undoableEditHappened(UndoableEditEvent e) {
	    		updateListCities(r, textField.getText(), jList);
			}
	    });
	    
	    button.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				selectedCity = (City)jList.getSelectedValue();
			}
	    });

	    jList.addListSelectionListener(new ListSelectionListener(){
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if(jList.getSelectedValue() != null){
					Node node = ((City)jList.getSelectedValue()).getNode();
					String text = "Lat : " + node.getLatitude() + " Lon " + node.getLongitude();
					if(selectedCity != null){
						text += " distance " + NodeUtil.getDistance(selectedCity.getNode(), node);
					}
					label.setText(text);
					mapPanel.setLatLon(node.getLatitude(), node.getLongitude());
				} else {
					String text = selectedCity == null ? "" : selectedCity.getName();
					label.setText(text);
				}
				
			}
	    	
	    });

	    treePlaces.addTreeSelectionListener(new TreeSelectionListener(){
			@Override
			public void valueChanged(TreeSelectionEvent e) {
				if (e.getPath() != null) {
 					if (e.getPath().getLastPathComponent() instanceof DefaultMutableTreeNode) {
						Object o = ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject();

						if (o instanceof City) {
							City c = (City) o;
							mapPanel.setLatLon(c.getNode().getLatitude(), c.getNode().getLongitude());
							mapPanel.requestFocus();
						}

						if (o instanceof Entity) {
							Entity c = (Entity) o;
							if (c instanceof Node) {
								mapPanel.setLatLon(((Node) c).getLatitude(), ((Node) c).getLongitude());
//								mapPanel.requestFocus();
							} else {
								DefaultMutableTreeNode n = (DefaultMutableTreeNode) e.getPath().getPathComponent(
										e.getPath().getPathCount() - 2);
								if (n.getUserObject() instanceof Street) {
									Street str = (Street) n.getUserObject();
									LatLon l = str.getLocationBuilding(c);
									mapPanel.setLatLon(l.getLatitude(), l.getLongitude());
									mapPanel.requestFocus();
								}
							}
						}
					}
				}
				
			}
	    });
	    
	    
	    frame.setSize(1024, 768);
	    frame.setVisible(true);
	}
	
	@Override
	public void locationChanged(final double newLatitude, final double newLongitude){
		Region reg = (Region) amenitiesTree.getUserObject();
		List<Node> closestAmenities = reg.getClosestAmenities(newLatitude, newLongitude);
		Collections.sort(closestAmenities, new Comparator<Node>(){
			@Override
			public int compare(Node o1, Node o2) {
				return Double.compare(NodeUtil.getDistance(o1, newLatitude, newLongitude), 
						NodeUtil.getDistance(o2, newLatitude, newLongitude));
			}
			
		});
		
		Map<String, List<Node>> filter = new TreeMap<String, List<Node>>();
		for(Node n : closestAmenities){
			String type = NodeUtil.getTag(n, "amenity");
			if(!filter.containsKey(type)){
				filter.put(type, new ArrayList<Node>());
			}
			filter.get(type).add(n);
		}
		for(int i=1; i< amenitiesTree.getChildCount(); ){
			if(!filter.containsKey(((DefaultMutableTreeNode)amenitiesTree.getChildAt(i)).getUserObject())){
				amenitiesTree.remove(i);
			} else {
				i++;
			}
		}
		
		((DefaultMutableTreeNode)amenitiesTree.getChildAt(0)).removeAllChildren();
		
		
		for(int i=0; i<15 && i < closestAmenities.size(); i++){
			Node n = closestAmenities.get(i);
			String type = NodeUtil.getTag(n, "amenity");
			String name = NodeUtil.getTag(n, "name");
			int dist = (int) (NodeUtil.getDistance(n, newLatitude, newLongitude));
			String str = type +" "+(name == null ? n.getId() : name) +" [" +dist+" m ]";
			((DefaultMutableTreeNode)amenitiesTree.getChildAt(0)).add(
					new DataExtractionTreeNode(str, n));
		}
		
		for(String s : filter.keySet()){
			DefaultMutableTreeNode p = null;
			for(int i=0; i< amenitiesTree.getChildCount(); i++){
				if(s.equals(((DefaultMutableTreeNode)amenitiesTree.getChildAt(i)).getUserObject())){
					p = ((DefaultMutableTreeNode)amenitiesTree.getChildAt(i));
					break;
				}
			}
			if (p == null) {
				p = new DefaultMutableTreeNode(s);
			}
//			Map<Node, DataExtractionTreeNode> consists = new LinkedHashMap<Node, DataExtractionTreeNode>();
//			for(Node n : filter.get(s)){
//				consists.put(n, null);
//			}
//			for(int i=0; i<p.getChildCount();){
//				Object userObject = ((DefaultMutableTreeNode)p.getChildAt(i)).getUserObject();
//				if(consists.containsKey(userObject)){
//					consists.put((Node) userObject, (DataExtractionTreeNode) p.getChildAt(i));
//					i++;
//				} else {
//					p.remove(i);
//				}
//			}
			
			p.removeAllChildren();
			for(Node n : filter.get(s)){
				String name = NodeUtil.getTag(n, "name");
				int dist = (int) (NodeUtil.getDistance(n, newLatitude, newLongitude));
				String str = (name == null ? n.getId() : name) +" [" +dist+" m ]";
//				if(consists.get(n) != null){
//					consists.get(n).setName(str);
//				} else {
					DataExtractionTreeNode node = new DataExtractionTreeNode(str, n);
					p.add(node);
//				}
			}
			amenitiesTree.add(p);
		}
		treePlaces.updateUI();
	}
	
	public void updateListCities(Region r, String text, JList jList){
		Collection<City> city = r.getSuggestedCities(text, 100);
		City[] names = new City[city.size()];
		int i=0;
		for(City c : city){
			names[i++] = c;
		}
		jList.setListData(names);
	}
	
	
	public static class DataExtractionTreeNode extends DefaultMutableTreeNode {
		private static final long serialVersionUID = 1L;
		private String name;

		public DataExtractionTreeNode(String name, Object userObject){
			super(userObject);
			this.name = name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		@Override
		public String toString() {
			return name;
		}
		
	}
	public static class ExitListener extends WindowAdapter {
		public void windowClosing(WindowEvent event) {
			System.exit(0);
		}
	}

}
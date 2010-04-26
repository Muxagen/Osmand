package com.anvisics.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;

import com.anvisics.NodeUtil;
import com.anvisics.NodeUtil.LatLon;
import com.anvisics.data.City.CityType;

public class Region {
	private Entity entity;
	
	private DataTileManager<Node> amenities = new DataTileManager<Node>();
	
	private Map<CityType, Collection<City>> cities = new HashMap<CityType, Collection<City>>();
	{
		for(CityType type : CityType.values()){
			cities.put(type, new ArrayList<City>());
		}
	}
	
	
	public Region(Entity entity){
		this.entity = entity;
	}
	
	
	public void setEntity(Entity e){
		this.entity = e;
	}
	
	public String getName(){
		return entity == null ? "" : NodeUtil.getTag(entity, "name");
	}
	
	public Collection<City> getCitiesByType(CityType type){
		return cities.get(type);
	}
	
	public Collection<City> getCitiesByName(String name){
		return getCityByName(name, true, Integer.MAX_VALUE);
	}
	
	public Collection<City> getSuggestedCities(String name, int number){
		return getCityByName(name, false, number);
	}
	
	protected Collection<City> getCityByName(String name, boolean exactMatch, int number){
		List<City> l = new ArrayList<City>();
		for(CityType type : CityType.values()){
			for(City c : cities.get(type)){
				if( (exactMatch && c.getName().equalsIgnoreCase(name)) || 
					(!exactMatch && c.getName().toLowerCase().startsWith(name.toLowerCase())
							)){
						l.add(c);
						if(l.size() >= number){
							break;
					}
				}
			}
		}
		return l;
	}
	
	public City getClosestCity(LatLon point){
		City closest = null;
		double relDist = Double.POSITIVE_INFINITY;
		for(CityType t : CityType.values()){
			for(City c : cities.get(t)){
				double rel = NodeUtil.getDistance(c.getNode(), point) / t.getRadius();
				if(rel < 1) {
					return c; // we are in that city
				}
				if(rel < relDist){
					closest = c;
					relDist = rel;
				}
			}
		}
		return closest;
	}
	
	public List<Node> getClosestAmenities(double latitude, double longitude){
		return amenities.getClosestObjects(latitude, longitude, 2);
	}
	
	public void registerAmenity(Node n){
		amenities.registerObject(n.getLatitude(), n.getLongitude(), n);
	}
	
	public City registerCity(Node c){
		City city = new City(c);
		if(city.getType() != null && !NodeUtil.isEmpty(city.getName())){
			cities.get(city.getType()).add(city);
			return city;
		}
		return null;
	}
	
	
	

}
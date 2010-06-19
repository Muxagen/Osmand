package com.osmand.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Xml;
import android.widget.Toast;

import com.osmand.LogUtil;
import com.osmand.OsmandSettings;
import com.osmand.Version;

public class SavingTrackHelper extends SQLiteOpenHelper {
	public final static String TRACKS_PATH = "tracks"; //$NON-NLS-1$
	
	public final static String DATABASE_NAME = "tracks"; //$NON-NLS-1$
	public final static int DATABASE_VERSION = 1;
	
	public final static String TRACK_NAME = "track"; //$NON-NLS-1$
	public final static String TRACK_COL_DATE = "date"; //$NON-NLS-1$
	public final static String TRACK_COL_LAT = "lat"; //$NON-NLS-1$
	public final static String TRACK_COL_LON = "lon"; //$NON-NLS-1$
	public final static String TRACK_COL_ALTITUDE = "altitude"; //$NON-NLS-1$
	public final static String TRACK_COL_SPEED = "speed"; //$NON-NLS-1$
	
	public final static Log log = LogUtil.getLog(SavingTrackHelper.class);
	

	private String updateScript;
	private long lastTimeUpdated = 0;
	private final Context ctx;
	
	public SavingTrackHelper(Context ctx){
		super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		this.ctx = ctx;
		updateScript = "INSERT INTO " + TRACK_NAME + " VALUES (?, ?, ?, ?, ?)"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TRACK_NAME+ " ("+TRACK_COL_LAT +" double, " + TRACK_COL_LON+" double, " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$  
				+ TRACK_COL_ALTITUDE+" double, " + TRACK_COL_SPEED+" double, "  //$NON-NLS-1$ //$NON-NLS-2$
				+ TRACK_COL_DATE +" long )" ); //$NON-NLS-1$
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}
	
	private static class TrkPt {
		public double lat;
		public double lon;
		public double ele;
		public double speed;
		public long time;
	}
	
	
	protected void saveToXMLFiles(File dir, Map<String, List<List<TrkPt>>> data ){
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //$NON-NLS-1$
		try {
			for (String f : data.keySet()) {
				File fout = new File(dir, f + ".gpx"); //$NON-NLS-1$
				int ind = 1;
				while(fout.exists()){
					fout = new File(dir, f + "_"+(++ind)+".gpx"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				FileOutputStream output = new FileOutputStream(fout);
				XmlSerializer serializer = Xml.newSerializer();
				serializer.setOutput(output, "UTF-8"); //$NON-NLS-1$
				serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true); //$NON-NLS-1$
				serializer.startDocument("UTF-8", true); //$NON-NLS-1$
				serializer.startTag(null, "gpx"); //$NON-NLS-1$
				serializer.attribute(null, "version", "1.1"); //$NON-NLS-1$ //$NON-NLS-2$
				serializer.attribute(null, "creator", Version.APP_NAME_VERSION); //$NON-NLS-1$
				serializer.attribute("xmlns", "xsi", "http://www.w3.org/2001/XMLSchema-instance"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				serializer.attribute("xsi", "schemaLocation", "http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				serializer.attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1"); //$NON-NLS-1$ //$NON-NLS-2$
				
				serializer.startTag(null, "trk"); //$NON-NLS-1$
				for(List<TrkPt> l : data.get(f)){
					serializer.startTag(null, "trkseg"); //$NON-NLS-1$
					for(TrkPt p : l){
						serializer.startTag(null, "trkpt"); //$NON-NLS-1$
						serializer.attribute(null, "lat", p.lat+""); //$NON-NLS-1$ //$NON-NLS-2$
						serializer.attribute(null, "lon", p.lon+""); //$NON-NLS-1$ //$NON-NLS-2$
						serializer.startTag(null, "time"); //$NON-NLS-1$
						serializer.text(format.format(new Date(p.time)));
						serializer.endTag(null, "time"); //$NON-NLS-1$
						serializer.startTag(null, "ele"); //$NON-NLS-1$
						serializer.text(p.ele+""); //$NON-NLS-1$
						serializer.endTag(null, "ele"); //$NON-NLS-1$
						if (p.speed > 0) {
							serializer.startTag(null, "speed"); //$NON-NLS-1$
							serializer.text(p.speed + ""); //$NON-NLS-1$
							serializer.endTag(null, "speed"); //$NON-NLS-1$
						}
						
						serializer.endTag(null, "trkpt"); //$NON-NLS-1$
					}
					serializer.endTag(null, "trkseg"); //$NON-NLS-1$
				}
				serializer.endTag(null, "trk"); //$NON-NLS-1$
				
				serializer.endTag(null, "gpx"); //$NON-NLS-1$
				serializer.flush();
				serializer.endDocument();
				
				
			}
		} catch (RuntimeException e) {
			log.error("Error saving gpx", e); //$NON-NLS-1$
			Toast.makeText(ctx, "Exception occurred while saving gpx", Toast.LENGTH_LONG);
		} catch (IOException e) {
			log.error("Error saving gpx", e); //$NON-NLS-1$
			Toast.makeText(ctx, "Exception occurred while saving gpx", Toast.LENGTH_LONG);
		}
	}
	
	public boolean hasDataToSave(){
		SQLiteDatabase db = getReadableDatabase();
		if(db != null){
			Cursor q = db.query(false, TRACK_NAME, new String[0], null, null, null, null, null, null);
			boolean m = q.moveToFirst();
			q.close();
			return m;
		}
		
		return false;
	}
	
	public void saveDataToGpx(){
		SQLiteDatabase db = getReadableDatabase();
		File file = Environment.getExternalStorageDirectory();
		if(db != null && file.canWrite()){
			file = new File(file, "/osmand/"+TRACKS_PATH); //$NON-NLS-1$
			file.mkdirs();
			if (file.exists()) {
				Cursor query = db.rawQuery("SELECT " + TRACK_COL_LAT + "," + TRACK_COL_LON + "," + TRACK_COL_ALTITUDE + "," //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						+ TRACK_COL_SPEED + "," + TRACK_COL_DATE + " FROM " + TRACK_NAME, null); //$NON-NLS-1$ //$NON-NLS-2$
				long previousTime = 0;
				Map<String, List<List<TrkPt>>> data = new LinkedHashMap<String, List<List<TrkPt>>>();
				List<TrkPt> segment = new ArrayList<TrkPt>();
				List<List<TrkPt>> track = new ArrayList<List<TrkPt>>();
				track.add(segment);
				if (query.moveToFirst()) {
					do {
						TrkPt pt = new TrkPt();
						pt.lat = query.getDouble(0);
						pt.lon = query.getDouble(1);
						pt.ele = query.getDouble(2);
						pt.speed = query.getDouble(3);
						long time = query.getLong(4);
						pt.time = time;
						
						if (previousTime == 0) {
							data.put(DateFormat.format("yyyy-MM-dd", time).toString(), track); //$NON-NLS-1$
							segment.add(pt);
						} else if (Math.abs(time - previousTime) < 60000) {
							// 1 hour - same segment
							segment.add(pt);
						} else if (Math.abs(time - previousTime) < 3600000) {
							// 1 hour - same track
							segment = new ArrayList<TrkPt>();
							segment.add(pt);
							track.add(segment);
						} else {
							// check day (possibly better create new track (not new segment)
							String date = DateFormat.format("yyyy-MM-dd", time).toString(); //$NON-NLS-1$
							if (data.containsKey(date)) {
								track = data.get(date);
							} else {
								track = new ArrayList<List<TrkPt>>();
								data.put(date, track);
							}
							segment = new ArrayList<TrkPt>();
							segment.add(pt);
							track.add(segment);
						}

						previousTime = time;
					} while (query.moveToNext());
				}
				query.close();
				saveToXMLFiles(file, data);
			}
		}
		
		db = getWritableDatabase();
		if(db != null){
//			Calendar cal = Calendar.getInstance();
//			cal.setTime(new java.util.Date());
//			cal.set(Calendar.HOUR_OF_DAY, 0);
//			cal.set(Calendar.MINUTE, 0);
//			cal.set(Calendar.SECOND, 0);
//			cal.set(Calendar.MILLISECOND, 0);
			// remove all from db
			db.execSQL("DELETE FROM " + TRACK_NAME+ " WHERE " + TRACK_COL_DATE + " <= ?", new Object[]{System.currentTimeMillis()}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}
	
	public void insertData(double lat, double lon, double alt, double speed, long time){
		if (time - lastTimeUpdated > OsmandSettings.getSavingTrackInterval(ctx)) {
			SQLiteDatabase db = getWritableDatabase();
			if (db != null) {
				db.execSQL(updateScript, new Object[] { lat, lon, alt, speed, time });
			}
			lastTimeUpdated = time;
		}
	}
	

}
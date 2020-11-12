package uk.ac.ed.inf.drone;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Feature;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Hello world!
 *
 */

public class AirQualityDrone
{
    public static void main( String[] args ) throws IOException
    {
    	//System.out.print((Math.toDegrees(Math.atan2(-1, -1))+360) % 360);
        Drone drone = new Drone();
        FeatureCollection nfz = noFlyZones(args);
        ArrayList<SensorsList> sensors = dailySensors(args);
        ArrayList<Point> coordinates = what3words(args, sensors);
        ArrayList<Integer> dronePath = greedyPath(drone, coordinates, args);
        flightPath(drone, sensors, dronePath, coordinates);
    }
    
    public static class Drone 
    {
    	int moves; // Max 150 moves
    	Point position; // Can only move 0.0003 degrees at a time
    	int angle; //0 = E, 90 = N, 180 = W, 270 = S
    }
    
    public class SensorsList
    {
    	String location;
    	double battery;
    	String reading;
    }
    
    public class SensorDetails
    {
    	String country;
    	Square square;
    	public class Square
    	{
    		Southwest southwest;
    		public class Southwest
    		{
    			double lng;
    			double lat;
    		}
    		Northeast northeast;
    		public class Northeast
    		{
    			double lng;
    			double lat;
    		}
    	}
    	String nearestPlace;
    	Coordinates coordinates;
    	public class Coordinates
    	{
    		double lng;
    		double lat;
    	}
    	String words;
    	String language;
    	String map;
    }
    
    public static FeatureCollection noFlyZones(String[] args) throws IOException
    // This method will load in the "No Fly Zones" from the drone and return them as a Feature Collection
    {
    	// Load in the "No Fly Zones" for the drone
    	String no_fly_zone = "D:\\User Folders\\Ben\\Desktop\\Coding\\Year 3\\Informatics Large Practical\\Coursework 2\\drone\\WebServer\\buildings\\no-fly-zones.geojson";
    	var fr = new FileReader(no_fly_zone);
    	int reader1;
    	String nfz = "";
    	while ((reader1=fr.read()) != -1)
        {
        	nfz = nfz + (char) reader1;
        }
    	fr.close();
    	return FeatureCollection.fromJson(nfz);
    }
    
    public static ArrayList<SensorsList> dailySensors(String[] args) throws IOException
    // This method will load the list of sensors to be visited by the drone and return
    // them as an Array List of type SensorsList (as parsed by the Gson().fromJson function)
    {	
    	// Load in the list of sensors for that day
    	String air_quality_data = "D:\\User Folders\\Ben\\Desktop\\Coding\\Year 3\\Informatics Large Practical\\Coursework 2\\drone\\WebServer\\maps\\" + args[2] + "\\"
    							  + args[1] + "\\" + args[0] + "\\air-quality-data.json";
    	var fr2 = new FileReader(air_quality_data);
    	int reader2;
    	String aqd = "";
    	while ((reader2=fr2.read()) != -1)
        {
        	aqd = aqd + (char) reader2;
        }
    	fr2.close();
    	
    	Type listType = new TypeToken<ArrayList<SensorsList>>() {}.getType();
    	ArrayList<SensorsList> sensors = new Gson().fromJson(aqd, listType);
    	
    	return sensors;
    }
    
    public static ArrayList<Point> what3words(String[] args, ArrayList<SensorsList> sensors) throws IOException
    {
    	// Load in all the sensor details from What3Words address
    	var coordinates = new ArrayList<Point>();
    	for(int i = 0; i < 33; i++) {
    		String location = sensors.get(i).location;
    		String[] what3words = location.split("\\.");

    		String sensor_details = "D:\\User Folders\\Ben\\Desktop\\Coding\\Year 3\\Informatics Large Practical\\Coursework 2\\drone\\WebServer\\words\\" + what3words[0] + "\\"
				  + what3words[1] + "\\" + what3words[2] + "\\details.json";
    		
    		var fr3 = new FileReader(sensor_details);
			String sd = "";
			int reader3;
			while ((reader3=fr3.read()) != -1)
			{
			sd = sd + (char) reader3;
			}
			fr3.close();
    		
    		var sensorDetails = new Gson().fromJson(sd, SensorDetails.class);   
    		coordinates.add(Point.fromLngLat(sensorDetails.coordinates.lng, sensorDetails.coordinates.lat));
    	}
    	return coordinates;
    }
    	    	
    public static ArrayList<Integer> greedyPath(Drone drone, ArrayList<Point> coordinates, String[] args)
    // This method will set up a greedy pathfinding algorithm and return a list of integers
    // describing the permutation in which the drone will visit the sensors
    {	
    	double droneStartLng = Double.parseDouble(args[4]);
    	double droneStartLat = Double.parseDouble(args[3]);
    	
    	drone.position = Point.fromLngLat(droneStartLng, droneStartLat);
    	coordinates.add(0, drone.position);
    	
    	var dronePath = new ArrayList<Integer>();
    		for(int i = 0; i < coordinates.size(); i++)
    		{
    			dronePath.add(i);
    		}
    	var visited = new ArrayList<Integer>();
    	int closestSensor;
    	boolean closerFound;
    	double closestDist;
    	double dist;
    	int closestSensorNumber = 0;
    	
    	for(int i = 0; i < coordinates.size()-2; i++)
    	{
    		visited.add(dronePath.get(i));
    		closestSensor = dronePath.get(i+1);
    		closerFound = false;
    		for(int j = i+1; j < coordinates.size()-1; j++)
    		{
    			closestDist = euclidDist(coordinates.get(dronePath.get(i)), coordinates.get(closestSensor));
    			dist = euclidDist(coordinates.get(dronePath.get(i)), coordinates.get(dronePath.get(j)));
    			if(0 < dist & dist < closestDist & !(visited.contains(dronePath.get(j))))
    			{
    				closestSensor = dronePath.get(j);
    				closestSensorNumber = j;
    				closerFound = true;
    			}			
    		}
    		if(closerFound)
    		{
    			dronePath.set(closestSensorNumber, dronePath.get(i+1));
    			dronePath.set(i+1, closestSensor);
    		}
    	}
    	
    	// Find out which sensor the drone is closest to
    	//int droneClosest = 0;
    	//for(int i = 0; i < 33; i++)
    	//{
    	//	if (euclidDist(drone.position, coordinates.get(i)) < euclidDist(drone.position, coordinates.get(droneClosest)))
    	//	{
    	//		droneClosest = i;
    	//	}
    	//}
    	
    	//for(int i = 0; i < sensorList.size(); i++)
    	//{
    	//	System.out.print(sensorList.get(i) + " ");
    	//}
    	// Shift the sensor list permutation so that the drone's closest sensor is the first element
    	//var dronePath = new ArrayList<Integer>();
    	//for(int i = 0; i < 33; i++)
    	//{
    	//	dronePath.add((sensorList.get((sensorList.indexOf(droneClosest)+i) % sensorList.size())));
    	//	//System.out.print(dronePath.get(i) + " ");
    	//}
    	// Append the drone's starting position to the end
    	dronePath.add(0);
    	
    	return dronePath;
    }
    
    public static double euclidDist(Point dronePos, Point sensorPos)
    // This method will return the euclidean distance between two points
    {
    	double x1minusx2 = dronePos.longitude()-sensorPos.longitude();
    	double y1minusy2 = dronePos.latitude()-sensorPos.latitude();
    	double dist = Math.sqrt(Math.pow(x1minusx2, 2) + Math.pow(y1minusy2, 2) ); 
    	return dist;
    }
    
    public static void flightPath(Drone drone, ArrayList<SensorsList> sensors, ArrayList<Integer> dronePath, ArrayList<Point> coordinates)
    // This method will control the drone's flight, taking it to the different sensors
    // and making sure it does not go into any restricted areas
    {
    	var readings = new ArrayList<String>();
    	double hypotenuse = 0;
    	double height = 0;
    	double width = 0;
    	boolean sensProx = false;
    	for(int i = 1; i < dronePath.size(); i++)
    	{
    		sensProx = false;
    		while (!sensProx & drone.moves < 150)
	    	{
	    		// Determine angle of travel
	    		hypotenuse = euclidDist(coordinates.get(dronePath.get(i)), drone.position);
	    		height = coordinates.get(dronePath.get(i)).latitude() - drone.position.latitude();
	    		width = coordinates.get(dronePath.get(i)).longitude() - drone.position.longitude();
	    		drone.angle = (int) Math.round((Math.toDegrees(Math.atan2(height, width)))/10)*10;
	    		
	    		// Convert negative angles into their positive counterparts
	    		drone.angle = ((drone.angle + 360) % 360);	    		
	    		//System.out.print("\nClosest sensor = " + "[" + dronePath.get(i) + "] " + coordinates.get(dronePath.get(i))
	    		//				 + "\nDistance from sensor = " + hypotenuse
	    		//				 + "\nDrone angle = " + drone.angle);
	    		
	    		// Update the drone's position
	    		drone.position = Point.fromLngLat(drone.position.longitude() + (Math.cos(Math.toRadians(drone.angle)) * 0.0003), drone.position.latitude() + (Math.sin(Math.toRadians(drone.angle)) * 0.0003));
	    		//System.out.println("\nNew distance from sensor = " + euclidDist(coordinates.get(dronePath.get(i)), drone.position));
	    		//System.out.println("\nCHECKING 0.0003 = " + Math.sqrt(Math.pow((Math.sin(drone.angle)*0.0003), 2) + Math.pow((Math.cos(drone.angle)*0.0003), 2)));
	    		
	    		// Increment the drone's total moves
	    		drone.moves ++;
	    		//System.out.println("\nTotal moves = " + drone.moves);
	    		
	    		// Check whether the drone is close enough to a sensor to read it, and if so, take a reading
	    		// System.out.println("\n" + euclidDist(drone.position, coordinates.get(dronePath.get(i))));
	    		if(euclidDist(drone.position, coordinates.get(dronePath.get(i))) <= 0.0002)
	    		{
	    			//System.out.println("\nThe drone is close enough for a reading at sensor " + dronePath.get(i));
	    			readings.add(sensors.get(dronePath.get(i)).reading);
	    			sensProx = true;
	    			//System.out.println("\nReading taken as " + sensors.get(i).reading);
	    		}
	    	}
    	}
    	System.out.println(drone.moves);
    }
    
    public static ArrayList<String> hexCodeConversion(String[] predictions)
    // This function will convert the predictions into the appropriate hex-code colour
    {
    	var colourMap = new ArrayList<String>();
    	var symbolMap = new ArrayList<String>();
    	
    	for(int i = 0; i < 100; i++)
    	{
    		// Green, lighthouse
    		if (Integer.parseInt(predictions[i]) >= 0 && Integer.parseInt(predictions[i]) < 32)
    		{
    			colourMap.add(i, "#00ff00");
    			symbolMap.add(i, "lighthouse-15.svg");
    		}
    		// Medium green, lighthouse
    		else if (Integer.parseInt(predictions[i]) >= 32 && Integer.parseInt(predictions[i]) < 64)
    		{
    			colourMap.add(i, "#40ff00");
    		}
    		// Light green, lighthouse
    		else if (Integer.parseInt(predictions[i]) >= 64 && Integer.parseInt(predictions[i]) < 96)
    		{
    			colourMap.add(i, "#80ff00");
    			symbolMap.add(i, "lighthouse-15.svg");
    		}
    		// Lime green, lighthouse
    		else if (Integer.parseInt(predictions[i]) >= 96 && Integer.parseInt(predictions[i]) < 128)
    		{
    			colourMap.add(i, "#c0ff00");
    			symbolMap.add(i, "lighthouse-15.svg");
    		}
    		// Gold, danger
    		else if (Integer.parseInt(predictions[i]) >= 128 && Integer.parseInt(predictions[i]) < 160)
    		{
    			colourMap.add(i, "#ffc000");
    			symbolMap.add(i, "danger-15.svg");
    		}
    		// Orange, danger
    		else if (Integer.parseInt(predictions[i]) >= 160 && Integer.parseInt(predictions[i]) < 192)
    		{
    			colourMap.add(i, "#ff8000");
    			symbolMap.add(i, "danger-15.svg");
    		}
    		// Red/Orange, danger
    		else if (Integer.parseInt(predictions[i]) >= 192 && Integer.parseInt(predictions[i]) < 224)
    		{
    			colourMap.add(i, "#ff4000");
    			symbolMap.add(i, "danger-15.svg");
    		}
    		// Red, danger
    		else if (Integer.parseInt(predictions[i]) >= 224 && Integer.parseInt(predictions[i]) < 256)
    		{
    			colourMap.add(i, "#ff0000");
    			symbolMap.add(i, "danger-15.svg");
    		}
    		//else if (sensor.battery < 10)
    		//{
    		//	colourMap.add(i, "#000000");
    		// symbolMap.add(i, "cross-15.svg");
    		//}
    		//else if (notVisited)
    		//{
    		//	colourMap.add(i, "#aaaaaa");
    		// symbolMap.add(i, "");
    		//}
    	}
    	return colourMap;
    }
    public static void geojsonConvert(List<Polygon> polygons, ArrayList<String> colourMap) throws IOException
    // This method will create a list of features from the polygons and their associated colour values and add
    // them to a feature collection.  This feature collection will then be converted to GeoJSON format and  
    // written to a file called 'heatmap.geojson'
    {
    	// Creating a list to store the features and a File Writer to write the geojson file
    	var featureList = new ArrayList<Feature>();
    	var jsonFile = new FileWriter("heatmap.geojson");
    	
    	// Loop through the feature list, adding the polygons and their colour values
    	for(int i = 0; i < 100; i++)
    	{
    		featureList.add(Feature.fromGeometry((Geometry)polygons.get(i)));
    		featureList.get(i).addStringProperty("rgb-string", colourMap.get(i));
    		featureList.get(i).addStringProperty("fill", colourMap.get(i));
    		featureList.get(i).addNumberProperty("fill-opacity", 0.75);
    	}
    	// Create a feature collection from the list of features described above
    	var featureCol = FeatureCollection.fromFeatures(featureList);
    	
    	//Convert the feature collection to JSON format, write it to the file and then close the file 
    	jsonFile.write(featureCol.toJson());
    	jsonFile.close();
    }
}

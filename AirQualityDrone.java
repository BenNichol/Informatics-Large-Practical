package uk.ac.ed.inf.aqmaps;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Feature;
import com.mapbox.turf.TurfJoins;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.awt.geom.Line2D;


//import java.awt.Polygon;

/**Title: Air Quality Drone
 * Author: Ben Nichol 14/11/20
 *
 * Description: This program will create a virtual drone that, given an input of sensors, with 
 * 				their locations given as what3words addresses, will find an efficient path through
 * 				these sensors and report back the air quality readings and export them as a geojson
 * 				file. 
 */

public class AirQualityDrone
{
    public static void main( String[] args ) throws IOException, InterruptedException
    {
    	
        Drone drone = new Drone();
        var allFeatures = new ArrayList<Feature>();
        FeatureCollection nfz = noFlyZones(args);     
        ArrayList<SensorsList> sensors = dailySensors(args);
        ArrayList<Point> coordinates = what3words(args, sensors);
        ArrayList<Integer> dronePath = greedyPath(drone, coordinates, args);
        var sensorOutput = flightPath(drone, sensors, dronePath, coordinates, nfz, allFeatures);
        var sensorMap = hexCodeConversion(sensorOutput);
        geojsonConvert(sensorMap, sensors, dronePath, coordinates, allFeatures, nfz);
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
   
    // Just have one HttpClient, shared between all HttpRequests
    private static final HttpClient client = HttpClient.newHttpClient();
    
    
    public static FeatureCollection noFlyZones(String[] args) throws IOException, InterruptedException
    // This method will load in the "No Fly Zones" from the drone and return them as a Feature Collection
    {
    	// Load in the "No Fly Zones" for the drone
    	// HttpClient assumes that it is a GET request by default.
    	String urlString = "http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson";
    	var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
    	// The response object is of class HttpResponse<String>
    	var response = client.send(request, BodyHandlers.ofString());
    	var nfz = FeatureCollection.fromJson(response.body());
    	return nfz;
    }
    
    public static ArrayList<SensorsList> dailySensors(String[] args) throws IOException, InterruptedException
    // This method will load the list of sensors to be visited by the drone and return
    // them as an Array List of type SensorsList (as parsed by the Gson().fromJson function)
    {	
    	// Load in the list of sensors for that day
    	String urlString = "http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json";
    	var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
    	// The response object is of class HttpResponse<String>
    	var response = client.send(request, BodyHandlers.ofString());
    	
    	Type listType = new TypeToken<ArrayList<SensorsList>>() {}.getType();
    	ArrayList<SensorsList> sensors = new Gson().fromJson(response.body(), listType);
    	
    	return sensors;
    }
    
    public static ArrayList<Point> what3words(String[] args, ArrayList<SensorsList> sensors) throws InterruptedException, IOException
    {
    	// Load in all the sensor details from What3Words address
    	
    	var coordinates = new ArrayList<Point>();
    	for(int i = 0; i < 33; i++) {
    		String location = sensors.get(i).location;
    		String[] what3words = location.split("\\.");
    		String urlString = "http://localhost:" + args[6] + "/words/" + what3words[0] + "/" + what3words[1] + "/" + what3words[2] + "/details.json";
    		
    		var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
    		// The response object is of class HttpResponse<String>
    		var response = client.send(request, BodyHandlers.ofString());
    		
    		var sensorDetails = new Gson().fromJson(response.body(), SensorDetails.class);   
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
    	
    	for(int i = 0; i < coordinates.size()-1; i++)
    	{
    		visited.add(dronePath.get(i));
    		closestSensor = dronePath.get(i+1);
    		closerFound = false;
    		for(int j = i+1; j < coordinates.size(); j++)
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
    	
    	// Append the drone's starting position to the end
    	dronePath.add(0);
    	for(int i = 0; i<dronePath.size(); i++)
    	{
    		System.out.print(dronePath.get(i) + " ");
    	}
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
    
    public static boolean insidePolygon(Point dronePos, FeatureCollection nfz)
    // This method will determine whether the point is inside the polygon or not
    {
    	
    	for(Feature f : nfz.features())
    	{
    		Polygon poly = (Polygon)f.geometry();
    		if(TurfJoins.inside(dronePos, poly))
    		{
    			return true;
    		}
    	}
    	
    	return false;
    }
    
    public static boolean lineIntersectPolygon(Point startPos, Point endPos, FeatureCollection nfz)
    {	
    	for(Feature f : nfz.features())    		
    	{
    		Polygon poly = (Polygon)f.geometry();
    		var points = poly.coordinates().get(0);
    		for(int i = 0; i < points.size()-1; i++)
    		{
    			double startLng = points.get(i).longitude();
    			double startLat = points.get(i).latitude();
    			double endLng = points.get(i+1).longitude();
    			double endLat = points.get(i+1).latitude();
    			
    			Line2D.Double polyLine = new Line2D.Double(startLng, startLat, endLng, endLat);
    			Line2D.Double droneLine = new Line2D.Double(startPos.longitude(), startPos.latitude(), endPos.longitude(), endPos.latitude());
    			
    			if (polyLine.intersectsLine(droneLine)) return true;
    		}
    	}
    	return false;
    }
    
    public static boolean insideBoundary(Point pos)
    {
    	double maxLatBoundary = 55.946233;
    	double minLatBoundary = 55.942617;
    	double maxLngBoundary = -3.184319;
    	double minLngBoundary = -3.192473;
    	
    	return pos.latitude() < maxLatBoundary & pos.latitude() > minLatBoundary & pos.longitude() < maxLngBoundary & pos.longitude() > minLngBoundary;
    }
    
    public static ArrayList<List<String>> flightPath(Drone drone, ArrayList<SensorsList> sensors, ArrayList<Integer> dronePath, ArrayList<Point> coordinates, FeatureCollection nfz, ArrayList<Feature> allFeatures)
    // This method will control the drone's flight, taking it to the different sensors
    // and making sure it does not go into any restricted areas
    {
    	// No fly zones
    	
    	var movementHistory = new ArrayList<Point>();
    	var readings = new ArrayList<String>();
    	var batteries = new ArrayList<String>();
    	double deltaLng = 0;
    	double deltaLat = 0;
    	boolean sensProx = false;
    	Point newPosition;
    	for(int i = 1; i < dronePath.size(); i++)
    	{
    		sensProx = false;
    		while (!sensProx & drone.moves < 150)
	    	{
    			// Record all the positions of the drone
    			movementHistory.add(drone.position);
    			
	    		// Determine angle of travel
	    		deltaLat = coordinates.get(dronePath.get(i)).latitude() - drone.position.latitude();
	    		deltaLng = coordinates.get(dronePath.get(i)).longitude() - drone.position.longitude();
	    		drone.angle = (int) Math.round((Math.toDegrees(Math.atan2(deltaLat, deltaLng)))/10)*10;
	    		
	    		// Convert negative angles into their positive counterparts
	    		drone.angle = ((drone.angle + 360) % 360);	    		
	    		
	    		// Update the drone's position
	    		newPosition = Point.fromLngLat(drone.position.longitude() + (Math.cos(Math.toRadians(drone.angle)) * 0.0003), drone.position.latitude() + (Math.sin(Math.toRadians(drone.angle)) * 0.0003));
	    		
	    		// Check to see whether the new position is within a no fly zone, and if so, update the angle of trajectory
	    		while(lineIntersectPolygon(drone.position, newPosition, nfz) || !insideBoundary(newPosition))
	    		{
	    			drone.angle = (drone.angle + 10) % 360;
	    			newPosition = Point.fromLngLat(drone.position.longitude() + (Math.cos(Math.toRadians(drone.angle)) * 0.0003), drone.position.latitude() + (Math.sin(Math.toRadians(drone.angle)) * 0.0003));
	    		}
	    		drone.position = newPosition;
	    		
	    		// Increment the drone's total moves
	    		drone.moves ++;
	    		//System.out.println("\nTotal moves = " + drone.moves);
	    		
	    		// Check whether the drone is close enough to a sensor to read it, and if so, take a reading
	    		if(euclidDist(drone.position, coordinates.get(dronePath.get(i))) <= 0.0002)
	    		{
	    			if(i < dronePath.size()-1)
					{
						readings.add(sensors.get(dronePath.get(i)-1).reading);
						batteries.add(String.valueOf(sensors.get(dronePath.get(i)-1).battery));
					}
	    			
	    			sensProx = true;
	    		}
	    	}
    	}
    	System.out.println("\nTotal number of moves = " + drone.moves);
    	
    	Feature dronePositions;
    	var sensorOutput = new ArrayList<List<String>>();
    	sensorOutput.add(readings);
    	sensorOutput.add(batteries);
    	
    	// Add the drone positions as a line string
    	dronePositions = Feature.fromGeometry(LineString.fromLngLats(movementHistory));
    	
    	// Give the lines a dark grey colour

    	dronePositions.addStringProperty("rgb-string", "#404040");
    	allFeatures.add(dronePositions);
    	
    	return sensorOutput;
    }
    
    public static ArrayList<List<String>> hexCodeConversion(ArrayList<List<String>> sensorOutput)
    // This function will convert the predictions into the appropriate hex-code colour
    {
    	var colourMap = new ArrayList<String>();
    	var symbolMap = new ArrayList<String>();
    	var sensorMap = new ArrayList<List<String>>();
    	for(int i = 0; i < sensorOutput.get(0).size(); i++)
    	{
    		// Black, cross
    		if (Double.parseDouble(sensorOutput.get(1).get(i)) < 10)
    		{
    			colourMap.add(i, "#000000");
    			symbolMap.add(i, "cross");
    		}
    		// Green, lighthouse
    		else if (Double.parseDouble(sensorOutput.get(0).get(i)) >= 0 && Double.parseDouble(sensorOutput.get(0).get(i)) < 32)
    		{
    			colourMap.add(i, "#00ff00");
    			symbolMap.add(i, "lighthouse");
    		}
    		// Medium green, lighthouse
    		else if (Double.parseDouble(sensorOutput.get(0).get(i)) >= 32 && Double.parseDouble(sensorOutput.get(0).get(i)) < 64)
    		{
    			colourMap.add(i, "#40ff00");
    			symbolMap.add(i, "lighthouse");
    		}
    		// Light green, lighthouse
    		else if (Double.parseDouble(sensorOutput.get(0).get(i)) >= 64 && Double.parseDouble(sensorOutput.get(0).get(i)) < 96)
    		{
    			colourMap.add(i, "#80ff00");
    			symbolMap.add(i, "lighthouse");
    		}
    		// Lime green, lighthouse
    		else if (Double.parseDouble(sensorOutput.get(0).get(i)) >= 96 && Double.parseDouble(sensorOutput.get(0).get(i)) < 128)
    		{
    			colourMap.add(i, "#c0ff00");
    			symbolMap.add(i, "lighthouse");
    		}
    		// Gold, danger
    		else if (Double.parseDouble(sensorOutput.get(0).get(i)) >= 128 && Double.parseDouble(sensorOutput.get(0).get(i)) < 160)
    		{
    			colourMap.add(i, "#ffc000");
    			symbolMap.add(i, "danger");
    		}
    		// Orange, danger
    		else if (Double.parseDouble(sensorOutput.get(0).get(i)) >= 160 && Double.parseDouble(sensorOutput.get(0).get(i)) < 192)
    		{
    			colourMap.add(i, "#ff8000");
    			symbolMap.add(i, "danger");
    		}
    		// Red/Orange, danger
    		else if (Double.parseDouble(sensorOutput.get(0).get(i)) >= 192 && Double.parseDouble(sensorOutput.get(0).get(i)) < 224)
    		{
    			colourMap.add(i, "#ff4000");
    			symbolMap.add(i, "danger");
    		}
    		// Red, danger
    		else if (Double.parseDouble(sensorOutput.get(0).get(i)) >= 224 && Double.parseDouble(sensorOutput.get(0).get(i)) < 256)
    		{
    			colourMap.add(i, "#ff0000");
    			symbolMap.add(i, "danger");
    		}
    		// Gray, no symbol
    		else 
         	{
        		colourMap.add(i, "#aaaaaa");
        		symbolMap.add(i, "");
        	}
    	}
    	sensorMap.add(colourMap);
    	sensorMap.add(symbolMap);
    	return sensorMap;
    }
    public static void geojsonConvert(ArrayList<List<String>> sensorMap, ArrayList<SensorsList> sensors, ArrayList<Integer> dronePath, ArrayList<Point> coordinates, ArrayList<Feature> allFeatures, FeatureCollection nfz) throws IOException
    // This method will create a list of features from the sensor points and their associated values and add
    // them to a feature collection, along with the drone's movements.  This feature collection will then be converted to GeoJSON format and  
    // written to a file called 'aqmaps.geojson'
    {
    	// Creating a list to store the features and a File Writer to write the geojson file
    	var featureList = new ArrayList<Feature>();
    	var buildingsList = new ArrayList<Feature>();
    	var jsonFile = new FileWriter("aqmaps.geojson");
    	
    	// Normalise the dronePath and coordinates array, removing prepended & appended drone starting positions, and reducing the values by 1 to
    	// be in line with the sensor indexing
    	coordinates.remove(0);
    	dronePath.remove(0);
    	dronePath.remove(dronePath.size()-1);
    	for(int i = 0; i < dronePath.size(); i++)
    	{
    		dronePath.set(i, dronePath.get(i)-1);
    	}
    	
    	// Loop through the feature list, adding the points and their associated attributes
    	for(int i = 0; i < dronePath.size(); i++)
    	{
    		featureList.add(Feature.fromGeometry(coordinates.get(dronePath.get(i))));
    		featureList.get(i).addStringProperty("marker-size", "medium");
    		featureList.get(i).addStringProperty("location", sensors.get(dronePath.get(i)).location);
    		featureList.get(i).addStringProperty("rgb-string", sensorMap.get(0).get(i));
    		featureList.get(i).addStringProperty("marker-color", sensorMap.get(0).get(i));
    		featureList.get(i).addStringProperty("marker-symbol", sensorMap.get(1).get(i));
    	}
    	for(int i = 0; i < nfz.features().size(); i++)
    	{
    		buildingsList.add(nfz.features().get(i));
    		buildingsList.get(i).addStringProperty("rgb-string", "#ff0000");
    		buildingsList.get(i).addStringProperty("fill", "#ff0000");
    		buildingsList.get(i).addNumberProperty("fill-opacity", 0.75);
    	}
    	// Create a feature collection from the list of features described above
    	// Add the drone's movements to the feature list
    	for(int i = 0; i < allFeatures.size(); i++)
    	{
    		featureList.add(allFeatures.get(i));
    	}
    	
    	// Add the no fly zones to the feature list
    	for(int i = 0; i < buildingsList.size(); i++)
    	{
    		featureList.add(buildingsList.get(i));
    	}
    	
    	// Add all of the features from the feature list into a feature collection
    	var featureCol = FeatureCollection.fromFeatures(featureList);
    	
    	//Convert the feature collection to JSON format, write it to the file and then close the file 
    	jsonFile.write(featureCol.toJson());
    	jsonFile.close();
    }
}

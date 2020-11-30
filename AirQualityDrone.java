package uk.ac.ed.inf.aqmaps;

import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Feature;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.awt.geom.Line2D;


/**Title: AirQualityDrone
 * Author: Ben Nichol 14/11/20
 *
 * Description: This program will create a virtual drone that, given an input of sensors, with 
 * 				their locations given as what3words addresses, will find an efficient path through
 * 				these sensors and report back their associated air quality readings, export them as a geojson
 * 				file, and produce a flightpath.txt file presenting the movements of the drone through that path. 
 */

public class AirQualityDrone
{
    public static void main( String[] args ) throws IOException, InterruptedException
    {
    	// Create an instance of type Random, taking in the 'seed' from the arguments list
    	Random rand = new Random(Long.parseLong(args[5]));
    	
    	// Create an instance of the Drone class, this drone will be manipulated throughout the execution
    	// of the program
        Drone drone = new Drone();
        
        var flightPathFeatures = new ArrayList<Feature>();
        FeatureCollection nfz = noFlyZones(args);     
        ArrayList<SensorsList> sensors = dailySensors(args);
        ArrayList<Point> coordinates = what3words(args, sensors);
        ArrayList<Integer> dronePath = greedyPath(drone, coordinates, args);
        var sensorOutput = flightPath(rand, drone, sensors, dronePath, coordinates, nfz, flightPathFeatures, args);
        var sensorMap = hexCodeConversion(sensorOutput);
        geojsonConvert(sensorMap, sensors, dronePath, coordinates, flightPathFeatures, nfz, args);
    }
    
    public static class Drone 
    /**
     * This class is used to define the fields used by the drone
     */
    {
    	int moves;
    	Point position;
    	int angle;
    }
    
    public class SensorsList
    /**
     * This class is used to define the fields used by the sensors
     */
    {
    	String location;
    	double battery;
    	String reading;
    }
    
    public class SensorDetails
    /**
     * This class is used to allow the inference of types during
     * JSON parsing. Defining the class as such allows for ease of
     * accessing the information given by the Web Server via the 
     * HTTP Client.
     */
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
    /**
     * This method will load in the 'No Fly Zones' from the web server and parse them as a JSON formatted
     * file.
     * The file's will then be translated to a GeoJSON Feature Collection and returned, for use elsewhere.
     */
    {
    	// Load in the "No Fly Zones" for the drone
    	// HttpClient assumes that it is a GET request by default.
    	String urlString = "http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson";
    	var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
    	
    	// The response object is of class HttpResponse<String>
    	var response = client.send(request, BodyHandlers.ofString());
    	
    	// Create a variable of type Feature Collection to store the data
    	var nfz = FeatureCollection.fromJson(response.body());
    	
    	return nfz;
    }
    
    public static ArrayList<SensorsList> dailySensors(String[] args) throws IOException, InterruptedException
    /**
     * This method will load the list of sensors for the specific date (given with args[])
     * from the web server and parse them as a JSON formatted file. It will then
     * return them as an Array List of type SensorsList.
     */
    {	
    	// Load in the list of sensors for that day
    	String urlString = "http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json";
    	var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
    	
    	// The response object is of class HttpResponse<String>
    	var response = client.send(request, BodyHandlers.ofString());
    	
    	// Get the types from the SensorsList class
    	Type listType = new TypeToken<ArrayList<SensorsList>>() {}.getType();
    	
    	// Create a variable of type ArrayList<SensorsList>
    	ArrayList<SensorsList> sensors = new Gson().fromJson(response.body(), listType);
    	
    	return sensors;
    }
    
    public static ArrayList<Point> what3words(String[] args, ArrayList<SensorsList> sensors) throws InterruptedException, IOException
    /**
     * This method will determine the coordinates of each sensor using their associated 'What3Words' address.
     * The file returned from the web server will then have its contents attributed to certain types, based
     * on the SensorDetails class. The longitude and latitude coordinates for each sensor will then be
     * extracted and stored in an Array List of type Point and returned.  
     */
    {
    	// Create a variable to store the coordinates and loop through all of the sensors, obtaining their coordinates
    	var coordinates = new ArrayList<Point>();
    	for(int i = 0; i < 33; i++) {
    		String location = sensors.get(i).location;
    		String[] what3words = location.split("\\.");
    		String urlString = "http://localhost:" + args[6] + "/words/" + what3words[0] + "/" + what3words[1] + "/" + what3words[2] + "/details.json";
    		
    		// Load in all the sensor details from What3Words address
    		var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
    		// The response object is of class HttpResponse<String>
    		var response = client.send(request, BodyHandlers.ofString());
    		
    		// Create a variable to store the response from the webServer and then add the appropriate
    		// information to the coordinates Array List
    		var sensorDetails = new Gson().fromJson(response.body(), SensorDetails.class);   
    		coordinates.add(Point.fromLngLat(sensorDetails.coordinates.lng, sensorDetails.coordinates.lat));
    		
    	}
    	return coordinates;
    }
    	    	
    public static ArrayList<Integer> greedyPath(Drone drone, ArrayList<Point> coordinates, String[] args)
    /**
     * This method will set up a 'Greedy' pathfinding algorithm that will, given the coordinates of each
     * sensor and the starting position of the drone, return a list of integers describing the 
     * permutation in which the drone will visit the sensors (including returning to the start point).
     */
    {	
    	// Get the drone's starting point from args[]
    	double droneStartLng = Double.parseDouble(args[4]);
    	double droneStartLat = Double.parseDouble(args[3]);
    	
    	// Set the drone's starting position
    	drone.position = Point.fromLngLat(droneStartLng, droneStartLat);
    	
    	// Prepend the drone's starting position to the start of the coordinates ArrayList
    	coordinates.add(0, drone.position);
    	
    	// Create a variable to store the permutation
    	var dronePath = new ArrayList<Integer>();
    	
    		// Fill the list with integers in ascending order, 1 to represent sensor 1
    		// and so forth
    		for(int i = 0; i < coordinates.size(); i++)
    		{
    			dronePath.add(i);
    		}
    		
    	// Define variables
    	var visited = new ArrayList<Integer>();
    	int closestSensor;
    	boolean closerFound;
    	double closestDist;
    	double dist;
    	int closestSensorNumber = 0;
    	
    	// Determine which sensor is the closest to the current sensor, given it has not already
    	// been visited. Loop for each remaining sensor
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
    	
    	return dronePath;
    }
    
    public static double euclidDist(Point pos1, Point pos2)
    /**
     * This method will return the Euclidean distance between two points.
     */
    {
    	double x1minusx2 = pos1.longitude()-pos2.longitude();
    	double y1minusy2 = pos1.latitude()-pos2.latitude();
    	double dist = Math.sqrt(Math.pow(x1minusx2, 2) + Math.pow(y1minusy2, 2) ); 
    	return dist;
    }
    
    /**
     * The following method was removed due to an inability to calculate
     * whether the drone would enter a no fly zone
     * 
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
    */
    
    public static boolean lineIntersectPolygon(Point startPos, Point endPos, FeatureCollection nfz)
    /**
     * The following method is used to determine whether the drone enters a 'No Fly Zone'.
     * The drone's next trajectory will be checked to see whether it intersects any of the lines
     * described by the GeoJSON polygons given in the 'No Fly Zone' Feature Collection.
     */
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
    /**
     * The following method determines whether the drone is keeping within the 
     * boundaries given in the specification.
     */
    {
    	double maxLatBoundary = 55.946233;
    	double minLatBoundary = 55.942617;
    	double maxLngBoundary = -3.184319;
    	double minLngBoundary = -3.192473;
    	
    	return pos.latitude() < maxLatBoundary 
    		 & pos.latitude() > minLatBoundary 
    		 & pos.longitude() < maxLngBoundary 
    		 & pos.longitude() > minLngBoundary;
    }
    
    public static ArrayList<List<String>> flightPath(Random rand, Drone drone, ArrayList<SensorsList> sensors,
    												 ArrayList<Integer> dronePath, ArrayList<Point> coordinates, 
    												 FeatureCollection nfz, ArrayList<Feature> flightPathFeatures, String[] args) throws IOException
    /**
     * This method calculates the path the drone will take during its air quality data collection.
     * The drone will visit the sensors in the order given by the pathfinding algorithm and will determine the shortest route to get to that sensor
     * using the Euclidean distance formula. The drone can only fly in angles that are in multiples of 10, with 0 degrees implying East,
     * 90 degrees implying North, 180 degrees implying West and 270 degrees implying South.
     * 
     * The method will add the readings and battery level of each individual sensor and return it. A text file documenting the drone's
     * flightpath will also be created and written into the current directory.
     */
    {
    	var flightpathTextList = new ArrayList<List<String>>();
    	var movementHistory = new ArrayList<Point>();
    	var projDistList = new ArrayList<Double>();
    	var angleHistory = new ArrayList<Integer>();
    	var readings = new ArrayList<String>();
    	var batteries = new ArrayList<String>();
    	
    	double deltaLng = 0;
    	double deltaLat = 0;
    	double newDist;
    	
    	boolean sensProx = false;
    	
    	Point newPosition;
    	Point oldPosition;
    	Point projPos;
    	
    	String sensorW3W;
    	
    	int index;
    	int newAngle;
    	
    	drone.moves = 0;
    	
    	// Initialising the angleHistory Array List with 360 as the first element
    	// to avoid an index out of bounds exception
    	angleHistory.add(360);
    	
    	// Loop until all of the individual sensors have been visited
    	for(int i = 1; i < dronePath.size(); i++)
    	{
    		sensProx = false;
    		
    		// Loop until the drone is close enough to a sensor to take a reading (<= 0.0002 degrees)
    		// or the maximum move limit is reached
    		while (!sensProx & drone.moves < 150)
	    	{
    			// Initialise the what3words address as null, so it can be better used
    			// in the flightpath text file
    			sensorW3W = "null";
    			
    			// Record all the positions of the drone to later be added as a feature in
    			// the GeoJSON map
    			movementHistory.add(drone.position);
    			
    			oldPosition = drone.position;
    			
	    		// Determine angle of travel
	    		deltaLat = coordinates.get(dronePath.get(i)).latitude() - drone.position.latitude();
	    		deltaLng = coordinates.get(dronePath.get(i)).longitude() - drone.position.longitude();
	    		drone.angle = (int) Math.round((Math.toDegrees(Math.atan2(deltaLat, deltaLng)))/10)*10;
	    		
	    		// Convert negative angles into their positive counterparts
	    		drone.angle = ((drone.angle + 360) % 360);	    
	    		
	    		// Update the drone's position
	    		newPosition = Point.fromLngLat(drone.position.longitude() + (Math.cos(Math.toRadians(drone.angle)) * 0.0003), 
	    									   drone.position.latitude() + (Math.sin(Math.toRadians(drone.angle)) * 0.0003));
	    		
	    		// Check to see whether the new position is within a no fly zone, and if so, update the angle of trajectory
	    		// If the drone has doubled back on itself, instead send the drone in a random direction to remove the chance
	    		// of the drone being stuck in a permanent loop
	    		while(lineIntersectPolygon(drone.position, newPosition, nfz) || !insideBoundary(newPosition) || (angleHistory.get(drone.moves)+180)%360 == drone.angle)
	    		{
	    				if((angleHistory.get(drone.moves)+180)%360 == drone.angle)
	    				{
	    					drone.angle = (drone.angle + 10 * (rand.nextInt(35))) % 360;
	    				}
	    				else
	    				{
	    					for(int j = 1; j < 36; j++)
	    					{
	    						newAngle = (drone.angle + 10 * j) % 360;
	    						projPos = Point.fromLngLat(drone.position.longitude() + (Math.cos(Math.toRadians(newAngle)) * 0.0003), 
 									   					   drone.position.latitude() + (Math.sin(Math.toRadians(newAngle)) * 0.0003));
	    						if(!lineIntersectPolygon(drone.position, projPos, nfz) & insideBoundary(projPos) & !((newAngle + 180) % 360 == drone.angle))
	    						{
	    							projDistList.add(1/(euclidDist(projPos, coordinates.get(dronePath.get(i)))));
	    						}
	    						else
	    						{
	    							projDistList.add(-1.0);
	    						}
	    						
	    					}
	    					index = projDistList.indexOf(Collections.max(projDistList)) + 1;
	    					drone.angle = (drone.angle + 10 * index) % 360;
	    				}
	   
		    			newPosition = Point.fromLngLat(drone.position.longitude() + (Math.cos(Math.toRadians(drone.angle)) * 0.0003), 
		    										   drone.position.latitude() + (Math.sin(Math.toRadians(drone.angle)) * 0.0003));
	    		}
	    		angleHistory.add(drone.angle);
	    		
	    		// Update the drone
	    		drone.position = newPosition;
	    		drone.moves ++;
	    		
	    		// Get new distance from drone to sensor
	    		newDist = euclidDist(drone.position, coordinates.get(dronePath.get(i)));
	    		
	    		// Check whether the drone is close enough to a sensor to read it, and if so, take a reading
	    		if(newDist <= 0.0002)
	    		{
	    			
	    			if(i < dronePath.size()-1)
					{
						readings.add(sensors.get(dronePath.get(i)-1).reading);
						batteries.add(String.valueOf(sensors.get(dronePath.get(i)-1).battery));
						sensorW3W = sensors.get(dronePath.get(i)-1).location;
					}
	    			
	    			sensProx = true;
	    		}
	    		
	    		// Add all the required information for the flightpath-DD-MM-YYYY.txt file
	    		flightpathTextList.add(List.of(Integer.toString(drone.moves),
	    								   Double.toString(oldPosition.longitude()),
	    								   Double.toString(oldPosition.latitude()),
	    								   Integer.toString(drone.angle),
	    								   Double.toString(drone.position.longitude()),
	    								   Double.toString(drone.position.latitude()),
	    								   sensorW3W));
	    	}
    	}
    	
    	/** This can be used to check the total number of moves the drone performs
    	 * System.out.println("\nTotal number of moves = " + drone.moves);
    	 */
    	
    	// Write the contents of flightpathTextList to a .txt file
    	flightpathWrite(flightpathTextList, args);
    	
    	// Add the obtained data to an output list of Array Lists
    	Feature dronePositions;
    	var sensorOutput = new ArrayList<List<String>>();
    	sensorOutput.add(readings);
    	sensorOutput.add(batteries);
    	
    	// Add the drone positions as a line string
    	dronePositions = Feature.fromGeometry(LineString.fromLngLats(movementHistory));
    	
    	// Give the lines a dark grey colour
    	dronePositions.addStringProperty("rgb-string", "#404040");
    	flightPathFeatures.add(dronePositions);
    	
    	return sensorOutput;
    }
    
    public static void flightpathWrite(ArrayList<List<String>> flightpathTextList, String[] args) throws IOException
    /**
     * This method writes the flight data obtained by the drone's flightpath to a text file.
     */
    {
    	String fileName = "flightpath-" + args[0] + "-" + args[1] + "-" + args[2] + ".txt";
    	BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
    	
    	for(int i = 0; i < flightpathTextList.size(); i++)
    	{
    		for(int j = 0; j < flightpathTextList.get(i).size()-1; j++)
    		{
    			bw.write(flightpathTextList.get(i).get(j) + ",");
    		}
    		bw.write(flightpathTextList.get(i).get(flightpathTextList.get(i).size()-1));
    		bw.newLine();
    	}
    	
    	bw.flush();
    	bw.close();
    }
    
    public static ArrayList<List<String>> hexCodeConversion(ArrayList<List<String>> sensorOutput)
    /**
     * This method takes the reading and battery data from the sensors and uses them to determine
     * the colour and symbol the corresponding points will have on the GeoJSON map. 
     */
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
    public static void geojsonConvert(ArrayList<List<String>> sensorMap, ArrayList<SensorsList> sensors, ArrayList<Integer> dronePath, 
    								  ArrayList<Point> coordinates, ArrayList<Feature> flightPathFeatures, FeatureCollection nfz, String[] args) throws IOException
    /**
     * This method will create a list of features from the sensors and their associated values and add them to a GeoJSON Feature Collection, along with the drone's flightpath.
     * This feature collection will be converted to GeoJSON format and written to a file called 'readings-DD-MM-YYY-.geojson', where DD, MM and YYYY represent the date
     * of the drone's flight.
     */
    {
    	// Creating a list to store the features and a File Writer to write the geojson file
    	var featureList = new ArrayList<Feature>();
    	String fileName = "readings-" + args[0] + "-" + args[1] + "-" + args[2] + ".geojson";
    	var jsonFile = new FileWriter(fileName);
    	
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
    	
    	/** The following code can be used to visualise the no fly zones on the GeoJSON map
    	 * 
    	var buildingsList = new ArrayList<Feature>();
    	 	for(int i = 0; i < nfz.features().size(); i++)
    	   	{
    	    	buildingsList.add(nfz.features().get(i));
    			buildingsList.get(i).addStringProperty("rgb-string", "#ff0000");
    			buildingsList.get(i).addStringProperty("fill", "#ff0000");
    			buildingsList.get(i).addNumberProperty("fill-opacity", 0.75);
    		}
    	
    	
    		for(int i = 0; i < buildingsList.size(); i++)
    		{
    			featureList.add(buildingsList.get(i));
    		}
    	*/
    		    	
    	
    	// Create a feature collection from the list of features described above
    	// Add the drone's movements to the feature list
    	for(int i = 0; i < flightPathFeatures.size(); i++)
    	{
    		featureList.add(flightPathFeatures.get(i));
    	}
    	
    	
    	
    	// Add all of the features from the feature list into a feature collection
    	var featureCol = FeatureCollection.fromFeatures(featureList);
    	
    	//Convert the feature collection to JSON format, write it to the file and then close the file 
    	jsonFile.write(featureCol.toJson());
    	jsonFile.close();
    }
}
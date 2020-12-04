package uk.ac.ed.inf.aqmaps;

import java.awt.geom.Line2D;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class Controller {
	/**
	 * This class is used to Control the direction of the program, it hosts multiple
	 * methods to aid in the drone's navigation of the sensors, exporting the readings
	 * graphically and providing a text file documenting the drone's movements.
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 */
	
	private String[] date;
	
	private Server server;
	private ArrayList<Sensor> sensors;
	private ArrayList<List<String>> sensorData;
	private FeatureCollection nfz;
	
	private Drone drone;
	
	private String log;
	private Random rand;
	private Feature pointFeature;
	private ArrayList<Point> movementHistory;
	
	private ArrayList<String> colourMap;
	private ArrayList<String> symbolMap;
	
	public Controller(String[] date, Point startPosition, Long seed, String host, String port)
	{
		this.date = date;
		this.server = new Server(host, port);
		this.drone = new Drone(startPosition);
		this.rand = new Random(seed);
		this.log = "";
		this.movementHistory = new ArrayList<Point>();
		this.movementHistory.add(startPosition);
		this.sensorData = new ArrayList<List<String>>();
		this.colourMap = new ArrayList<String>();
		this.symbolMap = new ArrayList<String>();
	}
	public String toString()
	{
		String stringOutput = "Drone moves = " + drone.getMoves(); 
		
		return stringOutput;
	}
	
	public double euclidDist(Point pos1, Point pos2)
    /**
     * This method will return the Euclidean distance between two points.
     */
    {
    	double x1minusx2 = pos1.longitude()-pos2.longitude();
    	double y1minusy2 = pos1.latitude()-pos2.latitude();
    	double dist = Math.sqrt(Math.pow(x1minusx2, 2) + Math.pow(y1minusy2, 2) ); 
    	return dist;
    }
	
	public void serverDownload() throws IOException, InterruptedException
	{
		sensors = server.dailySensors(date);
		nfz = server.noFlyZones();
		
		for(Sensor s : sensors)
		{
			s.setCoordinates(server.pointFromW3W(s.getLocation()));
		}
	}
	
	public void orderSensors()
	/**
	 * This method will set up a 'Greedy' pathfinding algorithm that will, given the coordinates of each
	 * sensor and the starting position of the drone, return a list of integers describing the 
	 * permutation in which the drone will visit the sensors (including returning to the start point).
	 */
	{		
		// Create a variable to store the permutation
		var orderedPath = new ArrayList<Sensor>();
		
		// Find closest sensor to the drone's starting position and add to orderedPath
		int closestSensorIndex = 0;
		double closestDist = euclidDist(sensors.get(0).getCoordinates(), drone.getPosition());
		
		for(int i = 1; i < sensors.size(); i++) 
		{
			double dist = euclidDist(drone.getPosition(), sensors.get(i).getCoordinates());
			if(dist < closestDist)
			{
				closestSensorIndex = i;
				closestDist = dist; 
			}
		}
		orderedPath.add(sensors.get(closestSensorIndex));
		sensors.remove(closestSensorIndex);
		
		// Find shortest path from starting sensor through remaining sensors
		while(sensors.size() > 0)
		{
			closestSensorIndex = 0;
			closestDist = euclidDist(orderedPath.get(orderedPath.size()-1).getCoordinates(), sensors.get(closestSensorIndex).getCoordinates());
			for(int i = 0; i < sensors.size(); i++)
			{
				double dist = euclidDist(orderedPath.get(orderedPath.size()-1).getCoordinates(), sensors.get(i).getCoordinates());
				if(dist < closestDist)
				{
					closestSensorIndex = i;
					closestDist = dist;
				}
			}
			orderedPath.add(sensors.get(closestSensorIndex));
			sensors.remove(closestSensorIndex);
		}
		
		sensors = orderedPath;

	}
	
	public void droneFlight() throws IOException
	/**
	 * This method calculates the path the drone will take during its air quality data collection.
	 * The drone will visit the sensors in the order given by the pathfinding algorithm and will determine the shortest route to get to that sensor
	 * using the Euclidean distance formula. The drone can only fly in angles that are in multiples of 10, with 0 degrees implying East,
	 * 90 degrees implying North, 180 degrees implying West and 270 degrees implying South.
	 * 
	 * The method will add the readings and battery level of each individual sensor and return it.	
	 */
	{
		var projDistList = new ArrayList<Double>();
		var angleHistory = new ArrayList<Integer>();

		double deltaLng = 0;
		double deltaLat = 0;
		double newDist;

		boolean sensProx = false;

		Point newPosition;
		Point oldPosition;
		Point projPos;
		Point targetPoint;

		String sensorW3W;

		int index;
		int newAngle;

		// Initialising the angleHistory Array List with 360 as the first element
		// to avoid an index out of bounds exception
		angleHistory.add(drone.getAngle());
		

		// Loop until all of the individual sensors have been visited
		for(int i = 0; i <= sensors.size(); i++)
		{
			sensProx = false;
			targetPoint = i == sensors.size() ? drone.getStartPosition() : sensors.get(i).getCoordinates();
			System.out.println("Target point = " + targetPoint);
			// Loop until the drone is close enough to a sensor to take a reading (<= 0.0002 degrees)
			// or the maximum move limit is reached
			while (!sensProx & drone.getMoves() < 150)
			{
				
				System.out.println("Drone moves = " + drone.getMoves());
				// Initialise the what3words address as null, so it can be better used
				// in the flightpath text file
				sensorW3W = "null";
				
				// Get old position for movement log
				oldPosition = drone.getPosition();
				movementHistory.add(drone.getPosition());

				// Determine angle of travel
				deltaLat = targetPoint.latitude()  - drone.getPosition().latitude();
				deltaLng = targetPoint.longitude() - drone.getPosition().longitude();
				drone.setAngle((int) Math.round((Math.toDegrees(Math.atan2(deltaLat, deltaLng)))/10)*10);

				// Convert negative angles into their positive counterparts
				drone.setAngle((drone.getAngle() + 360) % 360);	    

				// Update the drone's position
				newPosition = Point.fromLngLat(drone.getPosition().longitude() + (Math.cos(Math.toRadians(drone.getAngle())) * 0.0003), 
											   drone.getPosition().latitude()  + (Math.sin(Math.toRadians(drone.getAngle())) * 0.0003));

				// Check to see whether the new position is within a no fly zone, and if so, update the angle of trajectory
				// If the drone has doubled back on itself, instead send the drone in a random direction to remove the chance
				// of the drone being stuck in a permanent loop
				while(lineIntersectPolygon(drone.getPosition(), newPosition, nfz) 
						|| !insideBoundary(newPosition) 
						|| (angleHistory.get(drone.getMoves())+180)%360 == drone.getAngle())
				{		
					// If the drone flies back to the same point, send it in a random direction instead
					if((angleHistory.get(drone.getMoves())+180)%360 == drone.getAngle())
					{
						drone.setAngle((drone.getAngle() + 10 * (rand.nextInt(35))) % 360);
					}
					// Else, find the best new angle to send it to
					else
					{
						for(int j = 1; j < 36; j++)
						{
							newAngle = (drone.getAngle() + 10 * j) % 360;
							projPos = Point.fromLngLat(drone.getPosition().longitude() + (Math.cos(Math.toRadians(newAngle)) * 0.0003), 
													   drone.getPosition().latitude()  + (Math.sin(Math.toRadians(newAngle)) * 0.0003));
							if(!lineIntersectPolygon(drone.getPosition(), projPos, nfz) 
									& insideBoundary(projPos) 
									& !((newAngle + 180) % 360 == drone.getAngle()))
							{
								projDistList.add(1/(euclidDist(projPos, targetPoint)));
							}
							else
							{
								projDistList.add(-1.0);
							}

						}
						index = projDistList.indexOf(Collections.max(projDistList)) + 1;
						drone.setAngle((drone.getAngle() + 10 * index) % 360);
					}

					newPosition = Point.fromLngLat(drone.getPosition().longitude() + (Math.cos(Math.toRadians(drone.getAngle())) * 0.0003), 
												   drone.getPosition().latitude()  + (Math.sin(Math.toRadians(drone.getAngle())) * 0.0003));
				}
				
				angleHistory.add(drone.getAngle());

				// Update the drone
				drone.setPosition(newPosition);
				drone.setMoves(drone.getMoves() + 1);

				// Get new distance from drone to sensor
				newDist = euclidDist(drone.getPosition(), targetPoint);

				// Check whether the drone is close enough to a sensor to read it, and if so, take a reading
				if(newDist <= 0.0002)
				{
					
					if(i < sensors.size())
					{
						var sensorDatum = new ArrayList<String>();
						sensorDatum.add(sensors.get(i).getReading());
						sensorDatum.add(String.valueOf(sensors.get(i).getBattery()));
						
						sensorData.add(sensorDatum);

						sensorW3W = sensors.get(i).getLocation();
					}
					
					sensProx = true;
				}	
				
				// Log all the moves
				logMove(drone.getMoves(), oldPosition, drone.getAngle(), newPosition, sensorW3W);
				System.out.println(i);
			}
		}

		// Add the drone positions as a line string
		pointFeature = (Feature.fromGeometry(LineString.fromLngLats(movementHistory)));

		// Give the lines a dark grey colour
		pointFeature.addStringProperty("rgb-string", "#404040");

	}

	public void logMove(int moves, Point oldPosition, int angle, Point newPosition, String sensorW3W)
	{
		log = log + String.valueOf(moves) 					+ ","
				  + String.valueOf(oldPosition.longitude()) + ","
				  + String.valueOf(oldPosition.latitude())  + ","
				  + String.valueOf(angle)					+ ","
				  + String.valueOf(newPosition.longitude()) + ","
				  + String.valueOf(newPosition.latitude())  + ","
				  + sensorW3W
				  + "\n";
	}
	
	public void logToFile() throws IOException
	{
		if(log.length() == 0) return;
		
		// Remove whitespace
		log.trim();
		
		String fileName = "flightpath-" + date[0] + "-" + date[1] + "-" + date[2] + ".txt";
    	FileWriter fw = new FileWriter(fileName);
    	
    	fw.write(log);
    	 
    	fw.close();
	}
	
	/*public static void main( String[] args ) throws IOException, InterruptedException
    		/**
     * This is the main() method in which all of the other methods in the class will be called
     * and where the Drone and Random instances will be created

    {
    	// Create an instance of the Drone class, this drone will be manipulated throughout the execution
    	// of the program
        Drone drone = new Drone();
        
        // Create an instance of type Random, taking in the 'seed' from the arguments list
        Random rand = new Random(Long.parseLong(args[5]));
        drone.setRand(rand);

        // Obtain the no fly zones, the list of daily sensors and their respective coordinates
        FeatureCollection nfz = noFlyZones(args);     
        ArrayList<Sensor> sensors = dailySensors(args);
        var coordinates = what3words(args, sensors);
        
        // Set and get the path permutation
        drone.setPath(coordinates, args);
        var dronePath = drone.getPath();
        
        // Set and get the flight path
        drone.setFlight(sensors, coordinates, nfz, args);
        var sensorMap = hexCodeConversion(drone.getFlight());
        
        Feature flightFeature = drone.getFlightFeature();
        geojsonConvert(sensorMap, sensors, dronePath, coordinates, flightFeature, nfz, args);
        
    	//This can be used to check the total number of moves the drone performs
    	System.out.println("\nTotal number of moves = " + drone.getMoves());
    	 
    }
	*/
	
//	// Just have one HttpClient, shared between all HttpRequests
//    private static final HttpClient client = HttpClient.newHttpClient();
//    
//    public static FeatureCollection noFlyZones(String[] args) throws IOException, InterruptedException
//    /**
//     * This method will load in the 'No Fly Zones' from the web server and parse them as a JSON formatted
//     * file.
//     * The file's will then be translated to a GeoJSON Feature Collection and returned, for use elsewhere.
//     */
//    {
//    	// Load in the "No Fly Zones" for the drone
//    	// HttpClient assumes that it is a GET request by default.
//    	String urlString = "http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson";
//    	var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
//    	
//    	// The response object is of class HttpResponse<String>
//    	var response = client.send(request, BodyHandlers.ofString());
//    	
//    	// Create a variable of type Feature Collection to store the data
//    	var nfz = FeatureCollection.fromJson(response.body());
//    	
//    	return nfz;
//    }
//    
//    public static ArrayList<Sensor> dailySensors(String[] args) throws IOException, InterruptedException
//    /**
//     * This method will load the list of sensors for the specific date (given with args[])
//     * from the web server and parse them as a JSON formatted file. It will then
//     * return them as an Array List of type SensorsList.
//     */
//    {	
//    	// Load in the list of sensors for that day
//    	String urlString = "http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json";
//    	var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
//    	
//    	// The response object is of class HttpResponse<String>
//    	var response = client.send(request, BodyHandlers.ofString());
//    	
//    	// Get the types from the SensorsList class
//    	Type listType = new TypeToken<ArrayList<Sensor>>() {}.getType();
//    	
//    	// Create a variable of type ArrayList<SensorsList>
//    	ArrayList<Sensor> sensors = new Gson().fromJson(response.body(), listType);
//    	
//    	return sensors;
//    }
//    
//    public static ArrayList<Point> what3words(String[] args, ArrayList<Sensor> sensors) throws InterruptedException, IOException
//    /**
//     * This method will determine the coordinates of each sensor using their associated 'What3Words' address.
//     * The file returned from the web server will then have its contents attributed to certain types, based
//     * on the DeserialisedW3W class. The longitude and latitude coordinates for each sensor will then be
//     * extracted and stored in an Array List of type Point and returned.  
//     */
//    {
//    	// Create a variable to store the coordinates and loop through all of the sensors, obtaining their coordinates
//    	var coordinates = new ArrayList<Point>();
//    	for(int i = 0; i < 33; i++) {
//    		String location = sensors.get(i).location;
//    		String[] what3words = location.split("\\.");
//    		String urlString = "http://localhost:" + args[6] + "/words/" + what3words[0] + "/" + what3words[1] + "/" + what3words[2] + "/details.json";
//    		
//    		// Load in all the sensor details from What3Words address
//    		var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
//    		// The response object is of class HttpResponse<String>
//    		var response = client.send(request, BodyHandlers.ofString());
//    		
//    		// Create a variable to store the response from the webServer and then add the appropriate
//    		// information to the coordinates Array List
//    		var DeserialisedW3W = new Gson().fromJson(response.body(), DeserialisedW3W.class);   
//    		coordinates.add(Point.fromLngLat(DeserialisedW3W.coordinates.lng, DeserialisedW3W.coordinates.lat));
//    		
//    	}
//    	return coordinates;
//    }
    
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

//    public static void flightpathWrite(ArrayList<List<String>> flightpathTextList, String[] args) throws IOException
//    /**
//     * This method writes the flight data obtained by the drone's flightpath to a text file.
//     */
//    {
//    	String fileName = "flightpath-" + args[0] + "-" + args[1] + "-" + args[2] + ".txt";
//    	BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
//    	
//    	for(int i = 0; i < flightpathTextList.size(); i++)
//    	{
//    		for(int j = 0; j < flightpathTextList.get(i).size()-1; j++)
//    		{
//    			bw.write(flightpathTextList.get(i).get(j) + ",");
//    		}
//    		bw.write(flightpathTextList.get(i).get(flightpathTextList.get(i).size()-1));
//    		bw.newLine();
//    	}
//    	
//    	bw.flush();
//    	bw.close();
//    }
//    
    public void hexCodeConversion()
    /**
     * This method takes the reading and battery data from the sensors and uses them to determine
     * the colour and symbol the corresponding points will have on the GeoJSON map. 
     */
    {
    	for(int i = 0; i < sensorData.size(); i++)
    	{
    		// Black, cross
    		if (Double.parseDouble(sensorData.get(i).get(1)) < 10)
    		{
    			colourMap.add(i, "#000000");
    			symbolMap.add(i, "cross");
    		}
    		// Green, lighthouse
    		else if (Double.parseDouble(sensorData.get(i).get(0)) >= 0 && Double.parseDouble(sensorData.get(i).get(0)) < 32)
    		{
    			colourMap.add(i, "#00ff00");
    			symbolMap.add(i, "lighthouse");
    		}
    		// Medium green, lighthouse
    		else if (Double.parseDouble(sensorData.get(i).get(0)) >= 32 && Double.parseDouble(sensorData.get(i).get(0)) < 64)
    		{
    			colourMap.add(i, "#40ff00");
    			symbolMap.add(i, "lighthouse");
    		}
    		// Light green, lighthouse
    		else if (Double.parseDouble(sensorData.get(i).get(0)) >= 64 && Double.parseDouble(sensorData.get(i).get(0)) < 96)
    		{
    			colourMap.add(i, "#80ff00");
    			symbolMap.add(i, "lighthouse");
    		}
    		// Lime green, lighthouse
    		else if (Double.parseDouble(sensorData.get(i).get(0)) >= 96 && Double.parseDouble(sensorData.get(i).get(0)) < 128)
    		{
    			colourMap.add(i, "#c0ff00");
    			symbolMap.add(i, "lighthouse");
    		}
    		// Gold, danger
    		else if (Double.parseDouble(sensorData.get(i).get(0)) >= 128 && Double.parseDouble(sensorData.get(i).get(0)) < 160)
    		{
    			colourMap.add(i, "#ffc000");
    			symbolMap.add(i, "danger");
    		}
    		// Orange, danger
    		else if (Double.parseDouble(sensorData.get(i).get(0)) >= 160 && Double.parseDouble(sensorData.get(i).get(0)) < 192)
    		{
    			colourMap.add(i, "#ff8000");
    			symbolMap.add(i, "danger");
    		}
    		// Red/Orange, danger
    		else if (Double.parseDouble(sensorData.get(i).get(0)) >= 192 && Double.parseDouble(sensorData.get(i).get(0)) < 224)
    		{
    			colourMap.add(i, "#ff4000");
    			symbolMap.add(i, "danger");
    		}
    		// Red, danger
    		else if (Double.parseDouble(sensorData.get(i).get(0)) >= 224 && Double.parseDouble(sensorData.get(i).get(0)) < 256)
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
    }
    public void geojsonConvert() throws IOException
    /**
     * This method will create a list of features from the sensors and their associated values and add them to a GeoJSON Feature Collection, along with the drone's flightpath.
     * This feature collection will be converted to GeoJSON format and written to a file called 'readings-DD-MM-YYY-.geojson', where DD, MM and YYYY represent the date
     * of the drone's flight.
     */
    {
    	// Creating a list to store the features and a File Writer to write the geojson file
    	var featureList = new ArrayList<Feature>();
    	String fileName = "readings-" + date[0] + "-" + date[1] + "-" + date[2] + ".geojson";
    	var jsonFile = new FileWriter(fileName);
    	
    	// Loop through the feature list, adding the points and their associated attributes
    	for(int i = 0; i < sensors.size(); i++)
    	{
    		featureList.add(Feature.fromGeometry(sensors.get(i).getCoordinates()));
    		featureList.get(i).addStringProperty("marker-size", "medium");
    		featureList.get(i).addStringProperty("location", sensors.get(i).getLocation());
    		featureList.get(i).addStringProperty("rgb-string", colourMap.get(i));
    		featureList.get(i).addStringProperty("marker-color", colourMap.get(i));
    		featureList.get(i).addStringProperty("marker-symbol", symbolMap.get(i));
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
    	featureList.add(pointFeature);
    	
    	// Add all of the features from the feature list into a feature collection
    	var featureCol = FeatureCollection.fromFeatures(featureList);
    	
    	//Convert the feature collection to JSON format, write it to the file and then close the file 
    	jsonFile.write(featureCol.toJson());
    	jsonFile.close();
    }
}
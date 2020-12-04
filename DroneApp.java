package uk.ac.ed.inf.aqmaps;

import java.io.IOException;

import com.mapbox.geojson.Point;

/**Title: Air Quality Drone
 * Author: Ben Nichol 14/11/20
 *
 * Description: This program will create a virtual drone that, given an input of sensors, with 
 * 				their locations given as what3words addresses, will find an efficient path through
 * 				these sensors and report back their associated air quality readings, export them as a geojson
 * 				file, and produce a flightpath.txt file presenting the movements of the drone through that path. 
 */

public class DroneApp
/**
 * This class acts as the entry point into the program.
 */
{
	public static void main(String[] args) throws IOException, InterruptedException
	{
		// Parse input arguments
		String[] date = new String[3];
		date[0] = args[0];
		date[1] = args[1];
		date[2] = args[2];
		
		Point startPosition = Point.fromLngLat(Double.parseDouble(args[4]), Double.parseDouble(args[3]));
		long seed = Long.parseLong(args[5]);
		String port = args[6];
		
		// Call the controller class' methods
		Controller controller = new Controller(date, startPosition, seed, "http://localhost:", port);
		controller.serverDownload();
		System.out.println("Server Download done");
		controller.orderSensors();
		System.out.println("Ordering of sensors done");
		controller.droneFlight();
		System.out.println("Drone flight done");
		controller.logToFile();
		System.out.println("Log to file done");
		controller.hexCodeConversion();
		System.out.println("Hex code conversion done");
		controller.geojsonConvert();
		System.out.println("GeoJSON conversion done");
		System.out.println(controller.toString());
	}
}
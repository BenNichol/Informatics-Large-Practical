package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;

public class Server
/**
 * This class is used to perform all the connection required to the server,
 * downloading data and returning it when required. 
 */
{
	// Define attributes
	private String host;
	private String port;
    private final HttpClient client;
	
    // Constructor
	public Server(String host, String port) {
		this.host = host;
		this.port = port;
		this.client = HttpClient.newHttpClient();
	}
	
	public Point pointFromW3W(String what3words) throws IOException, InterruptedException
	/**
	 * This method will return the coordinate of the sensor corresponding to the particular
	 * 'What3Words' address from the webserver.
	 */
	{
		String[] words = what3words.split("\\.");
		String urlString = host + port + "/words/" + words[0] + "/" + words[1] + "/" + words[2] + "/details.json";
		
		var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
		// The response object is of class HttpResponse<String>
		var response = client.send(request, BodyHandlers.ofString());
		
		// Create a variable to store the response from the webServer
		var DeserialisedW3W = new Gson().fromJson(response.body(), DeserialisedW3W.class);
		
		return Point.fromLngLat(DeserialisedW3W.coordinates.lng, DeserialisedW3W.coordinates.lat);
	}
	
	public FeatureCollection noFlyZones() throws IOException, InterruptedException 
	/**
	 * This method will return a Feature Collection of the 'No Fly Zones' from the
	 * webserver.
	 */
	{
		String urlString = host + port + "/buildings/no-fly-zones.geojson";
		
		var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
    	
    	// The response object is of class HttpResponse<String>
    	var response = client.send(request, BodyHandlers.ofString());
    	
    	// Create a variable of type Feature Collection to store the data
    	var nfz = FeatureCollection.fromJson(response.body());
    	
    	return nfz;
	}
	
	public ArrayList<Sensor> dailySensors(String[] date) throws IOException, InterruptedException
	/**
	 * This method will return the list of 33 sensors from the webserver.
	 */
	{
		String urlString = host + port + "/maps/" + date[2] + "/" + date[1] + "/" + date[0] + "/air-quality-data.json";
		
		var request = HttpRequest.newBuilder().uri(URI.create(urlString)).build();
    	
    	// The response object is of class HttpResponse<String>
    	var response = client.send(request, BodyHandlers.ofString());
    	
    	// Get the types from the SensorsList class
    	Type listType = new TypeToken<ArrayList<Sensor>>() {}.getType();
    	
    	// Create a variable of type ArrayList<SensorsList>
    	ArrayList<Sensor> sensors = new Gson().fromJson(response.body(), listType);
    	
    	return sensors;
	}
}
package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public class Sensor
/**
 * This class allows for the JSON list of sensors downloaded by the
 * webserver to be deserialised to a java object using its type. It
 * also allows for the list of sensors to hold more information
 * than is given in the JSON list of sensors by adding a coordinates
 * attribute.
 */
{
	// Define attributes
	private String location;
	private double battery;
	private String reading;
	
	private Point coordinates;

	// Getters
	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public double getBattery() {
		return battery;
	}

	public void setBattery(double battery) {
		this.battery = battery;
	}

	public String getReading() {
		return reading;
	}

	// Setters
	public void setReading(String reading) {
		this.reading = reading;
	}

	public Point getCoordinates() {
		return coordinates;
	}

	public void setCoordinates(Point coordinates) {
		this.coordinates = coordinates;
	}
	
	
}
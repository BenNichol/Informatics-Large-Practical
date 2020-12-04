package uk.ac.ed.inf.aqmaps;

public class DeserialisedW3W
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
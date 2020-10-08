package uk.ac.ed.inf.heatmap;

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

/**
 * The following application will take a list of predictions and convert the data into
 * a format compatible with Geo-JSON.  This Geo-JSON document will then be used to produce
 * a heatmap
 * 
 * Written by Ben Nichol (s1805741) 
 */

public class App 
{
	
    public static void main(String[] args) throws Exception
    {
    	String[] pathname = args;
    	String[] predictions = predictionReader(pathname);
    	var polyCoords =  gridCoords();
    	var polygons = createPolygons(polyCoords);
    	var colourMap = hexCodeConversion(predictions);
    	geojsonConvert(polygons, colourMap);
    }
    
    public static String[] predictionReader(String[] args) throws Exception
    //predictionReader takes an pathname and reads the associated file, printing
    //the contents
    {
    	// The file-path is passed as a parameter
        var fr = new FileReader(args[0]); 
        int i;
        String readings = "";
        // Loop through the text in the file, adding each character to a string, stop when EOF is reached
        while ((i=fr.read()) != -1)
        {
        	readings = readings + (char) i;
        }
        // Modify the string for ease of delimiting and separating into an array
        readings = readings.replace("\n", ",").replace("\r", "");
        readings = readings.replace(" ", "");
        String[] predictions = readings.split(",");
        
        fr.close();
        return predictions;
    }
    
    public static List<List<Point>> gridCoords()
    // This method will return the variable "polyCoords", containing 100 lists of 5 coordinates, with each
    // list describing a single polygon in the 10x10 grid
    {
    	// These four coordinates describe the boundary of the drones flying zone
    	double lon1 = -3.192473;
    	double lon2 = -3.184319;
    	double lat1 = 55.946233;
    	double lat2 = 55.942617;
    	
    	// These values are used to separate the grid into a 10x10 area
    	double lonGridLen = (lon2 - lon1)/10;
    	double latGridLen = (lat2 - lat1)/10;
    	
    	var coordinates = new ArrayList<Point>();
    	var polyCoords = new ArrayList<List<Point>>();
    	
    	// Loop through all 121 points on the grid, matching each longitude and latitude and storing them
    	// in a list of points "coordinates"
    	for(int i = 0; i < 11; i++)
    	{
    		for(int j = 0; j < 11; j++)
    		{
    			coordinates.add(Point.fromLngLat(lon1 + lonGridLen*(double)j, lat1 + latGridLen*(double)i));
    		}
    	}
    	// Loop through each rectangle in the grid, adding the five corresponding coordinates for each polygon
    	// into a list, then adding that list to a list of lists of points that describe each polygon, called
    	// "polyCoords"
    	
    	for(int i = 0; i < 10; i++)
    	{
    		for(int j = 0; j < 10; j++)
    		{
    			// The 5 coordinates are added clockwise, starting and ending at the most north-westerly coordinate  
    			polyCoords.add(List.of((coordinates.get(j+(i*11))),
    								   (coordinates.get(j+1+(i*11))),
    								   (coordinates.get(j+12+(i*11))),
    								   (coordinates.get(j+11+(i*11))),
    								   (coordinates.get(j+(i*11)))));
    		}
    		
    	}
    	return polyCoords;
    }

    public static List<Polygon> createPolygons(List<List<Point>> polyCoords)
    // This method will return a list of all polygons present in the 10x10 drone fly zone
    {
    	var polygons = new ArrayList<Polygon>();
    	
    	for(int i = 0; i < 100; i++)
    	{
    		polygons.add(Polygon.fromLngLats(List.of(polyCoords.get(i))));	
    	}

    	return polygons;
    }
    
    public static ArrayList<String> hexCodeConversion(String[] predictions)
    // This function will convert the predictions into the appropriate hex-code colour
    {
    	var colourMap = new ArrayList<String>();
    	
    	for(int i = 0; i < 100; i++)
    	{
    		if (Integer.parseInt(predictions[i]) >= 0 && Integer.parseInt(predictions[i]) < 32)
    		{
    			colourMap.add(i, "#00ff00");
    		}
    		else if (Integer.parseInt(predictions[i]) >= 32 && Integer.parseInt(predictions[i]) < 64)
    		{
    			colourMap.add(i, "#40ff00");
    		}
    		else if (Integer.parseInt(predictions[i]) >= 64 && Integer.parseInt(predictions[i]) < 96)
    		{
    			colourMap.add(i, "#80ff00");
    		}
    		else if (Integer.parseInt(predictions[i]) >= 96 && Integer.parseInt(predictions[i]) < 128)
    		{
    			colourMap.add(i, "#c0ff00");
    		}
    		else if (Integer.parseInt(predictions[i]) >= 128 && Integer.parseInt(predictions[i]) < 160)
    		{
    			colourMap.add(i, "#ffc000");
    		}
    		else if (Integer.parseInt(predictions[i]) >= 160 && Integer.parseInt(predictions[i]) < 192)
    		{
    			colourMap.add(i, "#ff8000");
    		}
    		else if (Integer.parseInt(predictions[i]) >= 192 && Integer.parseInt(predictions[i]) < 224)
    		{
    			colourMap.add(i, "#ff4000");
    		}
    		else if (Integer.parseInt(predictions[i]) >= 224 && Integer.parseInt(predictions[i]) < 256)
    		{
    			colourMap.add(i, "#ff0000");
    		}
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

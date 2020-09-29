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
 */

public class App 
{
	
    public static void main( String[] args ) throws Exception
    {
    	String[] pathname = args;
    	String[] predictions = predictionReader(pathname);
    	List<List<Point>> polyCoords =  gridCoords();
    	List<Polygon> polygons = createPolygons(polyCoords);
    	ArrayList<String> colourMap = hexCodeConversion(predictions);
    	geojsonConvert(polygons, colourMap);
    }
    
    public static String[] predictionReader( String[] args) throws Exception
    //predictionReader takes an pathname and reads the associated file, printing
    //the contents
    {
    	// The file-path is passed as a parameter
        FileReader fr = new FileReader(args[0]); 
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
    // returns a list of four points, each denoting a polygon on the 10x10 grid
    {
    	double lon1 = -3.192473;
    	double lon2 = -3.184319;
    	double lat1 = 55.946233;
    	double lat2 = 55.942617;
    	double lonGridLen = (lon2 - lon1)/10;
    	double latGridLen = (lat2 - lat1)/10;
    	List<Point> coordinates = new ArrayList<>();
    	//List<Point> fourCorners = new ArrayList<>();
    	List<List<Point>> polyCoords = new ArrayList<>();
    	
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
    	List<Polygon> polygons = new ArrayList<>();
    	
    	for(int i = 0; i < 100; i++)
    	{
    		polygons.add(Polygon.fromLngLats(List.of(polyCoords.get(i))));	
    	}

    	return polygons;
    }
    
    public static ArrayList<String> hexCodeConversion(String[] predictions)
    // This function will convert the predictions into the appropriate hex-code colour
    {
    	ArrayList<String> colourMap = new ArrayList<String>();
    	
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
    {
    	List<Feature> featureList = new ArrayList<>();
    	FileWriter jsonFile = new FileWriter("dronezone.json");
    	
    	for(int i = 0; i < 100; i++)
    	{
    		featureList.add(Feature.fromGeometry((Geometry)polygons.get(i)));
    		featureList.get(i).addStringProperty("rgb-string", colourMap.get(i));
    		featureList.get(i).addStringProperty("fill", colourMap.get(i));
    		featureList.get(i).addNumberProperty("fill-opacity", 0.75);
    	}
    	System.out.print("\nFeature[0] = " + featureList.get(0));
    	FeatureCollection featureCol = FeatureCollection.fromFeatures(featureList);
    	
    	jsonFile.write(featureCol.toJson());
    	jsonFile.close();
    }
}

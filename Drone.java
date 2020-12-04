package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;

public class Drone 
/**
     * This class is used to allow the creation of a drone that can, given
     * a list of sensors, find an efficient path between them and navigate
     * no fly zones in the process, all while taking readings from the sensors.
     */
{    
    private int moves;
    private Point position;
    private Point startPosition;
    private int angle;
    private ArrayList<Point> movementHistory;

    public Drone(Point startPosition)
    {
    	this.moves = 0;
    	this.angle = 360;
    	this.position = startPosition;
    	this.startPosition = startPosition;
    }
    
    // Getters
    public int getMoves()
    {
    	return moves;
    }
    
    public Point getPosition()
    {
    	return position;
    }
    
    public int getAngle()
    {
    	return angle;
    }
    
    public Point getStartPosition()
    {
    	return startPosition;
    }
    
    //Setters
    public void setMoves(int newMoves)
    {
    	this.moves = newMoves;
    }
    
    public void setPosition(Point newPosition)
    {
    	this.position = newPosition;
    }
    
    public void setAngle(int newAngle)
    {
    	this.angle = newAngle;
    }

    
    

    
    

}
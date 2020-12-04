package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public class Drone 
/**
 * This class contains all the attributes the drone needs to be legally navigated
 * through the ordered path of sensors.
 */
{    
	// Define attributes
    private int moves;
    private Point position;
    private Point startPosition;
    private int angle;

    // Constructor
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
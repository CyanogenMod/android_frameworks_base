package com.android.internal.policy.impl;

import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

public class StylusGestureFilter extends SimpleOnGestureListener{
    
	 public final static int SWIPE_UP    = 1;
	 public final static int SWIPE_DOWN  = 2;
	 public final static int SWIPE_LEFT  = 3;
	 public final static int SWIPE_RIGHT = 4;
	 
	 	 
	 public final static int MODE_TRANSPARENT = 0;
	 public final static int MODE_SOLID       = 1;
	 public final static int MODE_DYNAMIC     = 2;
	 
	 private final static int ACTION_FAKE = -13; 
	 private int swipe_Min_Distance = 50;
	 private int swipe_Max_Distance = 350;
	 private int swipe_Min_Velocity = 100;
	 
	 private int mode      = MODE_DYNAMIC;
	 private boolean running = true;
	 private boolean tapIndicator = false;
	 private int action;
	 
	 
	 
	 private GestureDetector detector;
	 
	 private static StylusGestureFilter simple=null;
	 
	
	 private StylusGestureFilter() {
	 
	  this.detector = new GestureDetector(this);
	   
	 }
	 public static StylusGestureFilter getFilter(){
		 if(simple == null)
			 simple = new StylusGestureFilter();
		 return simple;
	 }
	 
	 public boolean onTouchEvent(MotionEvent event){
	  
	   if(!this.running)
		   return false;  
	  
	   boolean result = this.detector.onTouchEvent(event); 
	  
	   
	   if(this.mode == MODE_SOLID)
	    event.setAction(MotionEvent.ACTION_CANCEL);
	   else if (this.mode == MODE_DYNAMIC) {
	  
	     if(event.getAction() == ACTION_FAKE) 
	       event.setAction(MotionEvent.ACTION_UP);
	     else if (result)
	       event.setAction(MotionEvent.ACTION_CANCEL); 
	     else if(this.tapIndicator){
	      event.setAction(MotionEvent.ACTION_DOWN);
	      this.tapIndicator = false;
	     }
	  
	   }
	   return result;
	   
	 }
	 
	 public void setMode(int m){
	  this.mode = m;
	 }
	 
	 public int getMode(){
	  return this.mode;
	 }
	 
	 public void setEnabled(boolean status){
	  this.running = status;
	 }
	 
	 public void setSwipeMaxDistance(int distance){
	  this.swipe_Max_Distance = distance;
	 }
	 
	 public void setSwipeMinDistance(int distance){
	  this.swipe_Min_Distance = distance;
	 }
	 
	 public void setSwipeMinVelocity(int distance){
	  this.swipe_Min_Velocity = distance;
	 }
	 
	 public int getSwipeMaxDistance(){
	  return this.swipe_Max_Distance;
	 }
	 
	 public int getSwipeMinDistance(){
	  return this.swipe_Min_Distance;
	 }
	 
	 public int getSwipeMinVelocity(){
	  return this.swipe_Min_Velocity;
	 }
	 
	 public int getAction(){
		 return this.action;
	 }
	 	  
	 @Override
	 public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
	   float velocityY) {

	  final float xDistance = Math.abs(e1.getX() - e2.getX());
	  final float yDistance = Math.abs(e1.getY() - e2.getY());

	  velocityX = Math.abs(velocityX);
	  velocityY = Math.abs(velocityY);
	  boolean result = false;
	     
	  if(velocityX > this.swipe_Min_Velocity && xDistance > this.swipe_Min_Distance){
	   if(e1.getX() > e2.getX() ){ // right to left
	    //Swipe Left
		   System.out.println("Syed Swipe Left");
		   action = SWIPE_LEFT;
	     }
	   else{
	    //Swipe Right
		   System.out.println("Syed Swipe Right");
		   action = SWIPE_RIGHT;
	   }
	   result = true;
	  }
	  else if(velocityY > this.swipe_Min_Velocity && yDistance > this.swipe_Min_Distance){
	   if(e1.getY() > e2.getY()){ // bottom to up
		 //Swipe Up
		   System.out.println("Syed Swipe Up");
		   action = SWIPE_UP;
	   }
	   else{
		 //Swipe Down
		 System.out.println("Syed Swipe Down");
		 action = SWIPE_DOWN;
	   }
	   result = true;
	  }
	  
	 
	   return result;
	 }

	 @Override
	 public boolean onDoubleTap(MotionEvent arg0) {
		System.out.println("Syed double tap");
		 		 
		return false;
	 }

	 public void onLongPress(MotionEvent e) {
		 System.out.println("Syed Long PRess");
		 
     }
	 
	 
	 
	}

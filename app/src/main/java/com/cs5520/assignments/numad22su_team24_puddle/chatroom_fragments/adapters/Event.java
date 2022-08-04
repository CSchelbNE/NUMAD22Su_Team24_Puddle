package com.cs5520.assignments.numad22su_team24_puddle.chatroom_fragments.adapters;

import android.graphics.Bitmap;
import android.location.Location;

public class Event {
    Boolean isOnline;
    String name;
    String startingDatetime;
    String endingDatetime;
    Location location;
    String description;
    String backgroundImgUri;
    int attendanceCount;


    public Event(String name, String startingDatetime, String endingDatetime, Location location,
                 String description, String backgroundImgUri, int attendanceCount){
        this.name = name;
        this.startingDatetime = startingDatetime;
        this.endingDatetime = endingDatetime;
        this.location = location;
        this.description = description;
        this.backgroundImgUri = backgroundImgUri;
        this.attendanceCount = attendanceCount;
    }

    public String getName() {
        return name;
    }

    public String getBackgroundImgUri() {
        return backgroundImgUri;
    }

    public String getEndingDatetime() {
        return endingDatetime;
    }

    public String getStartingDatetime() {
        return startingDatetime;
    }

    public Location getLocation(){
        return location;
    }

    public String getDescription() {
        return description;
    }


    public int getAttendanceCount(){
        return attendanceCount;
    }
}

package com.google.devrel.training.conference.form;

import java.util.Date;

import com.google.devrel.training.conference.domain.Session.SessionType;

/**
 * A simple Java object representing a Session form sent from client. 
 */
public class SessionForm {
    
    private String speaker;

    private Date startTime;
    private String duration;
    private SessionType type;
    private String location;
    
    public String getSpeaker() {
        return speaker;
    }
    
    public Date getStartTime() {
        return startTime;
    }

    public String getDuration() {
        return duration;
    }

    public SessionType getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    private SessionForm() {}
}

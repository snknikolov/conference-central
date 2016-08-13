package com.google.devrel.training.conference.domain;

/**
 *  A simple wrapper for announcement, used by an endpoint function.
 */
public class Announcement {
    
    private String message;
    
    public Announcement(String message) {
        this.message = message;
    }
    
    @Override
    public String toString() {
        return message;
    }
}

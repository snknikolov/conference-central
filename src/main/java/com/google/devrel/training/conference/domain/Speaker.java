package com.google.devrel.training.conference.domain;

/**
 * A simple wrapper for speaker, used by endpoint function.
 */
public class Speaker {
    
    private String speakerName;
    
    public Speaker(String speakerName) {
        this.speakerName = speakerName;
    }
    
    @Override
    public String toString() {
        return speakerName;
    }
}

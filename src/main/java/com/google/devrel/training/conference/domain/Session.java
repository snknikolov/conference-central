package com.google.devrel.training.conference.domain;

import java.util.Date;

import com.google.api.server.spi.config.AnnotationBoolean;
import com.google.api.server.spi.config.ApiResourceProperty;
import com.google.appengine.repackaged.com.google.api.client.util.Preconditions;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Parent;
import com.google.devrel.training.conference.form.SessionForm;

/**
 * A session class stores information about a single session.
 * Each session has a Conference as a parent in the datastore.
 */
@Entity
public class Session {
    
    /**
     * The id for datastore key.
     */
    @Id
    private long id;
    
    /**
     * Holds Conference key as the parent.
     */
    @Parent
    @ApiResourceProperty(ignored = AnnotationBoolean.TRUE)
    private Key<Conference> conferenceKey;
    
    @ApiResourceProperty(ignored = AnnotationBoolean.TRUE)
    private long conferenceId;
    
    @Index private String speaker;
    private Date startTime;
    private String duration;
    @Index private SessionType type;
    private String location;
        
    public Session(final long id, final long conferenceId, final SessionForm form) {
        Preconditions.checkNotNull(form.getSpeaker(), "Speaker is required.");
        this.id = id;
        this.conferenceKey = Key.create(Conference.class, conferenceId);
        this.conferenceId = conferenceId;
        updateWithSessionForm(form);
    }
    
    public String getSpeaker() {
        return speaker;
    }
    
    /**
     * Updates the Session with SessionForm.
     * This method is used upon object creation as well as updating existing Session.
     *
     * @param form contains form data sent from the client.
     */
    public void updateWithSessionForm(SessionForm form) {
        speaker = form.getSpeaker();        
        duration = form.getDuration();
        
        Date startTime = form.getStartTime();
        this.startTime = startTime == null ? null : new Date(startTime.getTime());
        SessionType type = form.getType();
        this.type = type == null ? SessionType.NOT_SPECIFIED : type;
        String location = form.getLocation();
        this.location = location == null || location.length() == 0 ? "DEFAULT LOC" : location;
    }
    
    @ApiResourceProperty(ignored = AnnotationBoolean.TRUE)
    public Key<Conference> getConferenceKey() {
        return conferenceKey;
    }
    
    @ApiResourceProperty(ignored = AnnotationBoolean.TRUE)
    public long getConferenceId() {
        return conferenceId;
    }
    
    public Date startTime() {
        return startTime == null ? null : new Date(startTime.getTime());
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
    
    /**
     * Possible types of a session
     */
    public static enum SessionType {
        WORKSHOP, 
        LECTURE, 
        KEYNOTE,
        NOT_SPECIFIED
    }
    
    @SuppressWarnings("unused")
    private Session() {}

}

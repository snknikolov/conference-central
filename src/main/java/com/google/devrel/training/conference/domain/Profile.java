package com.google.devrel.training.conference.domain;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Cache
@Entity
public class Profile {
	private String displayName;
	private String mainEmail;
	private TeeShirtSize teeShirtSize;
	private List<String> conferencesKeysToAttend = new ArrayList<>(0);
	private List<String> sessionsKeysWishlist = new ArrayList<>(0);

	@Id private String userId;
    
    /**
     * Public constructor for Profile.
     * @param userId The user id, obtained from the email
     * @param displayName Any string user wants us to display him/her on this system.
     * @param mainEmail User's main e-mail address.
     * @param teeShirtSize The User's tee shirt size
     * 
     */
    public Profile(String userId, String displayName, String mainEmail, TeeShirtSize teeShirtSize) {
    	this.userId = userId;
    	this.displayName = displayName;
    	this.mainEmail = mainEmail;
    	this.teeShirtSize = teeShirtSize;
    }
    	
    public void update(String displayName, TeeShirtSize teeShirtSize) {
	    if (displayName != null)
	        this.displayName = displayName;
	    if (teeShirtSize != null)
	        this.teeShirtSize = teeShirtSize;
	}
    
    public List<String> getConferenceKeysToAttend() {
        return ImmutableList.copyOf(conferencesKeysToAttend);
    }
	
    public void addToConferenceKeysToAttend(String key) {
        conferencesKeysToAttend.add(key);
    }
	
    /**
     * Unregister from a conference using key.
     * @param key Conference key
     */
    public void unregisterFromConference(String key) {
        if (conferencesKeysToAttend.contains(key)) {
            conferencesKeysToAttend.remove(key);
        } else {
            throw new IllegalArgumentException("Conference key not found: " + key);
        }
    }
    
    public List<String> getSessionKeysWishlist() {
        return ImmutableList.copyOf(sessionsKeysWishlist);
    }
    
    public void addToSessionKeysWishlist(String key) {
        sessionsKeysWishlist.add(key);
    }
    
    public void deleteSessionInWishlist(String key) {
        if (sessionsKeysWishlist.contains(key)) {
            sessionsKeysWishlist.remove(key);
        } else {
            throw new IllegalArgumentException("Session key not found: " + key);
        }
    }
	
    public String getDisplayName() {
        return displayName;
    }

    public String getMainEmail() {
        return mainEmail;
    }

    public TeeShirtSize getTeeShirtSize() {
        return teeShirtSize;
    }

    public String getUserId() {
        return userId;
    }
	
	@SuppressWarnings("unused")
    private Profile() {}
}

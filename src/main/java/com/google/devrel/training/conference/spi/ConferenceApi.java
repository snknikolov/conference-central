package com.google.devrel.training.conference.spi;

import static com.google.devrel.training.conference.service.OfyService.ofy;
import static com.google.devrel.training.conference.service.OfyService.factory;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.Key;

/**
 * Defines conference APIs.
 */
@Api(name = "conference", 
    version = "v1", 
    scopes = { Constants.EMAIL_SCOPE }, 
    clientIds = { Constants.WEB_CLIENT_ID, Constants.API_EXPLORER_CLIENT_ID }, 
    description = "API for the Conference Central Backend application.")
public class ConferenceApi {

    /**
     * Creates or updates a Profile object associated with the given user
     * object.
     *
     * @param user
     *            A User object injected by the cloud endpoints.
     * @param profileForm
     *            A ProfileForm object sent from the client form.
     * @return Profile object just created.
     * @throws UnauthorizedException
     *             when the User object is null.
     */

    /** Declare this method as a method available externally through Endpoints
     *  The request that invokes this method should provide data that
     *  conforms to the fields defined in ProfileForm
     */
    @ApiMethod(name = "saveProfile", path = "profile", httpMethod = HttpMethod.POST)
    public Profile saveProfile(final User user, ProfileForm form) 
            throws UnauthorizedException {

        if (user == null) {
            throw new UnauthorizedException("Authorization required.");
        }
        
        String userId = user.getUserId();
        String mainEmail = user.getEmail();
        String displayName = form.getDisplayName();
        TeeShirtSize teeShirtSize = form.getTeeShirtSize();
        
        Profile profile = getProfile(user);
        if (profile != null) {
            profile.update(displayName, teeShirtSize);
        } else {
            if (displayName == null) {
                displayName = extractDefaultDisplayNameFromEmail(user.getEmail());
            }
            
            if (teeShirtSize == null) {
                teeShirtSize = TeeShirtSize.NOT_SPECIFIED;
            }
            profile = new Profile(userId, displayName, mainEmail, teeShirtSize);
        }
        ofy().save().entity(profile).now();

        return profile;
    }

    
    /**
     * Returns a Profile object associated with the given user object. The cloud
     * endpoints system automatically inject the User object.
     *
     * @param user
     *            A User object injected by the cloud endpoints.
     * @return Profile object.
     * @throws UnauthorizedException
     *             when the User object is null.
     */
    @ApiMethod(name = "getProfile", path = "profile", httpMethod = HttpMethod.GET)
    public Profile getProfile(final User user) throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        String userId = user.getUserId();
        Key<Profile> key = Key.create(Profile.class, userId);
        Profile profile = ofy().load().key(key).now();
        return profile;
    }
    
    /**
     * 
     * @param user 
     *          The user who creates/updates the conference.
     * @param form 
     *          A ProfileForm object sent from the client form.
     * @return Conference 
     *          The object just created.
     * @throws UnauthorizedException 
     *          If user object is null.
     */
    @ApiMethod(name="createConference", path="conference", httpMethod=HttpMethod.POST)
    public Conference createConference(final User user, final ConferenceForm form)
        throws UnauthorizedException {
        
        if (user == null) {
            throw new UnauthorizedException("Authorization required.");
        }
                
        // Get user's id, entity key and profile.
        String userId = user.getUserId();
        Key<Profile> profileKey = Key.create(Profile.class, userId);
        Profile profile = getProfileFromUser(user);

        // Generate a key and create a new conference entity.
        Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);
        Conference conf = new Conference(conferenceKey.getId(), userId, form);
//        conf.updateWithConferenceForm(form);
        
        ofy().save().entities(conf, profile).now();

        return conf;
    }
    
    /**
     * Get Profile entity from User object.
     * Create one if there is no entity for this user.
     * @param user
     * @return
     */
    private static Profile getProfileFromUser(User user) {
        Key<Profile> key = Key.create(Profile.class, user.getUserId());
        Profile profile = ofy().load().key(key).now();
        
        if (profile == null) {
            String email = user.getEmail();
            profile = new Profile(user.getUserId(),
                                extractDefaultDisplayNameFromEmail(email),
                                email,
                                TeeShirtSize.NOT_SPECIFIED);
        }
        return profile;
    }
    
    /**
     * Get the display name from the user's email. 
     */
    private static String extractDefaultDisplayNameFromEmail(String email) {
        return email == null ? null : email.substring(0, email.indexOf("@"));
    }

}

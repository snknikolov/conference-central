package com.google.devrel.training.conference.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Named;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Query;

import com.google.appengine.api.users.User;

import static com.google.devrel.training.conference.service.OfyService.ofy;
import static com.google.devrel.training.conference.service.OfyService.factory;

import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;

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
        
        ofy().save().entities(conf, profile).now();

        return conf;
    }
    
    @ApiMethod(name="queryConferences", path="queryConferences", httpMethod = HttpMethod.POST)
    public List<Conference> queryConferences(final ConferenceQueryForm queryForm) {
        Query<Conference> query = queryForm.getQuery();
        
        List<Conference> result = new ArrayList<>(0);
        List<Key<Profile>> organisersKeys = new ArrayList<>(0);
        
        for (Conference conference : query) {
            Key<Profile> organiserKey = Key.create(Profile.class, conference.getOrganizerUserId());
            organisersKeys.add(organiserKey);
            result.add(conference);
        }
        
        ofy().load().keys(organisersKeys);
        return result;
    }
    
    @ApiMethod(name="getConferencesCreated", path="getConferencesCreated", httpMethod = HttpMethod.POST)
    public List<Conference> getConferencesCreated(final User user) 
        throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required.");
        }
        String userId = user.getUserId();
        Key<Profile> profileKey = Key.create(Profile.class, userId);
        
        Query<Conference> query = ofy().load()
                                        .type(Conference.class)
                                        .ancestor(profileKey)
                                        .order("name");
        
        return query.list();
    }
    
    public List<Conference> playgroundFilter() {
        return ofy().load()
                    .type(Conference.class)
                    .filter("city =", "Tokyo")
                    .filter("seatsAvailable >", 0)
                    .filter("seatsAvailable <", 10)
                    .order("seatsAvailable")
                    .order("name")
                    .order("date")
                    .list();
    }
    
    /**
     * Register to attend a conference.
     * @param user The user who invokes this method, null when not not signed in.
     * @param websafeConferenceKey String representation of Conference key.
     * 
     * @return Boolean true if registered successfully, false otherwise.
     * 
     * @throws UnauthorizedException When user is not signed in.
     * @throws NotFoundException When there is no conference with this key.
     * @throws ForbiddenException
     * @throws ConflictException When user has already registered or there are no seats left.
     */
    @ApiMethod(name="registerForConference",
            path="conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.POST
            )
    public WrappedBoolean registerForConference(final User user,
            @Named("websafeConferenceKey") final String websafeConferenceKey) 
            throws UnauthorizedException, NotFoundException,
                    ForbiddenException, ConflictException {
        
        if (user == null) {
            throw new UnauthorizedException("Authorization required.");
        }
        
        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
           public WrappedBoolean run() {
               try {
                   Conference conference = getConference(websafeConferenceKey);                   
                   Profile profile = getProfileFromUser(user);

                   if (profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
                       return new WrappedBoolean(false, "Already registered.");
                   } else if (conference.getSeatsAvailable() <= 0) {
                       return new WrappedBoolean(false, "No seats left.");
                   } else {
                       profile.addToConferenceKeysToAttend(websafeConferenceKey);
                       conference.bookSeats(1);
                       ofy().save().entities(profile, conference).now();
                       
                       return new WrappedBoolean(true, "Registration successful.");
                   }
               } catch (Exception e) {
                   return new WrappedBoolean(false, "Unknown exception.");
               }
           }
        });
        
        if (!result.getResult()) {
            if (result.getReason().contains("Conference not found")) {
                throw new NotFoundException(result.getReason());
                
            } else if (result.getReason().equals("Already registered.")) {
                throw new ConflictException("You have already registered.");
                
            } else if (result.getReason().equals("No seats left.")) {
                throw new ConflictException("There are no seats left.");
            } else {
                throw new ForbiddenException("Unknown exception.");
            }
        }
        return result;
    }
    
    @ApiMethod(name="unregisterFromConference",
            path="conference/websafeConferenceKey/registration",
            httpMethod = HttpMethod.DELETE
            )
    public WrappedBoolean unregisterFromConference(final User user,
            @Named("websafeConferenceKey") final String websafeConferenceKey)
        throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {
        
        if (user == null) {
            throw new UnauthorizedException("Authorization required.");
        }
        
        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
            public WrappedBoolean run() {
                try {
                    Conference conference = getConference(websafeConferenceKey);                    
                    Profile profile = getProfileFromUser(user);
                    
                    if (!profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
                        return new WrappedBoolean(false, "Not registered.");
                    } else {
                        profile.unregisterFromConference(websafeConferenceKey);
                        conference.giveBackSeats(1);
                        ofy().save().entities(profile, conference).now();
                        
                        return new WrappedBoolean(true, "Successfully unregistered.");
                    }
                } catch (Exception e) {
                    return new WrappedBoolean(false, "Unknown exception.");
                }
            }
        });
        
        if (!result.getResult()) {
            if (result.getReason().contains("Conference not found")) {
                throw new NotFoundException(result.getReason());
            } else if (result.getReason().equals("Not registered.")) {
                throw new ConflictException("You are not registered.");
            } else {
                throw new ForbiddenException("Unknown exception.");
            }
        }

        return result;
    }
 
    
    /**
     * Get conferences, which user will attend.
     * @param user The user who invokes this method, null when not not signed in.
     * @return A Collection of Conferences which user is registered for.
     * @throws UnauthorizedException When user is not signed in.
     * @throws NotFoundException When there is no user with this profile.
     */
    @ApiMethod(name="getConferencesToAttend", 
            path="getConferencesToAttend", 
            httpMethod = HttpMethod.GET)
    public Collection<Conference> getConferencesToAttend(final User user) 
            throws UnauthorizedException, NotFoundException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required.");
        }
        
        Profile profile = getProfileFromUser(user);
        if (profile == null) {
            throw new NotFoundException("Profile doesn't exist.");
        }
        
        List<String> keyStringsToAttend = profile.getConferenceKeysToAttend();
        List<Key<Conference>> keysToAttend = new ArrayList<>();
        for (String keyString : keyStringsToAttend) {
            Key<Conference> key = Key.create(keyString);
            keysToAttend.add(key);
        }
        
        Collection<Conference> conferences = ofy().load().keys(keysToAttend).values();
        return conferences;
    }
    
    /**
     * Return a Conference object with given key.
     * @param websafeConferenceKey Conference's key.
     * @return
     * @throws NotFoundException If there is no conference with the given key.
     */
    @ApiMethod(name="getConference", 
            path="conference/{websafeConferenceKey}", 
            httpMethod = HttpMethod.GET
            )
    public Conference getConference(
            @Named("websafeConferenceKey") final String websafeConferenceKey) 
            throws NotFoundException {
        Key<Conference> key = Key.create(websafeConferenceKey);
        Conference conference = ofy().load().key(key).now();
        if (conference == null) {
            throw new NotFoundException("No conference found with key: " + websafeConferenceKey);
        }
        return conference;
    }
        
    /**
     * A wrapper for Boolean.
     */
    public static class WrappedBoolean {
        private final Boolean result;
        private final String reason;
        
        public WrappedBoolean(Boolean result) {
            this.result = result;
            this.reason = "";
        }
        
        public WrappedBoolean(Boolean result, String reason) {
            this.result = result;
            this.reason = reason;
        }
        
        public Boolean getResult() {
            return result;
        }
        
        public String getReason() {
            return reason;
        }
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

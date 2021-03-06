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
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.users.User;

import static com.google.devrel.training.conference.service.OfyService.ofy;
import static com.google.devrel.training.conference.service.OfyService.factory;

import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Announcement;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.domain.Session;
import com.google.devrel.training.conference.domain.Session.SessionType;
import com.google.devrel.training.conference.domain.Speaker;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.google.devrel.training.conference.form.SessionForm;

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
     *
     *  Declare this method as a method available externally through Endpoints
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
        final String userId = user.getUserId();
        Key<Profile> profileKey = Key.create(Profile.class, userId);
        final Profile profile = getProfileFromUser(user);
        final Queue queue = QueueFactory.getDefaultQueue();
        final Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);
                
        Conference conf = ofy().transact(new Work<Conference>() {
            public Conference run() {
                // Generate a key and create a new conference entity.
                Conference conf = new Conference(conferenceKey.getId(), userId, form);
                ofy().save().entities(conf, profile).now();

                // Add send confirmation email task to push queue.
                queue.add(ofy().getTransaction(), TaskOptions.Builder
                                     .withUrl("/tasks/send_confirmation_email")
                                     .param("email", profile.getMainEmail())
                                     .param("conferenceInfo", conf.toString()));

                return conf;
            }
        });
        return conf;
    }
    
    /**
     * Get a list of all conferences created.
     * @param queryForm
     * @return
     */
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
    
    /**
     * Get conferences created by a user.
     * @param user The user who invokes this method, null when not not signed in.
     * @return List of Conference objects created by user.
     * @throws UnauthorizedException If user is null
     */
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
    
    /**
     * Unregister from a conference.
     * @param user The user who invokes this method, null when not not signed in.
     * @param websafeConferenceKey String representation of Conference key.
     * 
     * @return Boolean true if unregistered successfully, false otherwise.
     * 
     * @throws UnauthorizedException When user is not signed in.
     * @throws NotFoundException When there is no conference with this key.
     * @throws ForbiddenException
     * @throws ConflictException When user is not registered for conference with this key.
     */
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
     * Create a session for a Conference. Only the user who created the conference
     * can add sessions to it.
     * @param sessionForm A SessionForm object sent from the client form.
     * @param websafeConferenceKey String representation of Conference key.
     * @return The object just created.
     * @throws UnauthorizedException When user is not signed in or is not the original Conference creator.
     * @throws NotFoundException When no Conference with this key is found.
     */
    @ApiMethod(name="createSession", path="session/new", httpMethod = HttpMethod.POST)
    public Session createSession(final User user,
            final SessionForm sessionForm, 
            @Named("websafeConferenceKey") final String websafeConferenceKey) 
            throws UnauthorizedException, NotFoundException {
        final Conference conference = getConference(websafeConferenceKey);
        if (user == null || !user.getUserId().equals(conference.getOrganizerUserId())) {
            throw new UnauthorizedException("Authorization required.");
        }
        
        final long conferenceId = conference.getId();
        final Key<Conference> conferenceKey = Key.create(Conference.class, conferenceId);
        final Key<Session> sessionKey = factory().allocateId(conferenceKey, Session.class);
        final Queue queue = QueueFactory.getDefaultQueue();

        Session session = ofy().transact(new Work<Session>() {
            public Session run() {
                Session session = new Session(sessionKey.getId(), conferenceId, sessionForm);
                ofy().save().entities(conference, session).now();
                
                // Add get featured speakers to push queue.
                queue.add(ofy().getTransaction(), TaskOptions.Builder
                                    .withUrl("/tasks/set_featured_speaker")
                                    .param("speaker", session.getSpeaker()));
                return session;
            }
        });        
        return session;
    }
        
    /**
     * Return all sessions for a conference with given key.
     * @param websafeConferenceKey String representation of Conference key.
     * @return List of all sessions for a Conference with this key.
     * @throws NotFoundException If there is no conference with the given key.
     */
    @ApiMethod(name="getConferenceSessions",
            path="getConferenceSessions",
            httpMethod = HttpMethod.POST)
    public List<Session> getConferenceSessions(
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws NotFoundException {
        // TODO Fix conference query by key.
        Key<Conference> key = Key.create(websafeConferenceKey);
        Conference c = getConference(websafeConferenceKey);
        Key<Conference> cKey = Key.create(Conference.class, c.getId());

        if (ofy().load().key(key).now() == null) {
            throw new NotFoundException("No conference found with key: " + key.toString());
        }
        
        Query<Session> query = ofy().load()
                                    .type(Session.class)
                                    .ancestor(cKey)
                                    .order("speaker");

        return query.list();
    }
        
    /**
     * Filter conference sessions by type. The method calls getConferenceSessions.
     * @param websafeConferenceKey String representation of Conference key.
     * @param typeOfSession The type of session to query for.
     * @return
     * @throws NotFoundException When there is no Conference with this key.
     */
    @ApiMethod(name="getConferenceSessionsByType",
            path="byType",
            httpMethod = HttpMethod.POST)
    public List<Session> getConferenceSessionsByType(
            @Named("websafeConferenceKey") final String websafeConferenceKey, 
            @Named("sessionType") final SessionType typeOfSession) throws NotFoundException {
        List<Session> allSessions = getConferenceSessions(websafeConferenceKey);
        List<Session> filteredSessions = new ArrayList<>();
        for (Session session : allSessions) {
            if (session.getType() == typeOfSession) {
                filteredSessions.add(session);
            }
        }
        return filteredSessions;
    }
    
    /**
     * Get all sessions by speaker.
     * @param speaker Speaker's name as String.
     * @return List of all sessions by speaker. Empty list if no sessions by speaker.
     */
    @ApiMethod(name="getSessionsBySpeaker",
            path="getSessionsBySpeaker/{speaker}",
            httpMethod = HttpMethod.POST)
    public List<Session> getSessionsBySpeaker(@Named("speaker") final String speaker) {
        Query<Session> query = ofy().load()
                                    .type(Session.class)
                                    .filter("speaker =", speaker);
        return query.list();
    }
        
    /**
     * Add a session to user's wish list.
     * @param user The user who invokes this method, null when not signed in.
     * @param websafeSessionKey String representation of Session key.
     * 
     * @return WrappedBoolean true if added successfully, false otherwise.
     * 
     * @throws UnauthorizedException When user is not signed in.
     * @throws NotFoundException When there is no session with this key.
     * @throws ForbiddenException
     * @throws ConflictException When user has already added session to wish list.
     */
    @ApiMethod(name="addSessionToWishlist", 
            path="session/{websafeSessionKey}/wishlist",
            httpMethod = HttpMethod.PUT)
    public WrappedBoolean addSessionToWishList(final User user, 
            @Named("websafeSessionKey") final String websafeSessionKey) 
            throws UnauthorizedException, NotFoundException, 
                ForbiddenException, ConflictException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required.");
        }
        
        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
           public WrappedBoolean run()  {
               try {
                   Profile profile = getProfileFromUser(user);
                   // Check if session with key exists. Throws NotFoundException if it doesn't.
                   Session session = getSession(websafeSessionKey);
                   
                   if (profile.getSessionKeysWishlist().contains(websafeSessionKey)) {
                       return new WrappedBoolean(false, "Session already in wishlist.");
                   } else {
                       profile.addToSessionKeysWishlist(websafeSessionKey);
                       ofy().save().entity(profile).now();
                       return new WrappedBoolean(true, "Successfully added to wishlist.");
                   }
               } catch (NotFoundException nfe) {
                   return new WrappedBoolean(false, "No session found with key: " + websafeSessionKey);
               } catch (Exception e) {
                   return new WrappedBoolean(false, "Unknown exception.");
               }
           }
        });
        
        if (!result.getResult()) {
            if (result.getReason().equals("Session already in wishlist.")) {
                throw new ConflictException("You have already added this session to wishlist.");
            } else if (result.getReason().startsWith("No session found with key")) {
                throw new NotFoundException(result.getReason());
            } else {
                throw new ForbiddenException("Uknown exception.");
            }
        }
        return result;
    }
    
    /**
     * Delete a session from user's wishlist.
     * @param user The user who invokes this method. Null when not signed in.
     * @param websafeSessionKey String representation of Session key.
     * 
     * @return WrappedBoolean true if deleted successfully, false otherwise.
     * 
     * @throws UnauthorizedException When user is not signed in.
     * @throws NotFoundException When there is no session with this key.
     * @throws ForbiddenException
     * @throws ConflictException When user has no session with this key in their wish list.
     */
    @ApiMethod(name="deleteSessionInWishlist",
            path="session/{websafeSessionKey}/wishlist",
            httpMethod = HttpMethod.DELETE)
    public WrappedBoolean deleteSessionInWishlist(final User user,
            @Named("websafeSessionKey") final String websafeSessionKey) 
            throws UnauthorizedException, NotFoundException, 
                ForbiddenException, ConflictException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required.");
        }
        
        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
            public WrappedBoolean run() {
                try {
                    Profile profile = getProfileFromUser(user);
                    // Check if session with key exists. Throws NotFoundException if it doesn't.
                    Session session = getSession(websafeSessionKey);
                    
                    if (!profile.getSessionKeysWishlist().contains(websafeSessionKey)) {
                        return new WrappedBoolean(false, "Session not in wishlist.");
                    } else {
                        profile.deleteSessionInWishlist(websafeSessionKey);
                        ofy().save().entity(profile).now();
                        return new WrappedBoolean(true, "Successfully removed from wishlist.");
                    }
                } catch (NotFoundException nfe) {
                    return new WrappedBoolean(false, "No session with key: " + websafeSessionKey);
                } catch (Exception e) {
                    return new WrappedBoolean(false, "Unknown exception.");
                }
            }
        });
        
        if (!result.getResult()) {
            if (result.getReason().equals("Session not in wishlist.")) {
                throw new ConflictException(result.getReason());
            } else if (result.getReason().startsWith("No session with key")) {
                throw new NotFoundException("Session not found.");
            } else {
                throw new ForbiddenException("Unknown exception.");
            }
        }
        return result;
    }
    
    /**
     * Get all sessions from user's wishlist.
     * @param user The user who invokes this method, null when not signed in.
     * @return A Collection of sessions that contains user's wishlist sessions.
     * @throws UnauthorizedException When user is not signed in.
     * @throws NotFoundException When there is no user with this profile.
     */
    @ApiMethod(name="getSessionsInWishlist",
            path="getSessionsInWishlist",
            httpMethod = HttpMethod.GET)
    public Collection<Session> getSessionsInWishlist(final User user) 
            throws UnauthorizedException, NotFoundException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required.");
        }
        Profile profile = getProfileFromUser(user);
        if (profile == null) {
            throw new NotFoundException("Profile doesn't exist.");
        }
        
        List<String> sessionsKeysWishlist = profile.getSessionKeysWishlist();
        List<Key<Session>> sessionKeys = new ArrayList<>();
        
        for (String keyString : sessionsKeysWishlist) {
            Key<Session> key = Key.create(keyString);
            sessionKeys.add(key);
        }
        
        Collection<Session> sessions = ofy().load().keys(sessionKeys).values();
        return sessions;
    }
    
    /**
     * Get announcements from memcache (if any).
     * @return Announcement object if there are announcements, null otherwise.
     */
    @ApiMethod(name="getAnnouncement", path="announcement", httpMethod=HttpMethod.GET)
    public Announcement getAnnouncement() {
        MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
        Object announcement = memcacheService.get(Constants.MEMCACHE_ANNOUNCEMENTS_KEY);
        
        return announcement == null ? 
                null : new Announcement(announcement.toString());
    }
    
    /**
     * Get featured speaker from memcache (if any).
     * A featured speaker is a speaker who has more than one session for a given conference.
     */
    @ApiMethod(name="getFeaturedSpeaker", path="featuredSpeaker", httpMethod=HttpMethod.GET)
    public Speaker getSpeaker() {
        MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
        Object speaker = memcacheService.get(Constants.MEMCACHE_FEATURED_SPEAKER_KEY);
        return speaker == null ? null : new Speaker(speaker.toString());
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
    
    private static Session getSession(String websafeSessionKey) 
        throws NotFoundException {
        Key<Session> key = Key.create(websafeSessionKey);
        Session session = ofy().load().key(key).now();
        if (session == null) {
            throw new NotFoundException("No conference found with key: " + websafeSessionKey);
        }
        return session;
    }

}

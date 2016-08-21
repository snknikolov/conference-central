package com.google.devrel.training.conference.domain;

import static com.google.devrel.training.conference.domain.Session.SessionType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.devrel.training.conference.form.SessionForm;
import com.google.devrel.training.conference.domain.Session;

/**
 * Tests for Session POJO.
 */
public class SessionTest {
    
    private static final long ID = 1234567L;
    
    private static final long CONFERENCE_ID = 987654321L;
    
    private static final String SPEAKER = "Test Speaker";
    
    private static final String DURATION = "2h";
    
    private static final SessionType SESSION_TYPE = SessionType.LECTURE;
    
    private static final String LOCATION = "San Francisco";
    
    private Date startTime;
    
    private SessionForm sessionForm;
    
    private final LocalServiceTestHelper helper = 
            new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig()
                    .setDefaultHighRepJobPolicyUnappliedJobPercentage(100));
    
    @Before
    public void setUp() throws Exception {
        helper.setUp();
        DateFormat dateFormat = new SimpleDateFormat("h:mm a");
        startTime = dateFormat.parse("9:00 PM");
        sessionForm = new SessionForm(SPEAKER, startTime, DURATION, SESSION_TYPE, LOCATION);
    }
    
    @After
    public void tearDown() throws Exception {
        helper.tearDown();
    }
    
    @Test(expected = NullPointerException.class)
    public void testNullSpeaker() throws Exception {
        SessionForm nullSessionForm = 
                new SessionForm(null, startTime, DURATION, SESSION_TYPE, LOCATION);
        new Session(ID, CONFERENCE_ID, nullSessionForm);
    }
    
    @Test
    public void testSession() throws Exception {
        Session session = new Session(ID, CONFERENCE_ID, sessionForm);
        assertEquals(CONFERENCE_ID, session.getConferenceId());
        assertEquals(startTime, session.startTime());
        assertEquals(DURATION, session.getDuration());
        assertEquals(SESSION_TYPE, session.getType());
        assertEquals(LOCATION, session.getLocation());
        // Test if date is defensive copy.
        assertNotSame(startTime, session.startTime());
    }

}

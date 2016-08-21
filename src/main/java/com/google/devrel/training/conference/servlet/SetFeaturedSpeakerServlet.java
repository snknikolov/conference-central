package com.google.devrel.training.conference.servlet;

import static com.google.devrel.training.conference.service.OfyService.ofy;
import static com.google.devrel.training.conference.Constants.MEMCACHE_FEATURED_SPEAKER_KEY;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.devrel.training.conference.domain.Session;

/**
 * A servlet for setting featured speaker in memcache.
 * Featured speaker is a speaker who has a number of sessions >= SESSIONS_THRESHOLD.
 */
@SuppressWarnings("serial")
public class SetFeaturedSpeakerServlet extends HttpServlet {
    
    private static final int SESSIONS_THRESHOLD_FEATURED_SPEAKER = 2;
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException, IOException {
        String speaker = request.getParameter("speaker");
        List<Session> sessions = ofy().load()
                                      .type(Session.class)
                                      .filter("speaker =", speaker)
                                      .list();
        
        if (sessions.size() >= SESSIONS_THRESHOLD_FEATURED_SPEAKER) {
            StringBuilder sb = new StringBuilder();
            sb.append("Featured speaker " + speaker + " has the following sessions:\n");
            for (Session s : sessions) {
                sb.append(s.getType().toString() + " ");
            }
            
            MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
            memcacheService.put(MEMCACHE_FEATURED_SPEAKER_KEY, sb.toString());

        }
        response.setStatus(204);
    }
}

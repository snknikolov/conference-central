package com.google.devrel.training.conference.servlet;

import static com.google.devrel.training.conference.service.OfyService.ofy;
import static com.google.devrel.training.conference.Constants.MEMCACHE_ANNOUNCEMENTS_KEY;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.devrel.training.conference.domain.Conference;

/**
 * A servlet for putting announcement String in memcache.
 */
@SuppressWarnings("serial")
public class SetAnnnouncementServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        
        List<Conference> conferences = ofy().load()
                                            .type(Conference.class)
                                            .filter("seatsAvailable <", 5)
                                            .filter("seatsAvailable >", 0)
                                            .order("seatsAvailable")
                                            .list();
        // If there are nearly sold-out conferences, put an announcement in memcache.
        if (conferences.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("The following conferences are nearly sold out: ");
            for (Conference conf : conferences) {
                sb.append(conf.getName() + " ");
            }
            
            MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
            memcacheService.put(MEMCACHE_ANNOUNCEMENTS_KEY, sb.toString());
        }
        
        response.setStatus(204);
    }
}

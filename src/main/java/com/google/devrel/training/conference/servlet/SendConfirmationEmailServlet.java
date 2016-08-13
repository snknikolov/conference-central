package com.google.devrel.training.conference.servlet;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;


import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.utils.SystemProperty;

/**
 *  Servlet for sending confirmation email when user creates a conference.
 */
@SuppressWarnings("serial")
public class SendConfirmationEmailServlet extends HttpServlet {
    
    private static final Logger LOG = 
            Logger.getLogger(SendConfirmationEmailServlet.class.getName());
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        String email = request.getParameter("email");
        String conferenceInfo = request.getParameter("conferenceInfo");
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties, null);
        String body = "You have created the following conference:\n" + conferenceInfo;
        
        try {
            Message message = new MimeMessage(session);
            InternetAddress from = new InternetAddress(
                    String.format("noreply@%s.appspotmail.com", SystemProperty.applicationId.get(), 
                            "Conference Central")
                    );
            message.setFrom(from);
            message.addRecipient(Message.RecipientType.TO, 
                    new InternetAddress(email, ""));
            message.setSubject("You created a new conference");
            message.setText(body);
            LOG.log(Level.INFO, String.format("Email: %s\nBody: %s", email, body));
            System.out.println(String.format("Email: %s\nBody: %s", email, body));
            Transport.send(message);
        } catch (MessagingException me) {
            LOG.log(Level.WARNING, String.format("Failed to send an mail to %s", email), me);
            throw new RuntimeException(me);
        }
    }
}

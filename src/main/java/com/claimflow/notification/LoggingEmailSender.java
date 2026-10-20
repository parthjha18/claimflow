package com.claimflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger("claimflow.email");

    @Override
    public void send(String to, String subject, String body) {
        log.info("EMAIL to={} subject=\"{}\" body=\"{}\"", to, subject, body);
    }
}

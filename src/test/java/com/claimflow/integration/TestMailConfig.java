package com.claimflow.integration;

import com.claimflow.notification.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Replaces the logging mail stub with one that remembers what was "sent", so tests can assert on it. */
@TestConfiguration
public class TestMailConfig {

    public record Mail(String to, String subject, String body) {
    }

    public static final List<Mail> SENT = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    EmailSender recordingEmailSender() {
        return (to, subject, body) -> SENT.add(new Mail(to, subject, body));
    }
}

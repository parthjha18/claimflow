package com.claimflow.notification;

/** Outbound mail port. The shipped implementation only logs; swap in SES/SMTP without touching callers. */
public interface EmailSender {

    void send(String to, String subject, String body);
}

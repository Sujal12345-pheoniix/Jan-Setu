package com.jansetu.integration.email;

/** Outbound e-mail. Business modules depend on this port, never on a provider SDK. */
public interface EmailDeliveryPort {

    record EmailMessage(String to, String subject, String body) {
    }

    void send(EmailMessage message);
}

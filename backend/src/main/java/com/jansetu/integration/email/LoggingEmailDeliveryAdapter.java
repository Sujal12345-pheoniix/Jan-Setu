package com.jansetu.integration.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Development e-mail adapter: writes messages to the log instead of sending them. The body (which may contain a
 * one-time password-reset link) is logged only when {@code jansetu.mail.log-body=true}, which is enabled solely in
 * the local profile. A real provider adapter is planned with the notifications phase.
 */
@Component
public class LoggingEmailDeliveryAdapter implements EmailDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailDeliveryAdapter.class);
    private final boolean logBody;

    public LoggingEmailDeliveryAdapter(@Value("${jansetu.mail.log-body:false}") boolean logBody) {
        this.logBody = logBody;
    }

    @Override
    public void send(EmailMessage message) {
        if (logBody) {
            log.info("[DEV ONLY - email not sent] to={} subject=\"{}\"\n{}", message.to(), message.subject(), message.body());
        } else {
            log.info("Email queued (logging adapter) to={} subject=\"{}\"", mask(message.to()), message.subject());
        }
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}

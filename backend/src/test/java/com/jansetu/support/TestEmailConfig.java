package com.jansetu.support;

import com.jansetu.integration.email.EmailDeliveryPort;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Captures outgoing e-mails so tests can read password-reset links. */
@TestConfiguration
public class TestEmailConfig {

    @Bean
    @Primary
    RecordingEmailDeliveryPort recordingEmailDeliveryPort() {
        return new RecordingEmailDeliveryPort();
    }

    public static class RecordingEmailDeliveryPort implements EmailDeliveryPort {

        private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_%-]+)");
        private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            sent.add(message);
        }

        public List<EmailMessage> sentTo(String to) {
            return sent.stream().filter(m -> m.to().equalsIgnoreCase(to)).toList();
        }

        public Optional<String> lastResetTokenFor(String to) {
            List<EmailMessage> messages = sentTo(to);
            if (messages.isEmpty()) {
                return Optional.empty();
            }
            Matcher matcher = TOKEN.matcher(messages.getLast().body());
            return matcher.find() ? Optional.of(java.net.URLDecoder.decode(matcher.group(1),
                    java.nio.charset.StandardCharsets.UTF_8)) : Optional.empty();
        }
    }
}

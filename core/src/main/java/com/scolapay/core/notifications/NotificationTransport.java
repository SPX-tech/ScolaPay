package com.scolapay.core.notifications;

import java.time.Duration;

/** Un transport accepte un corps figé. Aucun appel ne doit conserver de transaction SQL ouverte. */
public interface NotificationTransport {
    record Message(String idempotencyKey, String from, String recipient, String subject, String text) {
        @Override public String toString() { return "NotificationMessage[redacted]"; }
    }

    enum Outcome { SUBMITTED, RETRY, FAILED }
    record Result(Outcome outcome, String providerId, String failureCode) {
        public static Result submitted(String id) { return new Result(Outcome.SUBMITTED, id, null); }
        public static Result retry(String code) { return new Result(Outcome.RETRY, null, code); }
        public static Result failed(String code) { return new Result(Outcome.FAILED, null, code); }
    }

    String name();
    boolean supports(Notifications.Channel channel);
    Duration idempotencyWindow();
    Result send(Message message);
}

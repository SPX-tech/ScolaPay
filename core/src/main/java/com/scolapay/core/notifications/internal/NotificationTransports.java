package com.scolapay.core.notifications.internal;

import com.scolapay.core.notifications.NotificationTransport;
import com.scolapay.core.notifications.Notifications.Channel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class NotificationTransports {
    private final Map<String, NotificationTransport> transports;

    NotificationTransports(List<NotificationTransport> candidates) {
        for (NotificationTransport transport : candidates) {
            if (!transport.name().matches("[a-z0-9-]{1,32}")
                    || (!transport.idempotencyWindow().isZero() && transport.idempotencyWindow().compareTo(Duration.ofMinutes(5)) < 0)
                    || transport.idempotencyWindow().compareTo(Duration.ofHours(23)) > 0) {
                throw new IllegalStateException("NOTIFICATION_TRANSPORT_CONTRACT_INVALID");
            }
        }
        transports = candidates.stream().collect(Collectors.toUnmodifiableMap(NotificationTransport::name, Function.identity()));
    }

    Optional<NotificationTransport> forChannel(Channel channel) {
        List<NotificationTransport> matching = transports.values().stream().filter(t -> t.supports(channel)).toList();
        if (matching.size() > 1) throw new IllegalStateException("NOTIFICATION_TRANSPORT_AMBIGUOUS");
        return matching.stream().findFirst();
    }

    Optional<NotificationTransport> named(String name) { return Optional.ofNullable(transports.get(name)); }
}

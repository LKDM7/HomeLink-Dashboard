package fr.lkdm.homelink.dashboard.client.state;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Bounded event presentation captured on receipt; source names survive later page changes. */
public record AlertView(long id, UUID sourceId, String sourceName, String type, Instant timestamp,
                        String severity, String message, Map<String, String> data) {
    public AlertView { data = Map.copyOf(data); }
}

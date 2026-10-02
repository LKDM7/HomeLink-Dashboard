package fr.lkdm.homelink.dashboard.client.state;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Bounded session event presentation; source names and systems survive later page changes. */
public record AlertView(long id, UUID sourceId, String sourceName, String type, Instant timestamp,
                        String severity, String message, Map<String, String> data,
                        MachineSystems.Group system, boolean read, boolean acknowledged) {
    public AlertView { data = Map.copyOf(data); }

    AlertView withState(boolean read, boolean acknowledged) {
        return new AlertView(id, sourceId, sourceName, type, timestamp, severity, message, data, system, read, acknowledged);
    }
}

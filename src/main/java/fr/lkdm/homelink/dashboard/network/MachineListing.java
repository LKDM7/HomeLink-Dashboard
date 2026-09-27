package fr.lkdm.homelink.dashboard.network;

import fr.lkdm.homecore.api.action.ActionResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Machines inside a network's radio area, as shown to one player. Built by the server; the client only displays it.
 * @param code SUCCESS, or why the list is unavailable
 * @param entries at most {@link #MAX_ENTRIES} machines, addable ones first
 * @param total every machine in range, including those beyond the entries sent
 */
public record MachineListing(ActionResult.Code code, List<Entry> entries, int total) {
    public static final int MAX_ENTRIES = 128;
    public static final int MAX_NAME_LENGTH = 128;

    /** Binding of a machine relative to the access point's network. */
    public enum State { FREE, OTHER_NETWORK, IN_NETWORK, UNSUPPORTED }

    /** One machine; {@code networkName} stays empty unless the player may view that other network. */
    public record Entry(UUID id, String name, String type, State state, String networkName, int distance, boolean canAdd) {
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(state);
            name = clean(name);
            type = clean(type);
            networkName = clean(networkName);
            distance = Math.max(0, distance);
        }
    }

    public MachineListing {
        Objects.requireNonNull(code);
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES || total < entries.size()) throw new IllegalArgumentException("Invalid machine listing");
    }

    public static MachineListing of(ActionResult.Code code) { return new MachineListing(code, List.of(), 0); }

    /** Bounded, without formatting codes or control characters. */
    public static String clean(String text) {
        if (text == null) return "";
        var builder = new StringBuilder();
        for (int index = 0; index < text.length() && builder.length() < MAX_NAME_LENGTH; index++) {
            char character = text.charAt(index);
            if (character == '§') { index++; continue; }
            if (!Character.isISOControl(character)) builder.append(character);
        }
        if (!builder.isEmpty() && Character.isHighSurrogate(builder.charAt(builder.length() - 1))) builder.setLength(builder.length() - 1);
        return builder.toString();
    }
}

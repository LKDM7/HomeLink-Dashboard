package fr.lkdm.homelink.dashboard.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Bounded menu-opening data, not a second device transport. On a server setup screen it also carries a
 * name suggestion that is unique among the player's networks and those names, so the client can warn about duplicates.
 */
public record AccessPointSession(BlockPos position, Optional<UUID> networkId,
        List<NetworkChoice> choices, int directoryOffset, boolean hasNext, boolean canCreate,
        String suggestedName, List<String> takenNames, Display display, boolean canShare) {
    public static final int PAGE_SIZE = 16;
    public static final int MAX_TAKEN_NAMES = 256;
    public record NetworkChoice(UUID id, String name, boolean inRange) { }
    /** Whether the menu was opened from a wall display, and whether that display shows its owner's Home to everyone. */
    public enum Display { NONE, PRIVATE, SHARED }

    public AccessPointSession {
        position = position.immutable();
        choices = List.copyOf(choices);
        suggestedName = suggestedName == null ? "" : suggestedName;
        takenNames = List.copyOf(takenNames);
        display = display == null ? Display.NONE : display;
        if (choices.size() > PAGE_SIZE || directoryOffset < 0 || takenNames.size() > MAX_TAKEN_NAMES)
            throw new IllegalArgumentException("Invalid directory page");
    }

    public AccessPointSession(BlockPos position, Optional<UUID> networkId, List<NetworkChoice> choices,
            int directoryOffset, boolean hasNext, boolean canCreate) {
        this(position, networkId, choices, directoryOffset, hasNext, canCreate, "", List.of());
    }

    public AccessPointSession(BlockPos position, Optional<UUID> networkId, List<NetworkChoice> choices, int directoryOffset,
            boolean hasNext, boolean canCreate, String suggestedName, List<String> takenNames) {
        this(position, networkId, choices, directoryOffset, hasNext, canCreate, suggestedName, takenNames, Display.NONE, false);
    }

    /** @return this session with the display state of the access point it was opened from */
    public AccessPointSession withDisplay(Display value, boolean share) {
        return new AccessPointSession(position, networkId, choices, directoryOffset, hasNext, canCreate, suggestedName, takenNames, value, share);
    }

    /** Case- and surrounding-space-insensitive, matching how players read network names. */
    public boolean isTaken(String name) {
        String key = key(name);
        return takenNames.stream().anyMatch(taken -> key(taken).equals(key));
    }

    public static String key(String name) { return name == null ? "" : name.strip().toLowerCase(Locale.ROOT); }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        buffer.writeBoolean(networkId.isPresent());
        networkId.ifPresent(buffer::writeUUID);
        buffer.writeVarInt(directoryOffset);
        buffer.writeBoolean(hasNext);
        buffer.writeBoolean(canCreate);
        buffer.writeVarInt(choices.size());
        choices.forEach(choice -> { buffer.writeUUID(choice.id()); buffer.writeUtf(choice.name(), 128); buffer.writeBoolean(choice.inRange()); });
        buffer.writeUtf(suggestedName, 128);
        buffer.writeVarInt(takenNames.size());
        takenNames.forEach(name -> buffer.writeUtf(name, 128));
        buffer.writeEnum(display);
        buffer.writeBoolean(canShare);
    }

    public static AccessPointSession read(RegistryFriendlyByteBuf buffer) {
        var position = buffer.readBlockPos();
        var network = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.<UUID>empty();
        int offset = buffer.readVarInt();
        boolean next = buffer.readBoolean();
        boolean create = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("Invalid network directory size");
        var choices = new ArrayList<NetworkChoice>(count);
        for (int i = 0; i < count; i++) choices.add(new NetworkChoice(buffer.readUUID(), buffer.readUtf(128), buffer.readBoolean()));
        String suggested = buffer.readUtf(128);
        int taken = buffer.readVarInt();
        if (taken < 0 || taken > MAX_TAKEN_NAMES) throw new IllegalArgumentException("Invalid network name list");
        var names = new ArrayList<String>(taken);
        for (int i = 0; i < taken; i++) names.add(buffer.readUtf(128));
        return new AccessPointSession(position, network, choices, offset, next, create, suggested, names,
                buffer.readEnum(Display.class), buffer.readBoolean());
    }
}

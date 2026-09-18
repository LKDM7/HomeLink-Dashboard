package fr.lkdm.homelink.dashboard.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Bounded menu-opening data, not a second device transport. */
public record AccessPointSession(BlockPos position, Optional<UUID> networkId,
        List<NetworkChoice> choices, int directoryOffset, boolean hasNext, boolean canCreate) {
    public static final int PAGE_SIZE = 16;
    public record NetworkChoice(UUID id, String name) { }

    public AccessPointSession {
        position = position.immutable();
        choices = List.copyOf(choices);
        if (choices.size() > PAGE_SIZE || directoryOffset < 0) throw new IllegalArgumentException("Invalid directory page");
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        buffer.writeBoolean(networkId.isPresent());
        networkId.ifPresent(buffer::writeUUID);
        buffer.writeVarInt(directoryOffset);
        buffer.writeBoolean(hasNext);
        buffer.writeBoolean(canCreate);
        buffer.writeVarInt(choices.size());
        choices.forEach(choice -> { buffer.writeUUID(choice.id()); buffer.writeUtf(choice.name(), 128); });
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
        for (int i = 0; i < count; i++) choices.add(new NetworkChoice(buffer.readUUID(), buffer.readUtf(128)));
        return new AccessPointSession(position, network, choices, offset, next, create);
    }
}

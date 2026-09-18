package fr.lkdm.homelink.dashboard.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers physical networks even while every transmitter is unloaded or removed. */
public final class RadioNetworksSavedData extends SavedData {
    private static final Factory<RadioNetworksSavedData> FACTORY = new Factory<>(RadioNetworksSavedData::new,
            (tag, registries) -> load(tag));
    private final Set<UUID> networks = new HashSet<>();
    public static RadioNetworksSavedData get(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        var existing = storage.get(FACTORY, "homelink_dashboard_radio");
        if (existing == null && java.nio.file.Files.exists(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data/homelink_dashboard_radio.dat"))) throw new IllegalStateException("Cannot load existing radio networks");
        return storage.computeIfAbsent(FACTORY, "homelink_dashboard_radio");
    }
    public static RadioNetworksSavedData load(CompoundTag tag) {
        if (!(tag.get("networks") instanceof ListTag entries) || entries.size() > 65536
                || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid radio network storage");
        var data = new RadioNetworksSavedData();
        for (Tag raw : entries) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("id")) throw new IllegalArgumentException("Invalid radio network identity");
            data.networks.add(entry.getUUID("id"));
        }
        return data;
    }
    public boolean contains(UUID network) { return networks.contains(network); }
    public void add(UUID network, MinecraftServer server) {
        if (networks.contains(network)) return;
        networks.removeIf(id -> fr.lkdm.homecore.api.DashboardAPI.networks(server).getNetwork(id).isEmpty());
        if (networks.size() >= 65536) throw new IllegalStateException("Radio network capacity reached");
        if (networks.add(network)) setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (UUID id : networks) { var entry = new CompoundTag(); entry.putUUID("id", id); entries.add(entry); }
        tag.put("networks", entries);
        return tag;
    }
}

package fr.lkdm.homelink.dashboard.server;

import com.mojang.logging.LogUtils;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** World-backed personal layouts and favorites. No device snapshots are persisted here. */
public final class DashboardPreferencesSavedData extends SavedData {
    public static final String FILE_ID = "homelink_dashboard_preferences";
    private static final int VERSION = 1;
    private static final int MAX_PROFILES = 4096;
    private static final int MAX_NETWORKS_PER_PLAYER = 64;
    private static final Factory<DashboardPreferencesSavedData> FACTORY = new Factory<>(DashboardPreferencesSavedData::new,
            (tag, provider) -> {
                try { return load(tag, provider); }
                catch (RuntimeException exception) { return new DashboardPreferencesSavedData(exception); }
            });
    private record Key(UUID player, UUID network) { }
    private final Map<Key, DashboardProfile> profiles = new LinkedHashMap<>();
    private final RuntimeException failure;

    public DashboardPreferencesSavedData() { failure = null; }
    private DashboardPreferencesSavedData(RuntimeException failure) {
        this.failure = failure;
        LogUtils.getLogger().error("Cannot load HomeLink Dashboard preferences; existing data will not be overwritten", failure);
    }

    public static DashboardPreferencesSavedData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Preferences require server thread");
        var storage = server.overworld().getDataStorage();
        var existing = storage.get(FACTORY, FILE_ID);
        if (existing == null && Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_ID + ".dat")))
            throw new IllegalStateException("Existing Dashboard preferences could not be loaded; refusing replacement");
        var data = storage.computeIfAbsent(FACTORY, FILE_ID);
        data.ensureValid();
        return data;
    }

    public DashboardProfile profile(UUID player, UUID network) {
        ensureValid();
        return profiles.getOrDefault(new Key(Objects.requireNonNull(player), Objects.requireNonNull(network)), DashboardProfile.EMPTY);
    }

    public void put(UUID player, UUID network, DashboardProfile profile) {
        ensureValid();
        Key key = new Key(Objects.requireNonNull(player), Objects.requireNonNull(network));
        Objects.requireNonNull(profile);
        if (profile.equals(DashboardProfile.EMPTY)) {
            if (profiles.remove(key) != null) setDirty();
            return;
        }
        if (!profiles.containsKey(key) && (profiles.size() >= MAX_PROFILES
                || profiles.keySet().stream().filter(entry -> entry.player.equals(player)).count() >= MAX_NETWORKS_PER_PLAYER))
            throw new IllegalArgumentException("Dashboard profile storage limit reached");
        if (!profile.equals(profiles.put(key, profile))) setDirty();
    }

    public static DashboardPreferencesSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains("formatVersion", Tag.TAG_INT) || tag.getInt("formatVersion") != VERSION
                || !(tag.get("profiles") instanceof ListTag entries) || entries.size() > MAX_PROFILES
                || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Unsupported or malformed Dashboard preferences");
        DashboardPreferencesSavedData result = new DashboardPreferencesSavedData();
        int rejected = 0;
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                if (!entry.hasUUID("player") || !entry.hasUUID("network") || !entry.contains("profile", Tag.TAG_COMPOUND))
                    throw new IllegalArgumentException("Incomplete profile entry");
                Key key = new Key(entry.getUUID("player"), entry.getUUID("network"));
                if (result.profiles.containsKey(key)) throw new IllegalArgumentException("Duplicate profile entry");
                result.put(key.player, key.network, DashboardProfile.fromTag(entry.getCompound("profile")));
            } catch (IllegalArgumentException exception) { rejected++; }
        }
        result.setDirty(false);
        if (rejected > 0) LogUtils.getLogger().warn("Skipped {} invalid HomeLink Dashboard preference entries", rejected);
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ensureValid();
        tag.putInt("formatVersion", VERSION);
        ListTag entries = new ListTag();
        profiles.forEach((key, profile) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", key.player); entry.putUUID("network", key.network); entry.put("profile", profile.toTag());
            entries.add(entry);
        });
        tag.put("profiles", entries);
        return tag;
    }

    private void ensureValid() {
        if (failure != null) throw new IllegalStateException("Preferences are unreadable; refusing replacement", failure);
    }
}

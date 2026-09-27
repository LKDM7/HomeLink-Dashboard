package fr.lkdm.homelink.dashboard.client.state;

import fr.lkdm.homelink.dashboard.HomeLinkDashboard;
import fr.lkdm.homelink.dashboard.block.DashboardDisplayBlock;
import fr.lkdm.homelink.dashboard.blockentity.DashboardDisplayBlockEntity;
import fr.lkdm.homelink.dashboard.network.DisplaySummary;
import fr.lkdm.homelink.dashboard.network.DisplaySummaryPayloads;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Short-lived, per-viewer facade data. Never shares or creates a Dashboard menu subscription. */
@EventBusSubscriber(modid = HomeLinkDashboard.MOD_ID, value = Dist.CLIENT)
public final class DisplaySummaryClient {
    private static final int MAX_ENTRIES = 256;
    private static final int LIFETIME_TICKS = 45;
    private static final Map<BlockPos, Entry> ENTRIES = new LinkedHashMap<>();
    private static ClientLevel currentLevel;
    private static long ticks;

    private record Entry(DashboardDisplayBlockEntity entity, DisplaySummary summary, long received) { }
    private DisplaySummaryClient() { }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DisplaySummaryPayloads.listen(DisplaySummaryClient::receive));
    }

    public static void receive(DisplaySummaryPayloads.Update packet) {
        checkLevel();
        if (currentLevel == null || !currentLevel.dimension().location().equals(packet.dimension())
                || !currentLevel.hasChunkAt(packet.master())
                || !(currentLevel.getBlockEntity(packet.master()) instanceof DashboardDisplayBlockEntity entity)
                || entity.getBlockState().getValue(DashboardDisplayBlock.PART) != DashboardDisplayBlock.Part.BOTTOM_LEFT) return;
        ENTRIES.put(packet.master(), new Entry(entity, packet.summary(), ticks));
        while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.remove(ENTRIES.keySet().iterator().next());
    }

    /** Null means waiting for a fresh authorized sample; callers must not retain the old values. */
    public static DisplaySummary get(DashboardDisplayBlockEntity entity) {
        checkLevel();
        var entry = ENTRIES.get(entity.getBlockPos());
        return entry != null && entry.entity() == entity && valid(entry) ? entry.summary() : null;
    }

    private static boolean valid(Entry entry) {
        var player = Minecraft.getInstance().player;
        var entity = entry.entity();
        return currentLevel != null && entity.getLevel() == currentLevel && !entity.isRemoved()
                && ticks - entry.received() <= LIFETIME_TICKS && player != null
                && entity.getBlockPos().closerToCenterThan(player.position(), 16)
                && currentLevel.hasChunkAt(entity.getBlockPos())
                && currentLevel.getBlockEntity(entity.getBlockPos()) == entity;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        checkLevel();
        ticks++;
        ENTRIES.values().removeIf(entry -> !valid(entry));
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void checkLevel() {
        var level = Minecraft.getInstance().level;
        if (level != currentLevel) { clear(); currentLevel = level; }
    }

    public static void clear() { ENTRIES.clear(); currentLevel = null; ticks = 0; }
    public static int size() { return ENTRIES.size(); }
}

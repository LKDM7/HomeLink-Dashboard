package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homecore.api.transport.WireValue;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkWatchStateGameTests {
    private static final UUID NETWORK = new UUID(81, 1);
    private static final ResourceLocation METRIC = ResourceLocation.parse("fixture:value");

    @GameTest(template = "empty")
    public static void hundredDeviceWatchSeparatesMetricAndStructureChanges(GameTestHelper helper) {
        Transport wire = new Transport();
        try (DashboardClientState state = wire.state()) {
            state.start();
            List<UUID> ids = IntStream.range(0, 100).mapToObj(index -> new UUID(82, index)).toList();
            wire.roster(ids, 100);
            helper.assertTrue(state.loading() && state.isNetworkWatch(), "Watch must wait for its streamed initial snapshots");
            ids.forEach(id -> wire.emit(snapshot(id, "ONLINE")));
            helper.assertTrue(!state.loading() && state.devices().size() == 100 && state.requestCount() == 1 && !state.truncated(),
                    "A hundred devices must load under one network request");
            long structure = state.structureRevision();
            wire.emit(new HomeCorePayloads.MetricUpdate(NETWORK, ids.get(99), METRIC, 1, WireValue.from(42)));
            helper.assertTrue(state.structureRevision() == structure && state.deltaCount() == 1,
                    "Metric deltas must not invalidate device ordering/search structure");
            helper.assertTrue(state.device(ids.get(99)).orElseThrow().metrics().getFirst().displayValue().equals("42"),
                    "The last watched device must receive live values");
            wire.emit(snapshot(ids.get(99), "WARNING"));
            helper.assertTrue(state.structureRevision() > structure, "Status changes must invalidate status filters");
            for (int tick = 0; tick < 100; tick++) state.tick();
            helper.assertTrue(state.requestCount() == 1, "Steady-state ticks must not poll the server");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rosterChangesRetainHistoryAndHealthyRecords(GameTestHelper helper) {
        Transport wire = new Transport();
        try (DashboardClientState state = wire.state()) {
            state.start();
            UUID first = new UUID(83, 1), second = new UUID(83, 2), third = new UUID(83, 3);
            wire.roster(List.of(first, second), 2); wire.emit(snapshot(first, "ONLINE")); wire.emit(snapshot(second, "ONLINE"));
            var retained = state.device(second).orElseThrow();
            wire.emit(new HomeCorePayloads.DeviceEventNotification(NETWORK, new DeviceEvent(METRIC, first, Instant.EPOCH,
                    DeviceEvent.Severity.WARNING, Map.of("message", "Retain source history"))));
            wire.roster(List.of(second, third), 2);
            helper.assertTrue(state.device(first).isEmpty() && state.device(second).orElseThrow() == retained && state.loading(),
                    "Roster deltas must prune removed IDs and retain healthy device records while awaiting additions");
            wire.emit(snapshot(third, "ONLINE"));
            helper.assertTrue(!state.loading() && state.alerts().size() == 1 && state.error().isEmpty(),
                    "Device removal must retain an authorized network and its recent alerts");
            wire.emit(new HomeCorePayloads.NetworkWatchResponse(wire.request, NETWORK, List.of(), 0, false, ActionResult.Code.DENIED));
            helper.assertTrue(state.devices().isEmpty() && state.alerts().isEmpty(), "Revocation must still erase all protected state");
        }
        helper.succeed();
    }

    private static HomeCorePayloads.DeviceSnapshot snapshot(UUID device, String status) {
        CompoundTag data = new CompoundTag();
        data.putString("name", "Device " + device.getLeastSignificantBits()); data.putString("type", "fixture:watched"); data.putString("status", status);
        CompoundTag metric = new CompoundTag(); metric.putString("id", METRIC.toString()); metric.putString("name", "Value");
        metric.put("value", WireValue.from(1).toTag());
        ListTag metrics = new ListTag(); metrics.add(metric); data.put("metrics", metrics);
        return new HomeCorePayloads.DeviceSnapshot(NETWORK, device, data);
    }

    private static final class Transport implements DashboardClientState.Transport {
        private UUID request;
        private Consumer<CustomPacketPayload> listener;
        DashboardClientState state() { return new DashboardClientState(NETWORK, this, () -> 0L); }
        @Override public boolean supportsNetworkWatch() { return true; }
        @Override public UUID watch(UUID network) { return request = UUID.randomUUID(); }
        @Override public boolean connected() { return true; }
        @Override public AutoCloseable listen(Consumer<CustomPacketPayload> listener) { this.listener = listener; return () -> this.listener = null; }
        @Override public UUID request(UUID network, int offset) { throw new AssertionError("Live watch must not fall back to page polling"); }
        @Override public void unsubscribe(UUID network) { }
        void emit(CustomPacketPayload payload) { if (listener != null) listener.accept(payload); }
        void roster(List<UUID> ids, int total) { emit(new HomeCorePayloads.NetworkWatchResponse(request, NETWORK, ids, total, total > ids.size(), ActionResult.Code.SUCCESS)); }
    }
}

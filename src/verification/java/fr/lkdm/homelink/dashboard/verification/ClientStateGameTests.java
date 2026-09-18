package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homecore.api.transport.WireValue;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Deterministic tests of the client state machine using real public HomeCore payloads. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClientStateGameTests {
    private static final UUID NETWORK = new UUID(1, 1);
    private static final UUID DEVICE = new UUID(2, 1);
    private static final ResourceLocation METRIC = ResourceLocation.fromNamespaceAndPath("fixture", "count");

    @GameTest(template = "empty")
    public static void orderedDeltasAndPageReplacement(GameTestHelper helper) {
        FakeTransport wire = new FakeTransport();
        try (DashboardClientState state = wire.state()) {
            state.start();
            wire.respond(ActionResult.Code.SUCCESS, List.of(DEVICE), 16);
            wire.emit(snapshot(DEVICE, "ONLINE", 5, 10));
            var stableList = state.devices();
            helper.assertTrue(!state.loading() && stableList.size() == 1, "Initial snapshot must finish loading");
            wire.emit(delta(DEVICE, 6, 20));
            wire.emit(delta(DEVICE, 5, 99));
            wire.emit(delta(DEVICE, 6, 99));
            wire.emit(delta(new UUID(2, 2), 100, 99));
            wire.emit(new HomeCorePayloads.MetricUpdate(new UUID(1, 2), DEVICE, METRIC, 100, WireValue.from(99)));
            helper.assertTrue(state.deltaCount() == 1, "Stale, duplicate, other-device and other-network deltas must be rejected");
            helper.assertTrue(state.snapshotCount() == 1 && stableList == state.devices(), "Deltas must preserve the stable list and snapshot counter");
            helper.assertTrue(state.devices().getFirst().metrics().getFirst().displayValue().equals("20"), "Newest delta must remain displayed");
            wire.emit(snapshot(DEVICE, "WARNING", 8, 30));
            wire.emit(delta(DEVICE, 7, 99));
            helper.assertTrue(state.devices().getFirst().status().equals("WARNING"), "Status snapshot must replace status");
            helper.assertTrue(state.devices().getFirst().metrics().getFirst().revision() == 8 && state.deltaCount() == 1,
                    "Status snapshot revisions become the delta baseline");
            wire.now = 600_000_000L;
            state.requestPage(16);
            UUID second = new UUID(2, 2);
            wire.respond(ActionResult.Code.SUCCESS, List.of(second), -1);
            wire.emit(snapshot(second, "OFFLINE", 1, 7));
            wire.emit(delta(DEVICE, 100, 99));
            helper.assertTrue(state.offset() == 16 && state.nextOffset() == -1 && state.devices().size() == 1,
                    "Page replacement must retain only the active page");
            helper.assertTrue(state.devices().getFirst().id().equals(second) && state.deltaCount() == 1,
                    "Previous-page deltas must not restore removed entries");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rateLimitedRefreshRetainsRevocableSubscription(GameTestHelper helper) {
        FakeTransport wire = new FakeTransport();
        try (DashboardClientState state = wire.state()) {
            state.start();
            UUID activeRequest = wire.lastRequest;
            wire.respond(ActionResult.Code.SUCCESS, List.of(DEVICE), -1);
            wire.emit(snapshot(DEVICE, "ONLINE", 0, 1));
            wire.now = 600_000_000L;
            state.refresh();
            UUID refreshRequest = wire.lastRequest;
            wire.respond(ActionResult.Code.RATE_LIMITED, List.of(), -1);
            helper.assertTrue(state.error().equals("RATE_LIMITED") && !state.loading() && state.devices().size() == 1,
                    "Rate limiting a refresh must retain the existing authorized page");
            wire.emit(delta(DEVICE, 1, 2));
            helper.assertTrue(state.deltaCount() == 1, "Existing live subscription must continue after rate limit");
            wire.emit(new HomeCorePayloads.DeviceListResponse(refreshRequest, Optional.of(NETWORK), List.of(), -1, ActionResult.Code.DENIED));
            helper.assertTrue(state.devices().size() == 1, "An obsolete failed request must not invalidate the active subscription");
            wire.emit(new HomeCorePayloads.DeviceListResponse(activeRequest, Optional.of(NETWORK), List.of(), -1, ActionResult.Code.DENIED));
            helper.assertTrue(state.devices().isEmpty() && state.error().equals("DENIED"),
                    "Unsolicited revocation of the retained subscription must immediately erase data");
            wire.emit(snapshot(DEVICE, "ONLINE", 2, 9));
            wire.emit(delta(DEVICE, 3, 10));
            helper.assertTrue(state.devices().isEmpty(), "Late data after revocation must stay hidden");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void requestTimeoutAndSessionCleanup(GameTestHelper helper) {
        FakeTransport wire = new FakeTransport();
        DashboardClientState state = wire.state();
        helper.assertTrue(wire.requests == 0 && wire.listeners == 0, "Construction must not start network work");
        state.start();
        state.start();
        helper.assertTrue(wire.requests == 1 && wire.listeners == 1, "Repeated start must be idempotent");
        wire.now = 10_000_000_000L;
        state.tick();
        helper.assertTrue(state.error().equals("TIMEOUT") && !state.loading(), "No response must time out without polling");
        state.tick();
        helper.assertTrue(wire.requests == 1, "Timeout must not create an automatic polling loop");
        state.refresh();
        wire.respond(ActionResult.Code.SUCCESS, List.of(), -1);
        state.requestPage(16);
        state.requestPage(32);
        helper.assertTrue(wire.requests == 2 && state.loading(), "Rapid clicks must queue one bounded pending request");
        wire.now += 600_000_000L;
        state.tick();
        helper.assertTrue(wire.requests == 3 && wire.lastOffset == 32, "Pending requests must coalesce and respect the request interval");
        Consumer<CustomPacketPayload> removedListener = wire.listener;
        state.close();
        state.close();
        helper.assertTrue(state.isClosed() && wire.listeners == 0 && wire.unsubscribes == 1,
                "Close must unsubscribe once and release the listener");
        removedListener.accept(snapshot(DEVICE, "ONLINE", 1, 1));
        state.refresh();
        state.tick();
        helper.assertTrue(state.devices().isEmpty() && wire.requests == 3, "Closed state must reject late packets and requests");

        FakeTransport disconnected = new FakeTransport();
        try (DashboardClientState second = disconnected.state()) {
            second.start();
            disconnected.connected = false;
            second.tick();
            helper.assertTrue(second.isClosed() && second.error().equals("DISCONNECTED") && disconnected.listeners == 0,
                    "Disconnect must clear and dispose the local state");
            helper.assertTrue(disconnected.unsubscribes == 0, "Disconnect must not send on a missing connection");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownValuesAndBoundedSnapshot(GameTestHelper helper) {
        FakeTransport wire = new FakeTransport();
        try (DashboardClientState state = wire.state()) {
            state.start();
            wire.respond(ActionResult.Code.SUCCESS, List.of(DEVICE), -1);
            CompoundTag data = new CompoundTag();
            data.putString("name", "Long device\n" + "x".repeat(1024));
            data.putString("type", "fixture:unknown");
            ListTag metrics = new ListTag();
            for (int index = 0; index < 140; index++) {
                CompoundTag metric = new CompoundTag();
                metric.putString("id", "fixture:value_" + index);
                metric.putString("name", "Custom metric");
                metric.putString("type", "fixture:future_type");
                CompoundTag value = new CompoundTag();
                value.putString("kind", "FUTURE_UNKNOWN_KIND");
                metric.put("value", value);
                metrics.add(metric);
            }
            data.put("metrics", metrics);
            wire.emit(new HomeCorePayloads.DeviceSnapshot(NETWORK, DEVICE, data));
            var view = state.devices().getFirst();
            helper.assertTrue(view.metrics().size() == 128, "Debug state must bound third-party metric allocations");
            helper.assertTrue(view.name().length() <= 256 && !view.name().contains("\n"), "Untrusted text must be bounded and single-line");
            helper.assertTrue(view.status().equals("UNKNOWN"), "Missing status must have a fallback");
            helper.assertTrue(view.metrics().getFirst().displayValue().equals("? (unsupported value)"),
                    "Unknown future wire kinds must use a harmless display fallback");
        }
        helper.succeed();
    }

    private static HomeCorePayloads.DeviceSnapshot snapshot(UUID device, String status, long revision, int value) {
        CompoundTag data = new CompoundTag();
        data.putString("name", "Fixture device");
        data.putString("type", "fixture:device");
        data.putString("status", status);
        CompoundTag metric = new CompoundTag();
        metric.putString("id", METRIC.toString());
        metric.putString("name", "Count");
        metric.putString("type", "homecore:integer");
        metric.putLong("revision", revision);
        metric.put("value", WireValue.from(value).toTag());
        ListTag metrics = new ListTag();
        metrics.add(metric);
        data.put("metrics", metrics);
        return new HomeCorePayloads.DeviceSnapshot(NETWORK, device, data);
    }

    private static HomeCorePayloads.MetricUpdate delta(UUID device, long revision, int value) {
        return new HomeCorePayloads.MetricUpdate(NETWORK, device, METRIC, revision, WireValue.from(value));
    }

    private static final class FakeTransport implements DashboardClientState.Transport {
        private boolean connected = true;
        private long now;
        private int requests;
        private int listeners;
        private int unsubscribes;
        private int lastOffset;
        private UUID lastRequest;
        private Consumer<CustomPacketPayload> listener;

        DashboardClientState state() { return new DashboardClientState(NETWORK, this, () -> now); }
        @Override public boolean connected() { return connected; }
        @Override public AutoCloseable listen(Consumer<CustomPacketPayload> listener) {
            this.listener = listener;
            listeners++;
            return () -> { this.listener = null; listeners--; };
        }
        @Override public UUID request(UUID network, int offset) {
            if (!network.equals(NETWORK)) throw new AssertionError("Wrong requested network");
            lastOffset = offset;
            lastRequest = new UUID(3, ++requests);
            return lastRequest;
        }
        @Override public void unsubscribe(UUID network) {
            if (!network.equals(NETWORK)) throw new AssertionError("Wrong unsubscribed network");
            unsubscribes++;
        }
        void emit(CustomPacketPayload payload) {
            if (listener != null) listener.accept(payload);
        }
        void respond(ActionResult.Code result, List<UUID> ids, int nextOffset) {
            emit(new HomeCorePayloads.DeviceListResponse(lastRequest, Optional.of(NETWORK), ids, nextOffset, result));
        }
    }
}

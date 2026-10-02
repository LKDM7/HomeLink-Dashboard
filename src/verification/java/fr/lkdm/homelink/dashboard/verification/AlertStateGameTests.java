package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.MachineSystems;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AlertStateGameTests {
    private static final UUID NETWORK = new UUID(61, 1);
    private static final UUID DEVICE = new UUID(62, 1);
    private static final UUID OWNER = new UUID(63, 1);

    @GameTest(template = "empty")
    public static void readAndAcknowledgedEventsAreIndependent(GameTestHelper helper) {
        Transport wire = new Transport();
        try (DashboardClientState state = wire.state()) {
            wire.start(state);
            var stable = state.alerts();
            wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.WARNING, "first"));
            long first = stable.getFirst().id();
            helper.assertTrue(!stable.getFirst().read() && !stable.getFirst().acknowledged()
                    && state.activeAlertCount() == 1 && state.unreadAlertCount() == 1,
                    "New alerts must be active and unread");
            long revision = state.alertRevision(), dashboardRevision = state.revision();
            helper.assertTrue(!state.setAlertRead(-1, true) && !state.acknowledgeAlert(-1)
                    && !state.setAlertRead(first, false) && state.alertRevision() == revision && state.revision() == dashboardRevision,
                    "Unknown events and unchanged read flags must not invalidate the UI");
            helper.assertTrue(state.setAlertRead(first, true) && state.activeAlertCount() == 1 && state.unreadAlertCount() == 0,
                    "Reading must leave an alert active");
            helper.assertTrue(state.setAlertRead(first, false) && state.unreadAlertCount() == 1,
                    "Read flags must be reversible");
            helper.assertTrue(state.acknowledgeAlert(first) && stable.getFirst().read() && stable.getFirst().acknowledged()
                    && state.activeAlertCount() == 0 && state.unreadAlertCount() == 0,
                    "Acknowledgement must also mark the retained event read");
            revision = state.alertRevision(); dashboardRevision = state.revision();
            helper.assertTrue(!state.acknowledgeAlert(first) && !state.setAlertRead(first, true)
                    && state.acknowledgeAllAlerts() == 0 && state.alertRevision() == revision && state.revision() == dashboardRevision,
                    "Repeated acknowledgement must be idempotent");
            helper.assertTrue(state.setAlertRead(first, false) && state.activeAlertCount() == 0 && state.unreadAlertCount() == 1,
                    "Unread flags must not undo acknowledgement");
            wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.CRITICAL, "second"));
            helper.assertTrue(stable == state.alerts() && stable.size() == 2 && stable.getFirst().id() != first
                    && !stable.getFirst().acknowledged() && state.activeAlertCount() == 1 && state.unreadAlertCount() == 2,
                    "A later event from the same source must create its own active unread alert");
            revision = state.alertRevision(); dashboardRevision = state.revision();
            helper.assertTrue(state.acknowledgeAllAlerts() == 2 && state.activeAlertCount() == 0 && state.unreadAlertCount() == 0
                    && state.alertRevision() == revision + 1 && state.revision() == dashboardRevision + 1,
                    "Bulk acknowledgement must report changes and invalidate each view only once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eventStateAndSystemSurviveRefreshAndPageChanges(GameTestHelper helper) {
        Transport wire = new Transport();
        try (DashboardClientState state = wire.state()) {
            wire.start(state);
            wire.device("homelink_energy:hydro_turbine");
            wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.WARNING, "retained"));
            long id = state.alerts().getFirst().id();
            state.acknowledgeAlert(id);
            wire.now = 600_000_000L;
            state.refresh();
            wire.initial();
            helper.assertTrue(state.alerts().getFirst().id() == id && state.alerts().getFirst().read()
                    && state.alerts().getFirst().acknowledged() && state.alerts().getFirst().system() == MachineSystems.Group.ENERGY,
                    "Refresh must preserve event identity, acknowledgement and captured source system");
            wire.now = 1_200_000_000L;
            state.requestPage(16);
            wire.emit(new HomeCorePayloads.DeviceListResponse(wire.request, Optional.of(NETWORK), List.of(), -1, ActionResult.Code.SUCCESS));
            helper.assertTrue(state.devices().isEmpty() && state.alerts().getFirst().sourceName().equals("Event fixture")
                    && state.alerts().getFirst().system() == MachineSystems.Group.ENERGY
                    && state.activeAlertCount() == 0 && state.unreadAlertCount() == 0,
                    "Page changes must retain event presentation and flags after the source disappears");
            state.setAlertRead(id, false);
            wire.emit(new HomeCorePayloads.DeviceListResponse(wire.request, Optional.of(NETWORK), List.of(), -1, ActionResult.Code.DENIED));
            helper.assertTrue(state.alerts().isEmpty() && state.activeAlertCount() == 0 && state.unreadAlertCount() == 0
                    && !state.acknowledgeAlert(id) && !state.setAlertRead(id, true),
                    "Revocation must clear event state as well as protected history");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void evictedEventsHaveNoRetainedState(GameTestHelper helper) {
        Transport wire = new Transport();
        DashboardClientState state = wire.state();
        state.setAlertLimit(2);
        wire.start(state);
        wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.INFO, "old"));
        long oldest = state.alerts().getFirst().id();
        state.acknowledgeAlert(oldest);
        wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.WARNING, "middle"));
        long middle = state.alerts().getFirst().id();
        state.setAlertRead(middle, true);
        wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.CRITICAL, "new"));
        helper.assertTrue(state.alerts().size() == 2 && state.activeAlertCount() == 2 && state.unreadAlertCount() == 1
                && !state.acknowledgeAlert(oldest) && !state.setAlertRead(oldest, true),
                "Oldest event eviction must also discard its flags and exclude it from counters");
        state.setAlertLimit(0);
        helper.assertTrue(state.alertLimit() == 1 && state.alerts().size() == 1 && state.activeAlertCount() == 1
                && state.unreadAlertCount() == 1 && !state.acknowledgeAlert(middle),
                "Shrinking history must trim state and clamp the budget to at least one event");
        state.close();
        helper.assertTrue(state.alerts().isEmpty() && state.activeAlertCount() == 0 && state.unreadAlertCount() == 0
                && state.acknowledgeAllAlerts() == 0, "Closing must release the entire session event state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void boundedEventsAndAuthorizedSource(GameTestHelper helper) {
        Transport wire = new Transport();
        try (DashboardClientState state = wire.state()) {
            state.setAlertLimit(3);
            wire.start(state);
            var stable = state.alerts();
            wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.INFO, "first"));
            wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.WARNING, "second"));
            wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.CRITICAL, "third"));
            wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.INFO, "fourth"));
            helper.assertTrue(stable == state.alerts() && stable.size() == 3, "Event history must be stable and bounded");
            helper.assertTrue(stable.getFirst().message().equals("fourth") && stable.getLast().message().equals("second"),
                    "Newest-first history must evict the oldest event");
            helper.assertTrue(stable.get(1).severity().equals("CRITICAL") && stable.getFirst().sourceName().equals("Event fixture"),
                    "Severity and source name must come from the authorized event and device");
            long revision = state.alertRevision();
            wire.emit(event(UUID.randomUUID(), DEVICE, DeviceEvent.Severity.CRITICAL, "foreign network"));
            wire.emit(event(NETWORK, UUID.randomUUID(), DeviceEvent.Severity.CRITICAL, "unsubscribed source"));
            helper.assertTrue(state.alertRevision() == revision && stable.size() == 3, "Unrelated event payloads must be ignored");
            state.setAlertLimit(1);
            helper.assertTrue(stable.size() == 1 && stable.getFirst().message().equals("fourth"), "Reducing limit must immediately trim oldest events");
            state.setAlertLimit(Integer.MAX_VALUE);
            helper.assertTrue(state.alertLimit() == 512, "Client event budget must remain bounded despite invalid config");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void networkMetadataAndAlertSessionLifetime(GameTestHelper helper) {
        Transport wire = new Transport();
        DashboardClientState state = wire.state();
        helper.assertTrue(state.connectionStatus().equals("OFFLINE"), "Unstarted session must be offline");
        state.start();
        helper.assertTrue(state.connectionStatus().equals("UNREACHABLE"), "Unconfirmed session must not claim connection");
        wire.initial();
        helper.assertTrue(state.connectionStatus().equals("CONNECTED") && state.ownerId().orElseThrow().equals(OWNER)
                && state.memberCount() == 4 && state.totalDeviceCount() == 17, "HomeCore network metadata must decode faithfully");
        wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.WARNING, "retained"));
        wire.now = 600_000_000L;
        state.refresh();
        wire.initial();
        helper.assertTrue(state.alerts().size() == 1 && state.alerts().getFirst().message().equals("retained"),
                "Manual page refresh must not erase recent event history");
        wire.emit(new HomeCorePayloads.DeviceListResponse(wire.request, Optional.of(NETWORK), List.of(), -1, ActionResult.Code.DENIED));
        helper.assertTrue(state.alerts().isEmpty() && state.ownerId().isEmpty() && state.memberCount() == 0
                && state.connectionStatus().equals("UNREACHABLE"), "Revocation must erase alerts and protected network metadata");
        wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.CRITICAL, "late"));
        helper.assertTrue(state.alerts().isEmpty(), "Late events after subscription revocation must be ignored");
        state.close();
        helper.assertTrue(wire.listener == null && state.connectionStatus().equals("OFFLINE"), "Closing must release listener and disconnect state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hostileEventPresentationFallbacks(GameTestHelper helper) {
        Transport wire = new Transport();
        try (DashboardClientState state = wire.state()) {
            wire.start(state);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("message", "Line\n" + "x".repeat(3000));
            for (int index = 0; index < 20; index++) data.put("field_" + index, "y".repeat(1000));
            var hostile = new DeviceEvent(ResourceLocation.fromNamespaceAndPath("fixture", "future_event"), DEVICE,
                    Instant.MAX, DeviceEvent.Severity.INFO, data);
            wire.emit(new HomeCorePayloads.DeviceEventNotification(NETWORK, hostile));
            var alert = state.alerts().getFirst();
            helper.assertTrue(alert.message().length() <= 256 && !alert.message().contains("\n"), "Third-party event messages must be bounded single-line text");
            helper.assertTrue(alert.data().size() <= 8 && alert.data().values().stream().allMatch(value -> value.length() <= 256),
                    "Decoded event fields must have bounded memory usage");
            helper.assertTrue(alert.timestamp().equals(Instant.EPOCH), "Unrenderable timestamps need a safe display fallback");
            wire.emit(event(NETWORK, DEVICE, DeviceEvent.Severity.INFO, "safe"));
            helper.assertTrue(state.alerts().getFirst().message().equals("safe"), "Bad presentation data must not stop later events");
        }
        helper.succeed();
    }

    private static HomeCorePayloads.DeviceEventNotification event(UUID network, UUID source, DeviceEvent.Severity severity, String text) {
        return new HomeCorePayloads.DeviceEventNotification(network, new DeviceEvent(
                ResourceLocation.fromNamespaceAndPath("fixture", "event"), source, Instant.ofEpochSecond(1_700_000_000), severity, Map.of("message", text)));
    }

    private static final class Transport implements DashboardClientState.Transport {
        private long now;
        private UUID request;
        private Consumer<CustomPacketPayload> listener;
        DashboardClientState state() { return new DashboardClientState(NETWORK, this, () -> now); }
        void start(DashboardClientState state) { state.start(); initial(); }
        void initial() {
            emit(new HomeCorePayloads.DeviceListResponse(request, Optional.of(NETWORK), List.of(DEVICE), 16, ActionResult.Code.SUCCESS));
            CompoundTag network = new CompoundTag();
            network.putString("name", "Event network"); network.putString("role", "OWNER"); network.putUUID("owner", OWNER);
            network.putInt("memberCount", 4); network.putInt("deviceCount", 17);
            emit(new HomeCorePayloads.HomeNetworkSnapshot(NETWORK, network));
            device("fixture:device");
        }
        void device(String type) {
            CompoundTag device = new CompoundTag();
            device.putString("name", "Event fixture"); device.putString("type", type); device.putString("status", "ONLINE");
            emit(new HomeCorePayloads.DeviceSnapshot(NETWORK, DEVICE, device));
        }
        void emit(CustomPacketPayload packet) { if (listener != null) listener.accept(packet); }
        @Override public boolean connected() { return true; }
        @Override public AutoCloseable listen(Consumer<CustomPacketPayload> listener) { this.listener = listener; return () -> this.listener = null; }
        @Override public UUID request(UUID network, int offset) { return request = UUID.randomUUID(); }
        @Override public void unsubscribe(UUID network) { }
    }
}

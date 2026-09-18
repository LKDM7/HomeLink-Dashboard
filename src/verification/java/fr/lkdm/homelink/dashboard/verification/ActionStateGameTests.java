package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.Unit;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ActionStateGameTests {
    private static final UUID NETWORK = new UUID(41, 1);
    private static final UUID DEVICE = new UUID(42, 1);

    @GameTest(template = "empty")
    public static void correlatedResultsAndNoOptimisticMutation(GameTestHelper helper) {
        Transport wire = new Transport();
        try (DashboardClientState state = wire.state()) {
            wire.start(state, "OWNER", "ONLINE", action("BUTTON"));
            var device = state.devices().getFirst();
            var descriptor = device.actions().getFirst();
            helper.assertTrue(state.executeAction(DEVICE, descriptor, Unit.INSTANCE), "Authorized action must be sent");
            helper.assertTrue(state.actionPending() && wire.executions == 1, "One action must await a server response");
            helper.assertTrue(!state.executeAction(DEVICE, descriptor, Unit.INSTANCE) && wire.executions == 1,
                    "Repeated clicks must not send concurrent actions");
            wire.emit(new HomeCorePayloads.ActionResultResponse(UUID.randomUUID(), ActionResult.success()));
            helper.assertTrue(state.actionPending() && state.lastActionCode().isEmpty(), "Uncorrelated results must be ignored");
            wire.emit(new HomeCorePayloads.ActionResultResponse(wire.actionRequest,
                    ActionResult.of(ActionResult.Code.SUCCESS, Component.literal("Accepted"))));
            helper.assertTrue(!state.actionPending() && state.lastActionCode().equals("SUCCESS")
                    && state.lastActionMessage().equals("Accepted"), "Matching response must update visible result");
            helper.assertTrue(state.devices().getFirst() == device && device.metrics().getFirst().displayValue().equals("7")
                    && state.deltaCount() == 0, "Sending and accepting an action must not mutate cached metrics optimistically");
            helper.assertTrue(state.executeAction(DEVICE, descriptor, Unit.INSTANCE), "A completed action must release the pending slot");
            wire.now = 10_000_000_000L;
            state.tick();
            helper.assertTrue(!state.actionPending() && state.lastActionCode().equals("FAILED")
                    && state.lastActionMessage().contains("timed out"), "Missing action response must have a bounded timeout");
            wire.emit(new HomeCorePayloads.ActionResultResponse(wire.actionRequest, ActionResult.success()));
            helper.assertTrue(state.lastActionCode().equals("FAILED"), "Late timed-out action results must be ignored");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void permissionsOfflineAndChangedSchema(GameTestHelper helper) {
        Transport wire = new Transport();
        try (DashboardClientState state = wire.state()) {
            wire.start(state, "VIEWER", "ONLINE", action("BUTTON"));
            var descriptor = state.devices().getFirst().actions().getFirst();
            helper.assertTrue(!state.canControl(descriptor) && !state.executeAction(DEVICE, descriptor, Unit.INSTANCE)
                    && wire.executions == 0 && state.lastActionCode().equals("DENIED"), "Viewer must not send controls");
            wire.role("OWNER");
            wire.emit(snapshot("OFFLINE", action("BUTTON")));
            descriptor = state.devices().getFirst().actions().getFirst();
            helper.assertTrue(!state.executeAction(DEVICE, descriptor, Unit.INSTANCE)
                    && state.lastActionCode().equals("DEVICE_OFFLINE") && wire.executions == 0, "Offline device must not send controls");
            wire.emit(snapshot("ONLINE", action("BUTTON")));
            descriptor = state.devices().getFirst().actions().getFirst();
            CompoundTag replacement = action("TEXT");
            replacement.putInt("maxLength", 16);
            wire.emit(snapshot("ONLINE", replacement));
            helper.assertTrue(!state.executeAction(DEVICE, descriptor, Unit.INSTANCE)
                    && state.lastActionCode().equals("INVALID_PARAMETER") && wire.executions == 0,
                    "A stale action descriptor must be rejected after a schema change");
            var current = state.devices().getFirst().actions().getFirst();
            helper.assertTrue(state.executeAction(DEVICE, current, "hello") && wire.executions == 1,
                    "The refreshed descriptor must be actionable");
            wire.emit(new HomeCorePayloads.ActionResultResponse(wire.actionRequest, ActionResult.of(ActionResult.Code.DENIED)));
            helper.assertTrue(state.lastActionCode().equals("DENIED") && !state.actionPending(),
                    "Server denial must override the client permission hint");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsupportedActionSchemaIsHarmless(GameTestHelper helper) {
        Transport wire = new Transport();
        try (DashboardClientState state = wire.state()) {
            wire.start(state, "OWNER", "ONLINE", action("FUTURE_CONTROL"));
            var unsupported = state.devices().getFirst().actions().getFirst();
            helper.assertTrue(!unsupported.supported() && !state.canControl(unsupported)
                    && !state.executeAction(DEVICE, unsupported, Unit.INSTANCE) && wire.executions == 0,
                    "Unknown action controls must remain visible as unsupported and never execute");
            CompoundTag invalidSlider = action("SLIDER");
            invalidSlider.putDouble("min", 10);
            invalidSlider.putDouble("max", 2);
            invalidSlider.putDouble("step", Double.NaN);
            wire.emit(snapshot("ONLINE", invalidSlider));
            helper.assertTrue(!state.devices().getFirst().actions().getFirst().supported(), "Invalid bounds must disable a control");
            CompoundTag extremeSlider = action("SLIDER");
            extremeSlider.putDouble("min", -Double.MAX_VALUE);
            extremeSlider.putDouble("max", Double.MAX_VALUE);
            wire.emit(snapshot("ONLINE", extremeSlider));
            helper.assertTrue(state.devices().getFirst().actions().getFirst().supported(),
                    "Finite extreme endpoints remain supported by decimal slider interpolation");
            CompoundTag invalidSelect = action("SELECT");
            CompoundTag option = new CompoundTag();
            option.putString("kind", "FUTURE_VALUE");
            ListTag options = new ListTag();
            options.add(option);
            invalidSelect.put("options", options);
            wire.emit(snapshot("ONLINE", invalidSelect));
            helper.assertTrue(!state.devices().getFirst().actions().getFirst().supported(), "Malformed option values must disable the whole action");
            CompoundTag largeSelect = action("SELECT");
            ListTag supportedOptions = new ListTag();
            for (int index = 0; index < 256; index++) supportedOptions.add(WireValue.from("choice_" + index).toTag());
            largeSelect.put("options", supportedOptions);
            wire.emit(snapshot("ONLINE", largeSelect));
            helper.assertTrue(state.devices().getFirst().actions().getFirst().supported()
                    && state.devices().getFirst().actions().getFirst().options().size() == 256,
                    "All 256 HomeCore-supported SELECT options must remain available");
            CompoundTag customPermission = action("BUTTON");
            customPermission.putString("permission", "fixture:unknown_permission");
            wire.emit(snapshot("ONLINE", customPermission));
            helper.assertTrue(!state.canControl(state.devices().getFirst().actions().getFirst()),
                    "Unknown permission must fail closed even for the owner UI");
            helper.assertTrue(wire.executions == 0, "Malformed metadata must never invoke transport");
        }
        helper.succeed();
    }

    private static CompoundTag action(String type) {
        CompoundTag action = new CompoundTag();
        action.putString("id", "fixture:command");
        action.putString("name", "Command");
        action.putString("description", "Fixture action");
        action.putString("type", type);
        action.putString("permission", "homecore:control");
        action.putInt("maxLength", 1024);
        return action;
    }

    private static HomeCorePayloads.DeviceSnapshot snapshot(String status, CompoundTag action) {
        CompoundTag device = new CompoundTag();
        device.putString("name", "Action fixture");
        device.putString("type", "fixture:device");
        device.putString("status", status);
        CompoundTag metric = new CompoundTag();
        metric.putString("id", "fixture:count");
        metric.putString("name", "Count");
        metric.putString("type", "homecore:integer");
        metric.put("value", WireValue.from(7).toTag());
        ListTag metrics = new ListTag();
        metrics.add(metric);
        device.put("metrics", metrics);
        ListTag actions = new ListTag();
        actions.add(action);
        device.put("actions", actions);
        return new HomeCorePayloads.DeviceSnapshot(NETWORK, DEVICE, device);
    }

    private static final class Transport implements DashboardClientState.Transport {
        private long now;
        private int executions;
        private UUID listRequest;
        private UUID actionRequest;
        private Consumer<CustomPacketPayload> listener;
        DashboardClientState state() { return new DashboardClientState(NETWORK, this, () -> now); }
        void start(DashboardClientState state, String role, String status, CompoundTag action) {
            state.start();
            emit(new HomeCorePayloads.DeviceListResponse(listRequest, Optional.of(NETWORK), List.of(DEVICE), -1, ActionResult.Code.SUCCESS));
            role(role);
            emit(snapshot(status, action));
        }
        void role(String role) {
            CompoundTag network = new CompoundTag();
            network.putString("name", "Action test network");
            network.putString("role", role);
            network.putInt("deviceCount", 1);
            emit(new HomeCorePayloads.HomeNetworkSnapshot(NETWORK, network));
        }
        void emit(CustomPacketPayload payload) { if (listener != null) listener.accept(payload); }
        @Override public boolean connected() { return true; }
        @Override public AutoCloseable listen(Consumer<CustomPacketPayload> listener) {
            this.listener = listener;
            return () -> this.listener = null;
        }
        @Override public UUID request(UUID network, int offset) { return listRequest = UUID.randomUUID(); }
        @Override public void unsubscribe(UUID network) { }
        @Override public UUID execute(UUID network, UUID device, String action, Object parameter) {
            if (!network.equals(NETWORK) || !device.equals(DEVICE) || !action.equals("fixture:command"))
                throw new AssertionError("Incorrect target identity");
            executions++;
            return actionRequest = UUID.randomUUID();
        }
    }
}

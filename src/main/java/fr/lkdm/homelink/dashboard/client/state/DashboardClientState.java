package fr.lkdm.homelink.dashboard.client.state;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.client.ClientDeviceCache;
import fr.lkdm.homecore.api.client.HomeCoreClient;
import fr.lkdm.homecore.api.metric.Duration;
import fr.lkdm.homecore.api.metric.Energy;
import fr.lkdm.homecore.api.metric.FluidValue;
import fr.lkdm.homecore.api.metric.ItemValue;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Position;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homecore.api.transport.WireValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-thread state for a bounded live network watch; the legacy page transport remains injectable for compatibility tests. */
public final class DashboardClientState implements AutoCloseable {
    public static final int PAGE_SIZE = 16;
    private static final long REQUEST_INTERVAL = 600_000_000L;
    private static final long TIMEOUT = 10_000_000_000L;

    /** A narrow seam for testing the actual snapshot/delta lifecycle without a running client. */
    public interface Transport {
        boolean connected();
        AutoCloseable listen(Consumer<CustomPacketPayload> listener);
        UUID request(UUID network, int offset);
        void unsubscribe(UUID network);
        default boolean supportsNetworkWatch() { return false; }
        default UUID watch(UUID network) { return request(network, 0); }
        default UUID execute(UUID network, UUID device, String action, Object parameter) {
            throw new UnsupportedOperationException("Action transport unavailable");
        }
    }

    private final UUID networkId;
    private final Transport transport;
    private final LongSupplier clock;
    private final boolean networkWatch;
    private final List<DebugDeviceView> devices = new ArrayList<>();
    private final List<DebugDeviceView> deviceView = Collections.unmodifiableList(devices);
    private final List<AlertView> alerts = new ArrayList<>();
    private final List<AlertView> alertView = Collections.unmodifiableList(alerts);
    private final Set<UUID> expected = new LinkedHashSet<>();
    private AutoCloseable listener;
    private UUID requestId;
    private UUID subscriptionId;
    private UUID actionRequestId;
    private long actionSentAt;
    private String lastActionCode = "";
    private String lastActionMessage = "";
    private String networkName = "";
    private String role = "UNKNOWN";
    private String error = "";
    private int totalDeviceCount;
    private UUID ownerId;
    private int memberCount;
    private int alertLimit = 128;
    private long alertSequence;
    private long alertRevision;
    private int offset;
    private int requestedOffset;
    private int nextOffset = -1;
    private Integer pendingOffset;
    private boolean started;
    private boolean closed;
    private boolean loading;
    private boolean awaitingSnapshots;
    private boolean sentRequest;
    private long sentAt;
    private long snapshotCount;
    private long deltaCount;
    private long revision;
    private long structureRevision;
    private long requestCount;
    private boolean truncated;

    public DashboardClientState(UUID networkId) {
        this(networkId, new LiveTransport(), System::nanoTime);
    }

    public DashboardClientState(UUID networkId, Transport transport, LongSupplier clock) {
        this.networkId = Objects.requireNonNull(networkId);
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
        networkWatch = transport.supportsNetworkWatch();
    }

    public void start() {
        if (started || closed) return;
        started = true;
        if (!transport.connected()) { disconnect(); return; }
        listener = transport.listen(this::accept);
        requestPage(0);
    }

    public void requestPage(int requested) {
        if (!started || closed || requested < 0) return;
        pendingOffset = networkWatch ? 0 : requested;
        tick();
    }

    public void refresh() { requestPage(offset); }

    /** Sends only explicitly queued requests; there is no periodic server polling. */
    public void tick() {
        if (!started || closed) return;
        if (!transport.connected()) { disconnect(); return; }
        long now = clock.getAsLong();
        if (actionRequestId != null && now - actionSentAt >= TIMEOUT) {
            actionRequestId = null;
            actionResult("FAILED", "Action response timed out");
        }
        if (loading && now - sentAt >= TIMEOUT) {
            requestId = null;
            awaitingSnapshots = false;
            loading = false;
            error = "TIMEOUT";
            revision++;
        }
        if (pendingOffset == null || loading || sentRequest && now - sentAt < REQUEST_INTERVAL) return;
        requestedOffset = pendingOffset;
        pendingOffset = null;
        sentAt = now;
        sentRequest = true;
        loading = true;
        error = "";
        revision++;
        try {
            requestCount++;
            requestId = networkWatch ? transport.watch(networkId) : transport.request(networkId, requestedOffset);
        } catch (RuntimeException exception) {
            loading = false;
            error = "FAILED";
            revision++;
        }
    }

    private void accept(CustomPacketPayload payload) {
        if (closed) return;
        if (payload instanceof HomeCorePayloads.NetworkWatchResponse response) {
            if (!networkWatch || !response.networkId().equals(networkId)) return;
            boolean requested = response.requestId().equals(requestId);
            boolean active = response.requestId().equals(subscriptionId);
            if (!requested && !active) return;
            if (response.result() == ActionResult.Code.DENIED || response.result() == ActionResult.Code.FAILED) {
                clearData();
                requestId = null; subscriptionId = null; pendingOffset = null; loading = false;
                error = response.result().name();
                revision++;
            } else if (response.result() == ActionResult.Code.SUCCESS) {
                if (requested) devices.clear();
                subscriptionId = response.requestId();
                if (requested) requestId = null;
                expected.clear(); expected.addAll(response.ids());
                devices.removeIf(device -> !expected.contains(device.id()));
                totalDeviceCount = response.totalCount(); truncated = response.truncated();
                offset = 0; nextOffset = -1;
                awaitingSnapshots = devices.size() < expected.size();
                loading = requestId != null || awaitingSnapshots;
                if (awaitingSnapshots) sentAt = clock.getAsLong();
                error = "";
                structureRevision++;
                revision++;
            } else if (requested) {
                requestId = null; loading = false; error = response.result().name(); revision++;
            }
        } else if (payload instanceof HomeCorePayloads.ActionResultResponse response) {
            if (!response.requestId().equals(actionRequestId)) return;
            actionRequestId = null;
            actionResult(response.result().code().name(), response.result().message().map(message -> message.getString()).orElse(""));
        } else if (payload instanceof HomeCorePayloads.DeviceListResponse response) {
            if (response.networkId().filter(networkId::equals).isEmpty()) return;
            boolean requested = response.requestId().equals(requestId);
            boolean active = response.requestId().equals(subscriptionId);
            if (!requested && !active) return;
            if (response.result() == ActionResult.Code.DENIED || response.result() == ActionResult.Code.FAILED) {
                clearData();
                requestId = null;
                subscriptionId = null;
                pendingOffset = null;
                loading = false;
                error = response.result().name();
                revision++;
            } else if (requested && response.result() == ActionResult.Code.SUCCESS) {
                subscriptionId = requestId;
                requestId = null;
                offset = requestedOffset;
                nextOffset = response.nextOffset();
                devices.clear();
                structureRevision++;
                expected.clear();
                response.ids().stream().limit(PAGE_SIZE).forEach(expected::add);
                awaitingSnapshots = !expected.isEmpty();
                loading = awaitingSnapshots;
                error = "";
                revision++;
            } else if (requested) {
                requestId = null;
                loading = false;
                error = response.result().name();
                revision++;
            }
        } else if (payload instanceof HomeCorePayloads.HomeNetworkSnapshot snapshot) {
            if (!snapshot.networkId().equals(networkId) || subscriptionId == null) return;
            CompoundTag tag = snapshot.data();
            networkName = bounded(tag.getString("name"), networkId.toString());
            role = bounded(tag.getString("role"), "UNKNOWN");
            if (!networkWatch) totalDeviceCount = Math.max(0, tag.getInt("deviceCount"));
            ownerId = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
            memberCount = Math.max(0, tag.getInt("memberCount"));
            revision++;
        } else if (payload instanceof HomeCorePayloads.DeviceSnapshot snapshot) {
            if (!snapshot.networkId().equals(networkId) || !expected.contains(snapshot.deviceId())) return;
            DebugDeviceView decoded = decode(snapshot);
            int index = indexOf(decoded.id());
            if (index < 0 || !sameStructure(devices.get(index), decoded)) structureRevision++;
            if (index < 0) devices.add(decoded); else devices.set(index, decoded);
            snapshotCount++;
            if (awaitingSnapshots && devices.size() == expected.size()) {
                awaitingSnapshots = false;
                loading = false;
            }
            revision++;
        } else if (payload instanceof HomeCorePayloads.DeviceEventNotification notification) {
            if (!notification.networkId().equals(networkId) || subscriptionId == null
                    || !networkWatch && !expected.contains(notification.event().source())) return;
            var event = notification.event();
            var data = new LinkedHashMap<String, String>();
            event.data().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(8)
                    .forEach(entry -> data.put(bounded(entry.getKey(), "field"), bounded(entry.getValue(), "")));
            String message = bounded(event.data().get("message"), "");
            if (message.isEmpty()) message = bounded(event.data().get("text"), "");
            if (message.isEmpty()) message = bounded(data.entrySet().stream().limit(3)
                    .map(entry -> entry.getKey() + ": " + entry.getValue()).collect(java.util.stream.Collectors.joining(" | ")), event.type().toString());
            int index = indexOf(event.source());
            String source = index < 0 ? event.source().toString() : devices.get(index).name();
            // Keep dates within the range of ordinary UI formatters, including hostile third-party event timestamps.
            Instant timestamp = event.timestamp();
            if (timestamp.getEpochSecond() < -62_135_596_800L || timestamp.getEpochSecond() > 253_402_300_799L) timestamp = Instant.EPOCH;
            if (alerts.size() == alertLimit) alerts.removeLast();
            alerts.addFirst(new AlertView(++alertSequence, event.source(), source, bounded(event.type().toString(), "unknown"),
                    timestamp, event.severity().name(), message, data));
            alertRevision++;
            revision++;
        } else if (payload instanceof HomeCorePayloads.MetricUpdate update) {
            if (!update.networkId().equals(networkId)) return;
            int index = indexOf(update.deviceId());
            if (index < 0) return;
            DebugDeviceView device = devices.get(index);
            for (int metricIndex = 0; metricIndex < device.metrics().size(); metricIndex++) {
                DebugDeviceView.Metric previous = device.metrics().get(metricIndex);
                if (!previous.id().equals(update.metricId().toString()) || update.revision() <= previous.revision()) continue;
                List<DebugDeviceView.Metric> metrics = new ArrayList<>(device.metrics());
                metrics.set(metricIndex, new DebugDeviceView.Metric(previous.id(), previous.name(),
                        format(update.value(), previous.unit()), update.revision(), previous.type(), previous.unit(), update.value()));
                devices.set(index, new DebugDeviceView(device.id(), device.name(), device.type(), device.status(), metrics,
                        device.position(), device.capabilities(), device.searchText(), device.actions(), device.powered()));
                deltaCount++;
                revision++;
                break;
            }
        }
    }

    private static DebugDeviceView decode(HomeCorePayloads.DeviceSnapshot snapshot) {
        CompoundTag tag = snapshot.data();
        var definitions = tag.getList("metrics", Tag.TAG_COMPOUND);
        List<DebugDeviceView.Metric> metrics = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < Math.min(128, definitions.size()); index++) {
            CompoundTag definition = definitions.getCompound(index);
            String id = bounded(definition.getString("id"), "unknown:" + index);
            if (!ids.add(id)) continue;
            String unit = bounded(definition.getString("unitSymbol"), "");
            String value;
            WireValue wireValue = null;
            try {
                wireValue = WireValue.fromTag(definition.getCompound("value"));
                value = format(wireValue, unit);
            }
            catch (RuntimeException exception) { value = "? (unsupported value)"; }
            metrics.add(new DebugDeviceView.Metric(id, bounded(definition.getString("name"), id), value,
                    definition.getLong("revision"), bounded(definition.getString("type"), "unknown"), unit, wireValue));
        }
        String position = "";
        if (tag.contains("x", Tag.TAG_INT) && tag.contains("y", Tag.TAG_INT) && tag.contains("z", Tag.TAG_INT)) {
            position = tag.getInt("x") + " / " + tag.getInt("y") + " / " + tag.getInt("z");
            String dimension = bounded(tag.getString("dimension"), "");
            if (!dimension.isEmpty()) position += " (" + dimension + ")";
        }
        List<String> capabilities = new ArrayList<>();
        var capabilityTags = tag.getList("capabilities", Tag.TAG_STRING);
        for (int index = 0; index < Math.min(128, capabilityTags.size()); index++) {
            String capability = bounded(capabilityTags.getString(index), "");
            if (!capability.isEmpty()) capabilities.add(capability);
        }
        return new DebugDeviceView(snapshot.deviceId(), bounded(tag.getString("name"), snapshot.deviceId().toString()),
                bounded(tag.getString("type"), "unknown"), bounded(tag.getString("status"), "UNKNOWN"), metrics,
                bounded(position, ""), capabilities, "", decodeActions(tag),
                tag.contains("powered", Tag.TAG_BYTE) ? tag.getBoolean("powered") : null);
    }

    private static boolean sameStructure(DebugDeviceView first, DebugDeviceView second) {
        return first.id().equals(second.id()) && first.name().equals(second.name()) && first.type().equals(second.type())
                && first.status().equals(second.status()) && first.capabilities().equals(second.capabilities());
    }

    private static List<DeviceActionView> decodeActions(CompoundTag device) {
        List<DeviceActionView> actions = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        var definitions = device.getList("actions", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(128, definitions.size()); index++) {
            CompoundTag definition = definitions.getCompound(index);
            String id = bounded(definition.getString("id"), "");
            if (ResourceLocation.tryParse(id) == null || !ids.add(id)) continue;
            String type = bounded(definition.getString("type"), "UNKNOWN");
            List<WireValue> options = new ArrayList<>();
            var optionTags = definition.getList("options", Tag.TAG_COMPOUND);
            if (optionTags.size() > 256) type = "UNKNOWN";
            for (int option = 0; option < Math.min(256, optionTags.size()); option++) {
                try { options.add(WireValue.fromTag(optionTags.getCompound(option))); }
                catch (RuntimeException exception) { type = "UNKNOWN"; }
            }
            // The server sends text in its own language: HomeCore's standard actions are translated here.
            String name = bounded(definition.getString("name"), id), description = bounded(definition.getString("description"), "");
            if (id.equals(DeviceActionView.POWER) || id.equals(DeviceActionView.RENAME)) {
                String key = "screen.homelink_dashboard.action_" + ResourceLocation.parse(id).getPath();
                name = net.minecraft.network.chat.Component.translatable(key).getString();
                description = net.minecraft.network.chat.Component.translatable(key + "_description").getString();
            }
            actions.add(new DeviceActionView(id, name, description, type,
                    bounded(definition.getString("permission"), "unknown"),
                    optionalDouble(definition, "min"), optionalDouble(definition, "max"), optionalDouble(definition, "step"),
                    definition.contains("maxLength", Tag.TAG_INT) ? definition.getInt("maxLength") : 1024, options));
        }
        return List.copyOf(actions);
    }

    private static Double optionalDouble(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        return tag.contains(key, Tag.TAG_DOUBLE) ? tag.getDouble(key) : Double.NaN;
    }

    /** UI affordance based on HomeCore's default role policy. The server remains authoritative. */
    public boolean canControl(DeviceActionView action) {
        if (closed || action == null || !action.supported()) return false;
        ResourceLocation permissionId = ResourceLocation.tryParse(action.permission());
        if (permissionId == null) return false;
        Permission permission = Permission.fromId(permissionId).orElse(null);
        if (permission == null) return false;
        return switch (role) {
            case "OWNER", "ADMIN" -> true;
            case "MEMBER" -> permission == Permission.VIEW || permission == Permission.CONTROL || permission == Permission.AUTOMATE;
            default -> false;
        };
    }

    /** Sends a request without mutating any metric or device locally. */
    public boolean executeAction(UUID deviceId, DeviceActionView action, Object parameter) {
        if (!started || closed || actionPending()) return false;
        if (!transport.connected()) { disconnect(); return false; }
        if (!canControl(action)) { actionResult("DENIED", "Action not permitted"); return false; }
        int index = indexOf(deviceId);
        if (index < 0) { actionResult("DEVICE_OFFLINE", "Device unavailable"); return false; }
        DebugDeviceView device = devices.get(index);
        if (!action.availableWhen(device.status())) { actionResult("DEVICE_OFFLINE", "Device unavailable"); return false; }
        if (!device.actions().contains(action)) { actionResult("INVALID_PARAMETER", "Action schema changed; select it again"); return false; }
        try {
            actionSentAt = clock.getAsLong();
            actionRequestId = Objects.requireNonNull(transport.execute(networkId, deviceId, action.id(), parameter));
            lastActionCode = "";
            lastActionMessage = "";
            revision++;
            return true;
        } catch (IllegalArgumentException exception) {
            actionResult("INVALID_PARAMETER", "Unsupported action parameter");
        } catch (RuntimeException exception) {
            actionResult("FAILED", "Unable to send action request");
        }
        return false;
    }

    private void actionResult(String code, String message) {
        lastActionCode = code;
        lastActionMessage = bounded(message, "");
        revision++;
    }

    /** Shared readable fallback for menu metrics and the in-world display. */
    public static String format(WireValue wire, String unit) {
        Object value = wire.value();
        String result = switch (value) {
            case Boolean enabled -> enabled ? "ON" : "OFF";
            case Percentage percent -> fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry.decimal(percent.value()) + "%";
            case Double number -> fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry.decimal(number);
            case Float number -> fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry.decimal(number);
            case Energy energy -> energy.stored() + " / " + energy.capacity();
            case Position position -> position.x() + " / " + position.y() + " / " + position.z();
            case BlockPos position -> position.getX() + " / " + position.getY() + " / " + position.getZ();
            case Duration duration -> duration.ticks() + " ticks";
            case ItemValue item -> item.count() + " " + item.item();
            case FluidValue fluid -> fluid.amount() + " " + fluid.fluid();
            case WireValue.EnumName enumeration -> enumeration.name();
            default -> String.valueOf(value);
        };
        if (!unit.isBlank() && !(value instanceof Percentage) && !(value instanceof Duration)) result += " " + unit;
        return bounded(result, "?");
    }

    private static String bounded(String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        String clean = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replace('\u00a7', '?');
        return clean.length() <= 256 ? clean : clean.substring(0, 253) + "...";
    }

    private int indexOf(UUID id) {
        for (int index = 0; index < devices.size(); index++) if (devices.get(index).id().equals(id)) return index;
        return -1;
    }

    private void clearData() {
        if (actionRequestId != null) {
            actionRequestId = null;
            actionResult("FAILED", "Dashboard session is no longer available");
        }
        devices.clear();
        structureRevision++;
        if (!alerts.isEmpty()) {
            alerts.clear();
            alertRevision++;
        }
        expected.clear();
        awaitingSnapshots = false;
        networkName = "";
        role = "UNKNOWN";
        totalDeviceCount = 0;
        ownerId = null;
        memberCount = 0;
        nextOffset = -1;
        truncated = false;
    }

    private void disconnect() {
        close();
        error = "DISCONNECTED";
        revision++;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        loading = false;
        pendingOffset = null;
        requestId = null;
        subscriptionId = null;
        if (listener != null) {
            try { listener.close(); } catch (Exception ignored) { /* Cache registrations are local handles. */ }
            listener = null;
        }
        if (started && transport.connected()) {
            try { transport.unsubscribe(networkId); } catch (RuntimeException ignored) { /* Connection may be closing. */ }
        }
        clearData();
        revision++;
    }

    public UUID networkId() { return networkId; }
    public String networkName() { return networkName; }
    public String role() { return role; }
    public int totalDeviceCount() { return totalDeviceCount; }
    public Optional<UUID> ownerId() { return Optional.ofNullable(ownerId); }
    public int memberCount() { return memberCount; }
    public List<AlertView> alerts() { return alertView; }
    public long alertRevision() { return alertRevision; }
    public int alertLimit() { return alertLimit; }
    public void setAlertLimit(int limit) {
        int bounded = Math.clamp(limit, 1, 512);
        if (alertLimit == bounded) return;
        alertLimit = bounded;
        while (alerts.size() > alertLimit) alerts.removeLast();
        alertRevision++;
        revision++;
    }
    public String connectionStatus() {
        if (closed || !started) return "OFFLINE";
        return subscriptionId != null && !error.equals("TIMEOUT") ? "CONNECTED" : "UNREACHABLE";
    }
    public List<DebugDeviceView> devices() { return deviceView; }
    public Optional<DebugDeviceView> device(UUID id) {
        int index = indexOf(id);
        return index < 0 ? Optional.empty() : Optional.of(devices.get(index));
    }
    public long structureRevision() { return structureRevision; }
    public long requestCount() { return requestCount; }
    public boolean isNetworkWatch() { return networkWatch; }
    public boolean truncated() { return truncated; }
    public int watchedDeviceLimit() { return HomeCorePayloads.WATCH_SIZE; }
    public int offset() { return offset; }
    public int nextOffset() { return nextOffset; }
    public boolean loading() { return loading || pendingOffset != null; }
    public String error() { return error; }
    public long snapshotCount() { return snapshotCount; }
    public long deltaCount() { return deltaCount; }
    public long revision() { return revision; }
    public boolean isClosed() { return closed; }
    public String lastActionCode() { return lastActionCode; }
    public String lastActionMessage() { return lastActionMessage; }
    public boolean actionPending() { return actionRequestId != null; }

    private static final class LiveTransport implements Transport {
        @Override public boolean connected() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.getConnection() != null && minecraft.player != null;
        }
        @Override public AutoCloseable listen(Consumer<CustomPacketPayload> listener) {
            return ClientDeviceCache.INSTANCE.listen(listener);
        }
        @Override public UUID request(UUID network, int offset) {
            return HomeCoreClient.requestDevices(Optional.of(network), offset);
        }
        @Override public void unsubscribe(UUID network) { HomeCoreClient.unsubscribe(network); }
        @Override public boolean supportsNetworkWatch() { return true; }
        @Override public UUID watch(UUID network) { return HomeCoreClient.subscribeNetwork(network); }
        @Override public UUID execute(UUID network, UUID device, String action, Object parameter) {
            return HomeCoreClient.executeAction(network, device, ResourceLocation.parse(action), parameter);
        }
    }
}

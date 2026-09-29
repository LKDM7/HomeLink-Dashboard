package fr.lkdm.homelink.dashboard.network;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homecore.api.security.RateLimiter;
import fr.lkdm.homelink.dashboard.HomeLinkDashboard;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import fr.lkdm.homelink.dashboard.server.DashboardPreferencesSavedData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only personal preferences travel here; HomeCore remains the device transport and authority. */
public final class PreferencesPayloads {
    public static final int MAX_BYTES = 32_768;
    public enum Operation { LOAD, SAVE_LAYOUT, TOGGLE_FAVORITE }
    private static final Map<MinecraftServer, RateLimiter> LIMITERS = new WeakHashMap<>();
    private static final Map<Long, Consumer<Response>> LISTENERS = new LinkedHashMap<>();
    private static long nextListener;
    private PreferencesPayloads() { }

    public record Request(UUID requestId, int containerId, UUID networkId, Operation operation, CompoundTag data)
            implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("preferences_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeUUID(packet.requestId); buffer.writeVarInt(packet.containerId); buffer.writeUUID(packet.networkId);
                    buffer.writeByte(packet.operation.ordinal()); writeTag(buffer, packet.data);
                }, buffer -> new Request(buffer.readUUID(), buffer.readVarInt(), buffer.readUUID(),
                        enumValue(Operation.values(), buffer.readUnsignedByte()), readTag(buffer)));
        public Request {
            Objects.requireNonNull(requestId); Objects.requireNonNull(networkId); Objects.requireNonNull(operation);
            if (containerId < 0) throw new IllegalArgumentException("Invalid container");
            data = Objects.requireNonNull(data).copy();
            if (data.sizeInBytes() > MAX_BYTES) throw new IllegalArgumentException("Preference request too large");
        }
        @Override public CompoundTag data() { return data.copy(); }
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Response(UUID requestId, int containerId, UUID networkId, ActionResult.Code result, DashboardProfile profile)
            implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(id("preferences_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> STREAM_CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeUUID(packet.requestId); buffer.writeVarInt(packet.containerId); buffer.writeUUID(packet.networkId);
                    buffer.writeByte(packet.result.ordinal()); writeTag(buffer, packet.profile.toTag());
                }, buffer -> new Response(buffer.readUUID(), buffer.readVarInt(), buffer.readUUID(),
                        enumValue(ActionResult.Code.values(), buffer.readUnsignedByte()), DashboardProfile.fromTag(readTag(buffer))));
        public Response {
            Objects.requireNonNull(requestId); Objects.requireNonNull(networkId); Objects.requireNonNull(result); Objects.requireNonNull(profile);
        }
        @Override public Type<Response> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Request.TYPE, Request.STREAM_CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, handle(player, packet));
        }));
        registrar.playToClient(Response.TYPE, Response.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> receive(packet)));
    }

    /** Server entry point also exercised by GameTests; caller identity never comes from packet data. */
    public static Response handle(ServerPlayer player, Request packet) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Preferences require server thread");
        RateLimiter limiter = LIMITERS.computeIfAbsent(player.server,
                server -> new RateLimiter(4, Duration.ofSeconds(1), 4096, System::nanoTime));
        if (!limiter.tryAcquire(player.getUUID())) return response(packet, ActionResult.Code.RATE_LIMITED, DashboardProfile.EMPTY);
        if (!(player.containerMenu instanceof DashboardMenu menu) || menu.containerId != packet.containerId
                || menu.session().networkId().filter(packet.networkId::equals).isEmpty() || !menu.stillValid(player)
                || !DashboardAPI.hasPermission(player, packet.networkId, Permission.VIEW))
            return response(packet, ActionResult.Code.DENIED, DashboardProfile.EMPTY);
        DashboardProfile current = DashboardProfile.EMPTY;
        try {
            var storage = DashboardPreferencesSavedData.get(player.server);
            current = storage.profile(player.getUUID(), packet.networkId);
            if (packet.operation == Operation.LOAD) return response(packet, ActionResult.Code.SUCCESS, current);
            var network = DashboardAPI.networks(player.server).getNetwork(packet.networkId).orElseThrow();
            DashboardProfile next;
            if (packet.operation == Operation.SAVE_LAYOUT) {
                if (!DashboardAPI.hasPermission(player, packet.networkId, Permission.CONFIGURE))
                    return response(packet, ActionResult.Code.DENIED, DashboardProfile.EMPTY);
                var requested = DashboardProfile.fromTag(packet.data);
                for (DashboardWidget widget : requested.widgets()) {
                    // Previously authorized references may become unavailable. Keeping/moving them must
                    // not prevent the player from editing or deleting other widgets in the same layout.
                    boolean retainedReference = current.widgets().stream().anyMatch(previous -> previous.id().equals(widget.id())
                            && previous.deviceId().equals(widget.deviceId()) && previous.type() == widget.type()
                            && previous.metricId().equals(widget.metricId()));
                    if (retainedReference || widget.type() == DashboardWidget.Type.ENERGY_BALANCE) continue;
                    if (!network.devices().contains(widget.deviceId())) throw new IllegalArgumentException("Device outside network");
                    if (widget.type() == DashboardWidget.Type.ACTION) {
                        var device = DashboardAPI.devices(player.server).get(widget.deviceId()).orElseThrow(
                                () -> new IllegalArgumentException("Device currently unavailable"));
                        // Only one-click actions fit a widget; the others need the full action editor.
                        if (device.actions().stream().noneMatch(action -> action.id().toString().equals(widget.metricId())
                                && DashboardWidget.oneClick(action.type().name())))
                            throw new IllegalArgumentException("Unknown action");
                    }
                    if (widget.type() == DashboardWidget.Type.METRIC) {
                        var device = DashboardAPI.devices(player.server).get(widget.deviceId()).orElseThrow(
                                () -> new IllegalArgumentException("Device currently unavailable"));
                        if (device.metrics().stream().noneMatch(metric -> metric.id().toString().equals(widget.metricId())))
                            throw new IllegalArgumentException("Unknown metric");
                    }
                }
                // Layout packets cannot modify favorites, even if they include that field.
                next = new DashboardProfile(requested.widgets(), current.favorites());
            } else {
                if (!packet.data.hasUUID("device")) throw new IllegalArgumentException("Missing favorite device");
                UUID device = packet.data.getUUID("device");
                var favorites = new HashSet<>(current.favorites());
                if (!favorites.remove(device)) {
                    if (!network.devices().contains(device)) throw new IllegalArgumentException("Device outside network");
                    favorites.add(device);
                }
                next = new DashboardProfile(current.widgets(), favorites);
            }
            storage.put(player.getUUID(), packet.networkId, next);
            return response(packet, ActionResult.Code.SUCCESS, next);
        } catch (IllegalArgumentException exception) {
            return response(packet, ActionResult.Code.INVALID_PARAMETER, current);
        } catch (RuntimeException exception) {
            return response(packet, ActionResult.Code.FAILED, current);
        }
    }

    private static Response response(Request request, ActionResult.Code code, DashboardProfile profile) {
        return new Response(request.requestId, request.containerId, request.networkId, code, profile);
    }

    /** Data-only callback registration: this shared payload class does not load GUI/client classes. */
    public static synchronized AutoCloseable listen(Consumer<Response> listener) {
        if (LISTENERS.size() >= 64) throw new IllegalStateException("Too many preference listeners");
        long id = ++nextListener;
        LISTENERS.put(id, Objects.requireNonNull(listener));
        return () -> { synchronized (PreferencesPayloads.class) { LISTENERS.remove(id); } };
    }

    private static synchronized void receive(Response packet) {
        for (var listener : List.copyOf(LISTENERS.values())) {
            try { listener.accept(packet); } catch (RuntimeException ignored) { }
        }
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HomeLinkDashboard.MOD_ID, path); }
    private static <T> T enumValue(T[] values, int ordinal) {
        if (ordinal >= values.length) throw new IllegalArgumentException("Unknown preference operation/result");
        return values[ordinal];
    }
    private static void writeTag(RegistryFriendlyByteBuf buffer, CompoundTag value) {
        ByteBuf temporary = Unpooled.buffer(256, MAX_BYTES);
        try {
            FriendlyByteBuf.writeNbt(temporary, value);
            FriendlyByteBuf.readNbt(temporary.duplicate(), NbtAccounter.create(MAX_BYTES));
            buffer.writeVarInt(temporary.readableBytes()); buffer.writeBytes(temporary);
        } finally { temporary.release(); }
    }
    private static CompoundTag readTag(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size <= 0 || size > MAX_BYTES || size > buffer.readableBytes()) throw new IllegalArgumentException("Invalid preference payload size");
        ByteBuf slice = buffer.readSlice(size);
        Tag value = FriendlyByteBuf.readNbt(slice, NbtAccounter.create(MAX_BYTES));
        if (!(value instanceof CompoundTag tag) || slice.isReadable()) throw new IllegalArgumentException("Invalid preference payload");
        return tag;
    }
}

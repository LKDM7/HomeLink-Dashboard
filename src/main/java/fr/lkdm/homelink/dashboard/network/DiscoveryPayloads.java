package fr.lkdm.homelink.dashboard.network;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.security.RateLimiter;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import fr.lkdm.homelink.dashboard.server.MachineDiscoveryService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Machine discovery derives its network from an authenticated, live menu session, never from packet data. */
public final class DiscoveryPayloads {
    public enum Operation { LIST, ADD, ADD_ALL }
    private static final Map<MinecraftServer, RateLimiter> LIMITERS = new WeakHashMap<>();
    private static Consumer<Response> listener;
    private DiscoveryPayloads() { }

    public static void listen(Consumer<Response> value) { listener = value; }
    public static void stopListening(Consumer<Response> value) { if (listener == value) listener = null; }

    public record Request(int containerId, Operation operation, Optional<UUID> device) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.parse("homelink_dashboard:discovery_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeVarInt(value.containerId);
                    buffer.writeEnum(value.operation);
                    buffer.writeBoolean(value.device.isPresent());
                    value.device.ifPresent(buffer::writeUUID);
                },
                buffer -> new Request(buffer.readVarInt(), buffer.readEnum(Operation.class),
                        buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty()));
        public Request {
            if (containerId < 0 || operation == null || device == null || operation == Operation.ADD && device.isEmpty())
                throw new IllegalArgumentException("Invalid discovery request");
        }
        @Override public Type<Request> type() { return TYPE; }
    }

    /** {@code operation} tells the client whether the code answers a listing or an addition. */
    public record Response(int containerId, Operation operation, ActionResult.Code result, MachineListing listing)
            implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(ResourceLocation.parse("homelink_dashboard:discovery_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeVarInt(value.containerId);
                    buffer.writeEnum(value.operation);
                    buffer.writeEnum(value.result);
                    buffer.writeEnum(value.listing.code());
                    buffer.writeVarInt(value.listing.total());
                    buffer.writeVarInt(value.listing.entries().size());
                    for (var entry : value.listing.entries()) {
                        buffer.writeUUID(entry.id());
                        buffer.writeUtf(entry.name(), MachineListing.MAX_NAME_LENGTH);
                        buffer.writeUtf(entry.type(), MachineListing.MAX_NAME_LENGTH);
                        buffer.writeEnum(entry.state());
                        buffer.writeUtf(entry.networkName(), MachineListing.MAX_NAME_LENGTH);
                        buffer.writeVarInt(entry.distance());
                        buffer.writeBoolean(entry.canAdd());
                    }
                },
                buffer -> {
                    int containerId = buffer.readVarInt();
                    var operation = buffer.readEnum(Operation.class);
                    var result = buffer.readEnum(ActionResult.Code.class);
                    var code = buffer.readEnum(ActionResult.Code.class);
                    int total = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MachineListing.MAX_ENTRIES) throw new IllegalArgumentException("Too many machines");
                    var entries = new ArrayList<MachineListing.Entry>(count);
                    for (int index = 0; index < count; index++) {
                        entries.add(new MachineListing.Entry(buffer.readUUID(),
                                buffer.readUtf(MachineListing.MAX_NAME_LENGTH), buffer.readUtf(MachineListing.MAX_NAME_LENGTH),
                                buffer.readEnum(MachineListing.State.class), buffer.readUtf(MachineListing.MAX_NAME_LENGTH),
                                buffer.readVarInt(), buffer.readBoolean()));
                    }
                    return new Response(containerId, operation, result, new MachineListing(code, entries, total));
                });
        @Override public Type<Response> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Request.TYPE, Request.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, handle(player, packet));
        }));
        registrar.playToClient(Response.TYPE, Response.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (listener != null) listener.accept(packet);
        }));
    }

    /** Server entry point also exercised by GameTests; the caller identity comes from the connection. */
    public static Response handle(ServerPlayer player, Request packet) {
        var limiter = LIMITERS.computeIfAbsent(player.server, server -> new RateLimiter(4, Duration.ofSeconds(1), 4096, System::nanoTime));
        if (!limiter.tryAcquire(player.getUUID()))
            return new Response(packet.containerId(), packet.operation(), ActionResult.Code.RATE_LIMITED, MachineListing.of(ActionResult.Code.RATE_LIMITED));
        if (!(player.containerMenu instanceof DashboardMenu menu) || menu.containerId != packet.containerId() || !menu.stillValid(player)
                || !(player.level().getBlockEntity(menu.position()) instanceof AccessPointBlockEntity point))
            return new Response(packet.containerId(), packet.operation(), ActionResult.Code.DENIED, MachineListing.of(ActionResult.Code.DENIED));
        try {
            var result = switch (packet.operation()) {
                case LIST -> ActionResult.Code.SUCCESS;
                case ADD -> MachineDiscoveryService.add(player, point, packet.device());
                case ADD_ALL -> MachineDiscoveryService.add(player, point, Optional.empty());
            };
            var listing = MachineDiscoveryService.describe(player, point);
            return new Response(packet.containerId(), packet.operation(), packet.operation() == Operation.LIST ? listing.code() : result, listing);
        } catch (RuntimeException failure) {
            return new Response(packet.containerId(), packet.operation(), ActionResult.Code.FAILED, MachineListing.of(ActionResult.Code.FAILED));
        }
    }
}

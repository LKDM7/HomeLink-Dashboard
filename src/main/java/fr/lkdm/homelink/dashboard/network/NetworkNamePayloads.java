package fr.lkdm.homelink.dashboard.network;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import fr.lkdm.homelink.dashboard.server.DashboardAccess;
import fr.lkdm.homelink.dashboard.server.DashboardNetworks;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Name mutations derive their network from an authenticated, live menu session. */
public final class NetworkNamePayloads {
    private static Consumer<Response> listener;
    private NetworkNamePayloads() { }
    public static void listen(Consumer<Response> value) { listener = value; }
    public static void stopListening(Consumer<Response> value) { if (listener == value) listener = null; }
    public record Request(int containerId, boolean create, String name) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.parse("homelink_dashboard:network_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeVarInt(value.containerId); buffer.writeBoolean(value.create); buffer.writeUtf(value.name, 128); },
                buffer -> new Request(buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(128)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Response(int containerId, ActionResult.Code result) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(ResourceLocation.parse("homelink_dashboard:network_name_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeVarInt(value.containerId); buffer.writeEnum(value.result); },
                buffer -> new Response(buffer.readVarInt(), buffer.readEnum(ActionResult.Code.class)));
        @Override public Type<Response> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Request.TYPE, Request.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var result = handle(player, packet);
                PacketDistributor.sendToPlayer(player, new Response(packet.containerId(), result));
                if (packet.create() && result == ActionResult.Code.SUCCESS
                        && player.level().getBlockEntity(((DashboardMenu) player.containerMenu).position()) instanceof AccessPointBlockEntity point)
                    DashboardAccess.open(player, point);
            }
        }));
        registrar.playToClient(Response.TYPE, Response.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (listener != null) listener.accept(packet);
        }));
    }
    public static ActionResult.Code handle(ServerPlayer player, Request packet) {
        if (!(player.containerMenu instanceof DashboardMenu menu) || menu.containerId != packet.containerId()
                || !menu.stillValid(player) || !(player.level().getBlockEntity(menu.position()) instanceof AccessPointBlockEntity point))
            return ActionResult.Code.DENIED;
        try {
            return packet.create() ? DashboardNetworks.create(player, point, packet.name())
                    : DashboardNetworks.rename(player, point, packet.name());
        } catch (RuntimeException failure) { return ActionResult.Code.FAILED; }
    }
}

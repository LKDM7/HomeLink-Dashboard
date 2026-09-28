package fr.lkdm.homelink.dashboard.network;

import fr.lkdm.homecore.api.transport.WireValue;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-pushed summaries only: there is no unauthenticated client query or global BE update. */
public final class DisplaySummaryPayloads {
    private static Consumer<Update> listener;
    private DisplaySummaryPayloads() { }

    public static void listen(Consumer<Update> value) { listener = Objects.requireNonNull(value); }

    public record Update(ResourceLocation dimension, BlockPos master, DisplaySummary summary) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(ResourceLocation.parse("homelink_dashboard:display_summary"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = StreamCodec.of(
                (buffer, update) -> {
                    buffer.writeResourceLocation(update.dimension);
                    buffer.writeBlockPos(update.master);
                    var summary = update.summary;
                    buffer.writeEnum(summary.mode());
                    buffer.writeUtf(summary.networkName(), DisplaySummary.MAX_NAME_LENGTH);
                    buffer.writeVarInt(summary.total());
                    buffer.writeVarInt(summary.online());
                    buffer.writeVarInt(summary.attention());
                    buffer.writeVarInt(summary.offline());
                    buffer.writeVarInt(summary.devices().size());
                    for (var device : summary.devices()) writeDevice(buffer, device);
                    buffer.writeVarInt(summary.widgets().size());
                    for (var widget : summary.widgets()) {
                        buffer.writeByte(widget.x());
                        buffer.writeByte(widget.y());
                        buffer.writeByte(widget.width());
                        buffer.writeByte(widget.height());
                        buffer.writeBoolean(widget.metric());
                        writeDevice(buffer, widget.device());
                    }
                }, buffer -> {
                    var dimension = buffer.readResourceLocation();
                    var master = buffer.readBlockPos();
                    var mode = buffer.readEnum(DisplaySummary.Mode.class);
                    var name = buffer.readUtf(DisplaySummary.MAX_NAME_LENGTH);
                    int total = buffer.readVarInt(), online = buffer.readVarInt(), attention = buffer.readVarInt(), offline = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > DisplaySummary.MAX_LINES) throw new IllegalArgumentException("Too many display lines");
                    var devices = new ArrayList<DisplaySummary.DeviceLine>(count);
                    for (int i = 0; i < count; i++) devices.add(readDevice(buffer));
                    int widgetCount = buffer.readVarInt();
                    if (widgetCount < 0 || widgetCount > DisplaySummary.MAX_WIDGETS) throw new IllegalArgumentException("Too many display widgets");
                    var widgets = new ArrayList<DisplaySummary.WidgetTile>(widgetCount);
                    for (int i = 0; i < widgetCount; i++) {
                        int x = buffer.readByte(), y = buffer.readByte(), width = buffer.readByte(), height = buffer.readByte();
                        boolean metric = buffer.readBoolean();
                        widgets.add(new DisplaySummary.WidgetTile(x, y, width, height, metric, readDevice(buffer)));
                    }
                    return new Update(dimension, master, new DisplaySummary(mode, name, total, online, attention, offline, devices, widgets));
                });
        public Update {
            Objects.requireNonNull(dimension);
            master = Objects.requireNonNull(master).immutable();
            Objects.requireNonNull(summary);
        }
        @Override public Type<Update> type() { return TYPE; }
    }

    private static void writeDevice(RegistryFriendlyByteBuf buffer, DisplaySummary.DeviceLine device) {
        buffer.writeUtf(device.name(), DisplaySummary.MAX_NAME_LENGTH);
        buffer.writeUtf(device.status(), 16);
        buffer.writeVarInt(device.metrics().size());
        for (var metric : device.metrics()) {
            buffer.writeUtf(metric.name(), DisplaySummary.MAX_NAME_LENGTH);
            buffer.writeUtf(metric.type(), 256);
            buffer.writeUtf(metric.unit(), DisplaySummary.MAX_NAME_LENGTH);
            WireValue.STREAM_CODEC.encode(buffer, metric.value());
        }
    }

    private static DisplaySummary.DeviceLine readDevice(RegistryFriendlyByteBuf buffer) {
        var deviceName = buffer.readUtf(DisplaySummary.MAX_NAME_LENGTH);
        var status = buffer.readUtf(16);
        int metricCount = buffer.readVarInt();
        if (metricCount < 0 || metricCount > DisplaySummary.MAX_METRICS) throw new IllegalArgumentException("Too many display metrics");
        var metrics = new ArrayList<DisplaySummary.MetricLine>(metricCount);
        for (int m = 0; m < metricCount; m++)
            metrics.add(new DisplaySummary.MetricLine(buffer.readUtf(DisplaySummary.MAX_NAME_LENGTH), buffer.readUtf(256),
                    buffer.readUtf(DisplaySummary.MAX_NAME_LENGTH), WireValue.STREAM_CODEC.decode(buffer)));
        return new DisplaySummary.DeviceLine(deviceName, status, metrics);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(Update.TYPE, Update.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (listener != null) listener.accept(packet);
        }));
    }
}

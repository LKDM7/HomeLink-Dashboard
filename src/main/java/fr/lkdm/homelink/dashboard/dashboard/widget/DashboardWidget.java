package fr.lkdm.homelink.dashboard.dashboard.widget;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** A personal widget stores references and grid geometry, never copies device state. */
public record DashboardWidget(UUID id, Type type, UUID deviceId, String metricId, int x, int y, int width, int height) {
    public enum Type { DEVICE_SUMMARY, METRIC }
    public static final int COLUMNS = 12;
    public static final int MAX_ROWS = 64;

    public DashboardWidget {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(metricId);
        if (metricId.length() > 256 || type == Type.METRIC && ResourceLocation.tryParse(metricId) == null
                || type == Type.DEVICE_SUMMARY && !metricId.isEmpty()) throw new IllegalArgumentException("Invalid metric reference");
        if (!supportedSize(width, height) || x < 0 || y < 0 || x > COLUMNS - width || y > MAX_ROWS - height)
            throw new IllegalArgumentException("Widget outside supported grid");
    }

    public static boolean supportedSize(int width, int height) {
        return height == 2 && (width == 3 || width == 4) || height == 3 && (width == 6 || width == 12);
    }

    public boolean overlaps(DashboardWidget other) {
        return x < other.x + other.width && x + width > other.x && y < other.y + other.height && y + height > other.y;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("type", type.name());
        tag.putUUID("device", deviceId);
        tag.putString("metric", metricId);
        tag.putInt("x", x); tag.putInt("y", y); tag.putInt("width", width); tag.putInt("height", height);
        return tag;
    }

    public static DashboardWidget fromTag(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("device") || !tag.contains("type", Tag.TAG_STRING)
                || !tag.contains("metric", Tag.TAG_STRING) || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT)
                || !tag.contains("width", Tag.TAG_INT) || !tag.contains("height", Tag.TAG_INT))
            throw new IllegalArgumentException("Incomplete widget");
        return new DashboardWidget(tag.getUUID("id"), Type.valueOf(tag.getString("type")), tag.getUUID("device"),
                tag.getString("metric"), tag.getInt("x"), tag.getInt("y"), tag.getInt("width"), tag.getInt("height"));
    }
}

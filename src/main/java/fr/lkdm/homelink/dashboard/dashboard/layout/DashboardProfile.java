package fr.lkdm.homelink.dashboard.dashboard.layout;

import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Immutable preferences scoped to one authenticated player and one HomeCore network. */
public record DashboardProfile(List<DashboardWidget> widgets, Set<UUID> favorites) {
    public static final int MAX_WIDGETS = 32;
    public static final int MAX_FAVORITES = 64;
    public static final DashboardProfile EMPTY = new DashboardProfile(List.of(), Set.of());

    public DashboardProfile {
        widgets = List.copyOf(widgets);
        favorites = Set.copyOf(favorites);
        if (widgets.size() > MAX_WIDGETS || favorites.size() > MAX_FAVORITES) throw new IllegalArgumentException("Profile limit exceeded");
        Set<UUID> ids = new HashSet<>();
        for (int index = 0; index < widgets.size(); index++) {
            DashboardWidget widget = widgets.get(index);
            if (!ids.add(widget.id())) throw new IllegalArgumentException("Duplicate widget UUID");
            for (int previous = 0; previous < index; previous++)
                if (widget.overlaps(widgets.get(previous))) throw new IllegalArgumentException("Overlapping widgets");
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag widgetTags = new ListTag();
        widgets.forEach(widget -> widgetTags.add(widget.toTag()));
        tag.put("widgets", widgetTags);
        ListTag favoriteTags = new ListTag();
        favorites.stream().sorted().forEach(id -> { CompoundTag favorite = new CompoundTag(); favorite.putUUID("id", id); favoriteTags.add(favorite); });
        tag.put("favorites", favoriteTags);
        return tag;
    }

    /** Strict decoder for untrusted client edits: any invalid entry rejects the transaction. */
    public static DashboardProfile fromTag(CompoundTag tag) {
        ListTag widgetTags = entries(tag, "widgets", MAX_WIDGETS);
        ListTag favoriteTags = entries(tag, "favorites", MAX_FAVORITES);
        List<DashboardWidget> widgets = new ArrayList<>();
        for (Tag widget : widgetTags) widgets.add(DashboardWidget.fromTag((CompoundTag) widget));
        Set<UUID> favorites = new LinkedHashSet<>();
        for (Tag raw : favoriteTags) {
            CompoundTag favorite = (CompoundTag) raw;
            if (!favorite.hasUUID("id") || !favorites.add(favorite.getUUID("id"))) throw new IllegalArgumentException("Invalid favorite");
        }
        return new DashboardProfile(widgets, favorites);
    }

    private static ListTag entries(CompoundTag tag, String key, int max) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() > max
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid profile list: " + key);
        return list;
    }
}

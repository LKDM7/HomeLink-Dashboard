package fr.lkdm.homelink.dashboard.block;

import net.minecraft.util.StringRepresentable;

public enum AccessPointStatus implements StringRepresentable {
    ONLINE("online"), OFFLINE("offline"), ERROR("error");

    private final String name;
    AccessPointStatus(String name) { this.name = name; }
    @Override public String getSerializedName() { return name; }
}

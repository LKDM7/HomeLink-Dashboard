package fr.lkdm.homelink.dashboard.client.state;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import fr.lkdm.homelink.dashboard.network.PreferencesPayloads;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;

/** Personal preference session, independently acknowledged from HomeCore's live device state. */
public final class DashboardPreferencesClient implements AutoCloseable {
    private final int containerId;
    private final UUID networkId;
    private DashboardProfile profile = DashboardProfile.EMPTY;
    private UUID request;
    private AutoCloseable listener;
    private long sentAt;
    private long revision;
    private String result = "";
    private boolean closed;
    private boolean started;

    public DashboardPreferencesClient(DashboardMenu menu) {
        containerId = menu.containerId;
        networkId = menu.session().networkId().orElseThrow();
    }

    public void start() {
        if (started || closed) return;
        started = true;
        listener = PreferencesPayloads.listen(this::accept);
        send(PreferencesPayloads.Operation.LOAD, new CompoundTag());
    }

    public boolean saveLayout(List<DashboardWidget> widgets) {
        if (!ready()) return false;
        try { return send(PreferencesPayloads.Operation.SAVE_LAYOUT, new DashboardProfile(widgets, profile.favorites()).toTag()); }
        catch (IllegalArgumentException exception) { result = "INVALID_PARAMETER"; revision++; return false; }
    }

    public boolean toggleFavorite(UUID device) {
        if (!ready()) return false;
        CompoundTag data = new CompoundTag();
        data.putUUID("device", device);
        return send(PreferencesPayloads.Operation.TOGGLE_FAVORITE, data);
    }

    private boolean send(PreferencesPayloads.Operation operation, CompoundTag data) {
        if (!started || closed || request != null) return false;
        if (!connected()) { result = "FAILED"; revision++; return false; }
        try {
            request = UUID.randomUUID();
            sentAt = System.nanoTime();
            result = "";
            PacketDistributor.sendToServer(new PreferencesPayloads.Request(request, containerId, networkId, operation, data));
            revision++;
            return true;
        } catch (RuntimeException exception) {
            request = null;
            result = "FAILED";
            revision++;
            return false;
        }
    }

    private void accept(PreferencesPayloads.Response response) {
        if (closed || !response.requestId().equals(request) || response.containerId() != containerId || !response.networkId().equals(networkId)) return;
        request = null;
        result = response.result().name();
        if (response.result() == ActionResult.Code.SUCCESS) { profile = response.profile(); loaded = true; }
        else if (response.result() == ActionResult.Code.DENIED) { profile = DashboardProfile.EMPTY; loaded = false; }
        revision++;
    }

    /** Screen tick performs only timeout/disconnect cleanup, never periodic network polling. */
    public void tick() {
        if (closed) return;
        if (!connected()) { close(); result = "DISCONNECTED"; return; }
        if (request != null && System.nanoTime() - sentAt >= 10_000_000_000L) {
            request = null;
            result = "FAILED";
            revision++;
        }
    }

    private boolean connected() {
        var minecraft = Minecraft.getInstance();
        return minecraft.getConnection() != null && minecraft.player != null && minecraft.player.containerMenu.containerId == containerId;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (listener != null) {
            try { listener.close(); } catch (Exception ignored) { }
            listener = null;
        }
        request = null;
        profile = DashboardProfile.EMPTY;
        revision++;
    }

    public DashboardProfile profile() { return profile; }
    private boolean loaded;
    public boolean ready() { return loaded && !closed; }
    public void refresh() { send(PreferencesPayloads.Operation.LOAD, new CompoundTag()); }
    public boolean pending() { return request != null; }
    public String result() { return result; }
    public long revision() { return revision; }
    public boolean isClosed() { return closed; }
}

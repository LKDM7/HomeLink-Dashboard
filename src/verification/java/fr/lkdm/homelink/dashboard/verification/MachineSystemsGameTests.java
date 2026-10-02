package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.transport.WireValue;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.client.state.MachineSystems;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Machine grouping and summaries use only public device type and metric identifiers. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineSystemsGameTests {
    @GameTest(template = "empty")
    public static void groupsDevicesByModule(GameTestHelper helper) {
        var summaries = MachineSystems.summarize(List.of(
                device("Solar", "homelink_energy:solar_panel", "ONLINE", metric("homelink_energy:generation_rate", 12.5)),
                device("Wind", "homelink_energy:wind_turbine", "WARNING", metric("homelink_energy:current_generation", 7.5)),
                device("Battery", "homelink_energy:battery", "ONLINE", metric("homelink_energy:stored_energy", 2_500L),
                        metric("homelink_energy:capacity", 10_000L)),
                device("Quarry", "homelink_quarry:quarry", "ONLINE", metric("homelink_quarry:status", new WireValue.EnumName("MINING")),
                        metric("homelink_quarry:progress", new Percentage(40)), metric("homelink_quarry:blocks_mined", 1_500L)),
                device("Lamp", "third_party:lamp", "OFFLINE")));
        helper.assertTrue(summaries.keySet().equals(java.util.EnumSet.of(MachineSystems.Group.ENERGY, MachineSystems.Group.FARM,
                        MachineSystems.Group.QUARRY, MachineSystems.Group.OTHER)),
                "Energy, farm and quarry always appear; empty storage stays hidden; OTHER appears with a third-party device");
        helper.assertTrue(MachineSystems.summarize(List.of(device("Chest", "homelink_storage:storage_controller", "ONLINE")))
                        .containsKey(MachineSystems.Group.STORAGE), "Storage must appear once the network holds a controller");
        var energy = summaries.get(MachineSystems.Group.ENERGY);
        helper.assertTrue(energy.total() == 3 && energy.online() == 2 && energy.warning() == 1, "Energy must count its three devices by state");
        helper.assertTrue(energy.devices().getFirst().name().equals("Wind"), "Devices needing attention must be listed first");
        helper.assertTrue(energy.lines().get(0).args().getFirst().equals("20 HE/t"), "Solar and wind production must be summed");
        helper.assertTrue(Math.abs(energy.lines().get(1).fraction() - 0.25) < 1e-9 && energy.lines().get(1).args().get(0).equals("2.5k"),
                "Battery storage must aggregate stored energy over capacity");
        var quarry = summaries.get(MachineSystems.Group.QUARRY);
        helper.assertTrue(quarry.lines().get(0).args().getFirst().equals("1/1"), "A mining quarry must count as active");
        helper.assertTrue(Math.abs(quarry.lines().get(1).fraction() - 0.4) < 1e-9, "Quarry progress must average the public percentage");
        helper.assertTrue(summaries.get(MachineSystems.Group.FARM).total() == 0 && summaries.get(MachineSystems.Group.OTHER).status().equals("OFFLINE"),
                "Empty groups stay visible and unknown devices fall back to OTHER");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void keyMetricsFallBackSafely(GameTestHelper helper) {
        var storage = device("Chest", "homelink_storage:storage_controller", "ONLINE",
                metric("homelink_storage:unique_items", 4), metric("homelink_storage:item_count", 64L), metric("homelink_storage:capacity", new Percentage(50)));
        var keys = MachineSystems.keyMetrics(storage);
        helper.assertTrue(keys.size() == 2 && keys.get(0).id().equals("homelink_storage:capacity") && keys.get(1).id().equals("homelink_storage:item_count"),
                "Known machine types must show their key metrics first");
        var unknown = device("Unknown", "third_party:thing", "ONLINE", metric("third_party:a", 1), metric("third_party:b", 2), metric("third_party:c", 3));
        helper.assertTrue(MachineSystems.keyMetrics(unknown).stream().map(DebugDeviceView.Metric::id).toList().equals(List.of("third_party:a", "third_party:b")),
                "Unknown types must show their first two metrics");
        var broken = device("Broken", "homelink_energy:battery", "ONLINE",
                new DebugDeviceView.Metric("homelink_energy:stored_energy", "Stored", "?", 0, "homecore:long", "HE", null));
        var energy = MachineSystems.summarize(List.of(broken)).get(MachineSystems.Group.ENERGY);
        helper.assertTrue(energy.lines().get(1).fraction() == 0, "Missing values and zero capacity must not break the summary");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hydroProductionExcludesPumpFlow(GameTestHelper helper) {
        var turbine = device("Hydro turbine", "homelink_energy:hydro_turbine", "ONLINE",
                metric("homelink_energy:available_flow", 200.0),
                metric("homelink_energy:hydro_turbine_status", new WireValue.EnumName("GENERATING")),
                metric("homelink_energy:current_generation", 15.5));
        var pump = device("Hydro pump", "homelink_energy:hydro_pump", "ONLINE",
                metric("homelink_energy:hydro_pump_level", 3),
                metric("homelink_energy:hydro_pump_status", new WireValue.EnumName("PUMPING")),
                metric("homelink_energy:available_flow", 100.0),
                metric("homelink_energy:current_generation", 999.0));
        var energy = MachineSystems.summarize(List.of(turbine, pump)).get(MachineSystems.Group.ENERGY);
        helper.assertTrue(energy.total() == 2, "Hydro pumps and turbines belong to Energy");
        helper.assertTrue(energy.lines().getFirst().args().equals(List.of("15.5 HE/t", "1")),
                "Only the turbine produces HE; pump flow and generation-like metrics must never count");
        helper.assertTrue(MachineSystems.keyMetrics(turbine).stream().map(DebugDeviceView.Metric::id).toList()
                        .equals(List.of("homelink_energy:hydro_turbine_status", "homelink_energy:current_generation")),
                "Turbine rows must show status and HE production");
        helper.assertTrue(MachineSystems.keyMetrics(pump).stream().map(DebugDeviceView.Metric::id).toList()
                        .equals(List.of("homelink_energy:hydro_pump_status", "homelink_energy:available_flow")),
                "Pump rows must show status and hydraulic flow");
        helper.succeed();
    }

    private static DebugDeviceView device(String name, String type, String status, DebugDeviceView.Metric... metrics) {
        return new DebugDeviceView(UUID.nameUUIDFromBytes(name.getBytes()), name, type, status, List.of(metrics));
    }

    private static DebugDeviceView.Metric metric(String id, Object value) {
        return new DebugDeviceView.Metric(id, id, String.valueOf(value), 1, "homecore:unknown", "", WireValue.from(value));
    }
}

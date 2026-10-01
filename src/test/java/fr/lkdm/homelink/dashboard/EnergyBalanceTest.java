package fr.lkdm.homelink.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.lkdm.homelink.dashboard.dashboard.widget.EnergyBalance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnergyBalanceTest {
    private record Device(String type, Map<String, Double> metrics) implements EnergyBalance.Source {
        @Override public double number(String metric) { return metrics.getOrDefault(metric, 0.0); }
    }

    private static Device battery(double consumption, double batteries, double stored) {
        return new Device("homelink_energy:battery", Map.of(
                "homelink_energy:stored_energy", stored, "homelink_energy:capacity", 1000.0,
                "homelink_energy:network_consumption", consumption, "homelink_energy:network_batteries", batteries));
    }

    @Test void sumsProducersAndCountsEachCableNetworkOnce() {
        var result = EnergyBalance.of(List.of(
                new Device("homelink_energy:solar_panel", Map.of("homelink_energy:generation_rate", 12.0)),
                new Device("homelink_energy:wind_turbine", Map.of("homelink_energy:current_generation", 8.0)),
                battery(5, 2, 100), battery(5, 2, 300)));
        assertEquals(20, result.production());
        assertEquals(5, result.consumption(), "two batteries of one cable network report the same consumption");
        assertEquals(15, result.net());
        assertEquals(400, result.stored());
        assertEquals(0.2, result.charge(), 1e-9);
        assertEquals(2, result.producers());
        assertEquals(2, result.batteries());
    }

    @Test void identicalTotalsFromSeparateNetworksAreBothCounted() {
        var result = EnergyBalance.of(List.of(battery(4, 1, 0), battery(4, 1, 0)));
        assertEquals(8, result.consumption());
    }

    @Test void withoutBatteryChargeIsUnknownAndOtherDevicesAreIgnored() {
        var result = EnergyBalance.of(List.of(new Device("homelink_farm:pump", Map.of())));
        assertEquals(EnergyBalance.Result.EMPTY, result);
        assertEquals(-1, result.charge());
    }
}

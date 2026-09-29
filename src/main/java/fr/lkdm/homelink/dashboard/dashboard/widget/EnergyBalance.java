package fr.lkdm.homelink.dashboard.dashboard.widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Energy balance of a HomeNetwork, derived from the public metrics of HomeLink Energy devices only: the
 * dashboard keeps no compile-time dependency on that mod. The client computes it from watched devices and the
 * server from registered devices, with the same rules.
 *
 * <p>Production adds the current output of every solar panel and wind turbine. Consumption comes from the
 * batteries, which measure what their cable network delivers to consumers. Batteries of the same cable network
 * report identical figures, so samples are grouped using those figures and the reported battery count. Without a
 * cable-network identifier, separate networks with identical figures and only some batteries visible can still
 * be grouped together. Consumers without a battery on their cable network are not measured.
 */
public final class EnergyBalance {
    private static final String NAMESPACE = "homelink_energy:";
    private static final List<String> CABLE_NETWORK = List.of("network_production", "network_consumption", "network_stored",
            "network_capacity", "network_producers", "network_consumers", "network_batteries");

    /** One device as seen by the caller. */
    public interface Source {
        /** @return the device type, such as {@code homelink_energy:battery} */
        String type();
        /** @param metric metric identifier
         *  @return its numeric value, or 0 when absent or not a number */
        double number(String metric);
    }

    /**
     * @param production HE/t produced now
     * @param consumption HE/t delivered to consumers now
     * @param stored HE stored in batteries
     * @param capacity HE battery capacity
     * @param producers solar panels and wind turbines
     * @param batteries batteries
     */
    public record Result(double production, double consumption, long stored, long capacity, int producers, int batteries) {
        public static final Result EMPTY = new Result(0, 0, 0, 0, 0, 0);
        /** @return production minus consumption, in HE/t */
        public double net() { return production - consumption; }
        /** @return stored share of the capacity in [0, 1], or -1 without battery */
        public double charge() { return capacity <= 0 ? -1 : Math.max(0, Math.min(1, (double) stored / capacity)); }
    }

    private EnergyBalance() { }

    public static Result of(Iterable<? extends Source> devices) {
        double production = 0, consumption = 0;
        long stored = 0, capacity = 0;
        int producers = 0, batteries = 0;
        Map<List<Double>, Integer> cableNetworkSamples = new HashMap<>();
        for (Source device : devices) {
            switch (device.type()) {
                case NAMESPACE + "solar_panel" -> { producers++; production += device.number(NAMESPACE + "generation_rate"); }
                case NAMESPACE + "wind_turbine" -> { producers++; production += device.number(NAMESPACE + "current_generation"); }
                case NAMESPACE + "battery" -> {
                    batteries++;
                    stored += (long) device.number(NAMESPACE + "stored_energy");
                    capacity += (long) device.number(NAMESPACE + "capacity");
                    List<Double> key = CABLE_NETWORK.stream().map(metric -> device.number(NAMESPACE + metric)).toList();
                    int sample = cableNetworkSamples.merge(key, 1, Integer::sum);
                    int reportedBatteries = Math.max(1, (int) device.number(NAMESPACE + "network_batteries"));
                    // One cable network reports the same totals from each of its batteries. A second network may
                    // have identical totals, so start another group once the first network's batteries are counted.
                    if ((sample - 1) % reportedBatteries == 0) consumption += device.number(NAMESPACE + "network_consumption");
                }
                default -> { }
            }
        }
        return new Result(finite(production), finite(consumption), stored, capacity, producers, batteries);
    }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0; }
}

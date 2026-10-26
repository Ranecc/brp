package dev.brp.mod.spi;

/**
 * Machine telemetry (SCADA plane). Create kinetic blocks report rpm/SU;
 * AE2 devices report energy and crafting CPU load (docs/02 sections 2-3).
 */
public interface MachineProbe extends BrpAdapter {

    String machineId();

    record MachineStats(
            String machineId,
            String kind,          // "create:kinetic" | "ae2:device" | "brp:farm" ...
            double rpm,           // Create rotation speed, 0 when absent
            double su,            // Create stress units used, 0 when absent
            double loadFactor,    // normalized 0..1 utilization for dashboards
            boolean stalled,      // jammed / no-input flag -> on-call alert
            long epochMs) {}

    MachineStats sample();
}

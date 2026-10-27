package dev.brp.mod.line;

import java.util.List;

/**
 * A Line Controller's program (docs/09 section 5): ordered steps, each
 * gated by a condition. Conditional boot = every condition holds; a
 * governor HALT overrides everything upstream (the gate outranks the plan).
 * Pure Java, no Minecraft imports — the evaluator runs on the agent's own
 * clock, game threads only read the verdict.
 */
public record PipelineProgram(List<Step> steps) {

    public record Step(String operation, Condition condition) {}

    public sealed interface Condition permits EnergyAvailable, QuotaRemaining, PriceWithin, TimeWindow {
        default boolean satisfiedBy(Inputs in) {
            return switch (this) {
                case EnergyAvailable(var min) -> in.energy() >= min;
                case QuotaRemaining(var min) -> in.quotaRemaining() >= min;
                case PriceWithin(var min, var max) -> in.spotPrice() >= min && in.spotPrice() <= max;
                case TimeWindow(var open, var close) -> in.tick() >= open && in.tick() < close;
            };
        }
    }

    /** Conditional boot conditions (docs/09 section 5), all local, all cheap. */
    public record EnergyAvailable(double min) implements Condition {}
    public record QuotaRemaining(long min) implements Condition {}
    public record PriceWithin(double min, double max) implements Condition {}
    public record TimeWindow(long openTick, long closeTick) implements Condition {}

    /** What the controller can see without asking anyone: cached backend state. */
    public record Inputs(double energy, long quotaRemaining, double spotPrice, long tick) {}

    public PipelineProgram {
        steps = List.copyOf(steps);
    }

    /** @return true when every step's condition holds — the machine may boot. */
    public boolean bootAllowed(Inputs in) {
        return steps.stream().allMatch(s -> s.condition().satisfiedBy(in));
    }

    /** @return the operations whose conditions block boot; empty means go. */
    public List<String> blockedBy(Inputs in) {
        return steps.stream()
                .filter(s -> !s.condition().satisfiedBy(in))
                .map(Step::operation)
                .toList();
    }
}

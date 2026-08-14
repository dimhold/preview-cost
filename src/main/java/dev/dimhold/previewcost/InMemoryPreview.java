package dev.dimhold.previewcost;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy B. Load the model once, then apply each slider's overrides to a copy
 * in memory and compute from that. The database is never touched again.
 *
 * <p>Nothing is written and nothing is locked. The cost is elsewhere: this path
 * only stays correct while its view of the model matches what the real read
 * path returns, and that has to be held true by hand, forever.
 */
final class InMemoryPreview implements Preview {

    private final Map<String, Double> base;
    private final int months;
    private final int plans;

    InMemoryPreview(Map<String, Double> base, int months, int plans) {
        this.base = Map.copyOf(base);
        this.months = months;
        this.plans = plans;
    }

    @Override
    public Calculator.Result preview(Map<String, Double> overrides) {
        Map<String, Double> inputs = new HashMap<>(base);
        inputs.putAll(overrides);
        return Calculator.run(inputs, months, plans);
    }
}

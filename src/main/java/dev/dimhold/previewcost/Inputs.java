package dev.dimhold.previewcost;

import java.util.LinkedHashMap;
import java.util.Map;

/** The model's inputs: one flat map, which is all the calculator needs. */
final class Inputs {

    static Map<String, Double> seed(int plans) {
        Map<String, Double> in = new LinkedHashMap<>();
        in.put("starting_cash", 900_000d);
        in.put("fixed_burn", 65_000d);
        for (int p = 0; p < plans; p++) {
            in.put("price_" + p, 29d + p * 17);
            in.put("growth_" + p, 0.04 + (p % 5) * 0.01);
            in.put("churn_" + p, 0.012 + (p % 4) * 0.006);
            in.put("subs_" + p, 120d + p * 9);
        }
        return in;
    }

    /** What a slider drag sends: a handful of changed fields, nothing else. */
    static Map<String, Double> overrides(int plans, double growthDelta) {
        Map<String, Double> o = new LinkedHashMap<>();
        for (int p = 0; p < Math.min(3, plans); p++) {
            o.put("growth_" + p, 0.04 + (p % 5) * 0.01 + growthDelta);
        }
        o.put("fixed_burn", 65_000d + growthDelta * 100_000);
        return o;
    }
}

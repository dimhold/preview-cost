package dev.dimhold.previewcost;

import java.util.Map;

/**
 * The heavy part, and the whole reason the question exists.
 *
 * <p>A month-by-month projection over a horizon, for every plan, carrying four
 * interdependent series: subscribers, MRR, cash and a cohort tail. Each month
 * depends on the previous one, so it cannot be vectorised away or cached by
 * key: change one input and the entire grid is different.
 *
 * <p>It is deliberately pure — inputs in, result out, no I/O. Both preview
 * strategies call this exact method, so the comparison measures how the inputs
 * were produced and nothing else.
 */
final class Calculator {

    record Result(double endingMrr, double endingCash, double lifetimeRevenue, int runwayMonth) {}

    static Result run(Map<String, Double> in, int months, int plans) {
        double cash = in.get("starting_cash");
        double fixedBurn = in.get("fixed_burn");
        double lifetimeRevenue = 0;
        double endingMrr = 0;
        int runwayMonth = -1;

        for (int plan = 0; plan < plans; plan++) {
            double price = in.get("price_" + plan);
            double growth = in.get("growth_" + plan);
            double churn = in.get("churn_" + plan);
            double subs = in.get("subs_" + plan);

            // Cohort tail: each month's intake decays on its own curve, so the
            // month's revenue is a sum over every cohort alive so far.
            double[] cohorts = new double[months];

            for (int m = 0; m < months; m++) {
                double intake = subs * growth;
                cohorts[m] = intake;
                subs += intake;

                double active = 0;
                for (int c = 0; c <= m; c++) {
                    cohorts[c] *= (1 - churn);
                    active += cohorts[c];
                }

                double mrr = active * price;
                lifetimeRevenue += mrr;
                if (m == months - 1) endingMrr += mrr;
            }
        }

        for (int m = 0; m < months; m++) {
            cash += (lifetimeRevenue / months) - fixedBurn;
            if (cash < 0 && runwayMonth < 0) runwayMonth = m + 1;
        }

        return new Result(endingMrr, cash, lifetimeRevenue, runwayMonth);
    }
}

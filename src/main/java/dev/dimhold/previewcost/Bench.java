package dev.dimhold.previewcost;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Measures what one preview costs, both ways, well enough to quote.
 *
 * <pre>
 *   gradle run                                          # H2, 5 forks
 *   gradle run --args="--url jdbc:postgresql://localhost:5432/bench --plans 40"
 * </pre>
 *
 * <p>Three things make these numbers worth publishing, and the first version of
 * this file had none of them.
 *
 * <p><b>Paired.</b> Within one iteration both strategies get the same overrides
 * and run back to back, so every measurement has a partner taken under the same
 * machine conditions. The statistic is the distribution of per-pair differences,
 * not two independent samples compared by eye.
 *
 * <p><b>Interleaved.</b> The order flips at random every iteration. Measuring A
 * fully and then B confounds the comparison with anything that drifts during the
 * run: thermal state, background load, and the JIT, which by the end of A's
 * block has already compiled the calculator the second strategy would then be
 * credited for.
 *
 * <p><b>Forked.</b> Each configuration runs in several fresh JVMs. Three hundred
 * samples inside one process describe that process, not the measurement: on this
 * machine the same command's median moved by most of its own value between two
 * runs. Per-fork medians are printed so that spread stays visible instead of
 * being averaged into one confident-looking number.
 *
 * <p>The interval on the median difference is a 10,000-resample percentile
 * bootstrap over the pairs, which assumes nothing about the shape of the
 * distribution. It is still not JMH: no blackhole, no dead-code elimination
 * guard beyond accumulating every result.
 */
public final class Bench {

    private static final int BOOTSTRAP = 10_000;

    public static void main(String[] args) throws Exception {
        var opt = Options.parse(args);
        if (opt.fork > 0) {
            parent(opt);
        } else {
            child(opt);
        }
    }

    // --- parent: spawn forks, aggregate, report --------------------------------

    private static void parent(Options opt) throws Exception {
        System.out.printf(Locale.ROOT, "months=%d plans=%d previews=%d warmup=%d forks=%d%nurl=%s%n%n",
                opt.months, opt.plans, opt.previews, opt.warmup, opt.fork, opt.url);

        var allA = new ArrayList<Long>();
        var allB = new ArrayList<Long>();
        var forkMedianA = new double[opt.fork];
        var forkMedianB = new double[opt.fork];

        for (int f = 0; f < opt.fork; f++) {
            var pairs = runFork(opt, f);
            var a = new ArrayList<Long>(pairs.size());
            var b = new ArrayList<Long>(pairs.size());
            for (var p : pairs) {
                a.add(p[0]);
                b.add(p[1]);
            }
            allA.addAll(a);
            allB.addAll(b);
            forkMedianA[f] = medianMs(a);
            forkMedianB[f] = medianMs(b);
            System.out.printf(Locale.ROOT,
                    "  fork %d: rollback p50 %6.2f ms   in-memory p50 %6.2f ms   (%d pairs)%n",
                    f + 1, forkMedianA[f], forkMedianB[f], pairs.size());
        }

        System.out.println();
        report("tx rollback", allA, forkMedianA);
        report("in memory  ", allB, forkMedianB);

        // Paired differences: same overrides, same iteration, same machine state.
        long[] diff = new long[allA.size()];
        for (int i = 0; i < diff.length; i++) diff[i] = allA.get(i) - allB.get(i);
        var ci = bootstrapMedianCi(diff);
        long slower = Arrays.stream(diff).filter(d -> d > 0).count();
        System.out.printf(Locale.ROOT,
                "%npaired difference (rollback minus in-memory), n=%d%n"
                        + "  median %.2f ms   95%% CI [%.2f, %.2f]   rollback slower in %.1f%% of pairs%n",
                diff.length, ns(median(diff)), ns(ci[0]), ns(ci[1]), 100.0 * slower / diff.length);
    }

    private static List<long[]> runFork(Options opt, int index) throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var cmd = List.of(
                java, "-cp", System.getProperty("java.class.path"), Bench.class.getName(),
                "--fork", "0",
                "--url", opt.url,
                "--months", String.valueOf(opt.months),
                "--plans", String.valueOf(opt.plans),
                "--previews", String.valueOf(opt.previews),
                "--warmup", String.valueOf(opt.warmup),
                "--seed", String.valueOf(index));
        var process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        var pairs = new ArrayList<long[]>();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("PAIR ")) {
                    var parts = line.substring(5).split(" ");
                    pairs.add(new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1])});
                } else if (!line.isBlank()) {
                    System.out.println("  [fork] " + line);
                }
            }
        }
        if (process.waitFor() != 0) throw new IllegalStateException("fork " + index + " failed");
        return pairs;
    }

    // --- child: one JVM, one configuration, paired and interleaved -------------

    private static void child(Options opt) throws SQLException {
        var random = new Random(opt.seed);
        try (var store = new Store(opt.url)) {
            store.createSchema();
            store.seed(Inputs.seed(opt.plans));

            Preview rollback = new TxRollbackPreview(store, opt.months, opt.plans);
            Preview inMemory = new InMemoryPreview(store.load(), opt.months, opt.plans);

            double sink = 0;
            for (int i = 0; i < opt.warmup; i++) {
                var overrides = Inputs.overrides(opt.plans, 0.001 * i);
                sink += rollback.preview(overrides).endingMrr();
                sink += inMemory.preview(overrides).endingMrr();
            }

            var out = new StringBuilder();
            for (int i = 0; i < opt.previews; i++) {
                var overrides = Inputs.overrides(opt.plans, 0.001 * i);
                long a;
                long b;
                if (random.nextBoolean()) {
                    a = time(rollback, overrides);
                    b = time(inMemory, overrides);
                } else {
                    b = time(inMemory, overrides);
                    a = time(rollback, overrides);
                }
                out.append("PAIR ").append(a).append(' ').append(b).append('\n');
            }
            if (sink == 0) throw new IllegalStateException("unreachable, keeps the JIT honest");
            System.out.print(out);
        }
    }

    private static long time(Preview preview, Map<String, Double> overrides) throws SQLException {
        long t0 = System.nanoTime();
        var result = preview.preview(overrides);
        long elapsed = System.nanoTime() - t0;
        if (result.endingMrr() == Double.MIN_VALUE) throw new IllegalStateException("never");
        return elapsed;
    }

    // --- statistics ------------------------------------------------------------

    private static void report(String label, List<Long> pooled, double[] forkMedians) {
        long[] sorted = pooled.stream().mapToLong(Long::longValue).sorted().toArray();
        var spread = Arrays.stream(forkMedians).summaryStatistics();
        System.out.printf(Locale.ROOT,
                "%s  pooled p50 %6.2f   p95 %6.2f   p99 %6.2f ms      fork medians %.2f-%.2f ms%n",
                label, ns(quantile(sorted, 0.50)), ns(quantile(sorted, 0.95)),
                ns(quantile(sorted, 0.99)), spread.getMin(), spread.getMax());
    }

    /** Percentile bootstrap on the median of the paired differences. */
    private static long[] bootstrapMedianCi(long[] diff) {
        var random = new Random(42);
        var medians = new long[BOOTSTRAP];
        var sample = new long[diff.length];
        for (int b = 0; b < BOOTSTRAP; b++) {
            for (int i = 0; i < diff.length; i++) sample[i] = diff[random.nextInt(diff.length)];
            Arrays.sort(sample);
            medians[b] = quantile(sample, 0.5);
        }
        Arrays.sort(medians);
        return new long[] {quantile(medians, 0.025), quantile(medians, 0.975)};
    }

    private static long median(long[] values) {
        long[] copy = values.clone();
        Arrays.sort(copy);
        return quantile(copy, 0.5);
    }

    private static long quantile(long[] sorted, double q) {
        int i = Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(q * sorted.length) - 1));
        return sorted[i];
    }

    private static double medianMs(List<Long> values) {
        return ns(median(values.stream().mapToLong(Long::longValue).toArray()));
    }

    private static double ns(long nanos) {
        return nanos / 1_000_000d;
    }

    record Options(String url, int months, int plans, int previews, int warmup, int fork, long seed) {
        static Options parse(String[] args) {
            var a = List.of(args);
            return new Options(
                    value(a, "--url", "jdbc:h2:mem:bench;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"),
                    Integer.parseInt(value(a, "--months", "60")),
                    Integer.parseInt(value(a, "--plans", "40")),
                    Integer.parseInt(value(a, "--previews", "300")),
                    Integer.parseInt(value(a, "--warmup", "80")),
                    Integer.parseInt(value(a, "--fork", "5")),
                    Long.parseLong(value(a, "--seed", "0")));
        }

        private static String value(List<String> args, String flag, String fallback) {
            int i = args.indexOf(flag);
            return i >= 0 && i + 1 < args.size() ? args.get(i + 1) : fallback;
        }
    }
}

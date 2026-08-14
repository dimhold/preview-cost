package dev.dimhold.previewcost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * The test that carries the whole argument.
 *
 * <p>Strategy B is only worth having while it returns exactly what strategy A
 * returns. This asserts that, and it is the test somebody has to keep green for
 * the life of the product — which is the real price of the second engine, and
 * the reason the first one exists.
 */
class PreviewsAgreeTest {

    private static final int MONTHS = 60;
    private static final int PLANS = 12;

    @Test
    void bothStrategiesReturnTheSameNumbers() throws SQLException {
        try (var store = new Store("jdbc:h2:mem:agree;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")) {
            store.createSchema();
            store.seed(Inputs.seed(PLANS));

            var rollback = new TxRollbackPreview(store, MONTHS, PLANS);
            var inMemory = new InMemoryPreview(store.load(), MONTHS, PLANS);

            for (double delta : new double[] {0.0, 0.005, 0.02, 0.05}) {
                var overrides = Inputs.overrides(PLANS, delta);
                assertEquals(rollback.preview(overrides), inMemory.preview(overrides),
                        "strategies diverged at delta " + delta);
            }
        }
    }

    @Test
    void overridesActuallyChangeTheAnswer() throws SQLException {
        try (var store = new Store("jdbc:h2:mem:changes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")) {
            store.createSchema();
            store.seed(Inputs.seed(PLANS));
            var inMemory = new InMemoryPreview(store.load(), MONTHS, PLANS);

            assertNotEquals(inMemory.preview(Inputs.overrides(PLANS, 0.0)),
                    inMemory.preview(Inputs.overrides(PLANS, 0.05)),
                    "a preview that ignores its overrides would make the benchmark meaningless");
        }
    }

    @Test
    void theRollbackLeavesNothingBehind() throws SQLException {
        try (var store = new Store("jdbc:h2:mem:clean;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")) {
            store.createSchema();
            store.seed(Inputs.seed(PLANS));
            double before = store.load().get("fixed_burn");

            new TxRollbackPreview(store, MONTHS, PLANS).preview(Inputs.overrides(PLANS, 0.05));

            assertEquals(before, store.load().get("fixed_burn"),
                    "the preview persisted, which is the one thing it must never do");
            try (Statement s = store.connection().createStatement()) {
                s.execute("SELECT 1");
            }
        }
    }
}

package dev.dimhold.previewcost;

import java.sql.SQLException;
import java.util.Map;

/**
 * Strategy A. Write the overrides through the normal write path, read the model
 * back the way production reads it, compute, then roll the transaction back.
 *
 * <p>One engine, one read path, nothing to keep in sync. Every preview writes
 * rows it is about to throw away, and holds the locks on them until it does.
 */
final class TxRollbackPreview implements Preview {

    private final Store store;
    private final int months;
    private final int plans;

    TxRollbackPreview(Store store, int months, int plans) {
        this.store = store;
        this.months = months;
        this.plans = plans;
    }

    @Override
    public Calculator.Result preview(Map<String, Double> overrides) throws SQLException {
        var connection = store.connection();
        connection.setAutoCommit(false);
        try {
            store.applyOverrides(overrides);
            var inputs = store.load();
            return Calculator.run(inputs, months, plans);
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }
}

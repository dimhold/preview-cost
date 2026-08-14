package dev.dimhold.previewcost;

import java.sql.SQLException;
import java.util.Map;

/** One preview of the model with a slider's worth of overrides applied. */
interface Preview {
    Calculator.Result preview(Map<String, Double> overrides) throws SQLException;
}

package local.myhome.marketresearch.analysis.cohort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Explicit same-horizon scenarios, not revenue predictions derived from engagement counters. */
public final class ExperimentEconomics {
    private ExperimentEconomics() {}
    public record Costs(BigDecimal research, BigDecimal aiApi, BigDecimal compute,
                        BigDecimal publicationAndFees, BigDecimal otherOperations,
                        BigDecimal manualMinutes, BigDecimal manualHourlyValue) {}
    public record Evaluation(BigDecimal operatingCost, BigDecimal expectedReceipts,
                             BigDecimal expectedSurplus, BigDecimal receiptsPerCost) {}
    public static Evaluation evaluate(Costs costs, BigDecimal expectedReceipts) {
        var amounts = java.util.Arrays.asList(costs.research(),costs.aiApi(),costs.compute(),costs.publicationAndFees(),
            costs.otherOperations(),costs.manualMinutes(),costs.manualHourlyValue());
        if (amounts.stream().filter(java.util.Objects::nonNull).anyMatch(x -> x.signum() < 0)
                || expectedReceipts != null && expectedReceipts.signum() < 0)
            throw new IllegalArgumentException("Costs and receipts must be nonnegative");
        if (amounts.stream().anyMatch(java.util.Objects::isNull))
            return new Evaluation(null, expectedReceipts, null, null);
        BigDecimal labor = costs.manualMinutes().multiply(costs.manualHourlyValue())
            .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
        BigDecimal total = costs.research().add(costs.aiApi()).add(costs.compute())
            .add(costs.publicationAndFees()).add(costs.otherOperations()).add(labor);
        return new Evaluation(total, expectedReceipts,
            expectedReceipts == null ? null : expectedReceipts.subtract(total),
            expectedReceipts == null || total.signum() == 0 ? null
                : expectedReceipts.divide(total, 8, RoundingMode.HALF_UP));
    }
}

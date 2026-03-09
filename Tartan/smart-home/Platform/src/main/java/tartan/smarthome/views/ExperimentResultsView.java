package tartan.smarthome.views;

import io.dropwizard.views.common.View;

import java.util.List;

/**
 * View for the AB experiment results page (variant assignment and outcome comparison).
 */
public class ExperimentResultsView extends View {

    private final List<HouseExperimentRow> houseRows;
    private final double usageOnlyAvgMs;
    private final double costEstimateAvgMs;
    private final String conclusion;
    private final String configFilePath;

    public ExperimentResultsView(List<HouseExperimentRow> houseRows,
                                 double usageOnlyAvgMs,
                                 double costEstimateAvgMs,
                                 String conclusion,
                                 String configFilePath) {
        super("experimentResults.ftl");
        this.houseRows = houseRows;
        this.usageOnlyAvgMs = usageOnlyAvgMs;
        this.costEstimateAvgMs = costEstimateAvgMs;
        this.conclusion = conclusion;
        this.configFilePath = configFilePath;
    }

    public List<HouseExperimentRow> getHouseRows() {
        return houseRows;
    }

    public double getUsageOnlyAvgMs() {
        return usageOnlyAvgMs;
    }

    public double getCostEstimateAvgMs() {
        return costEstimateAvgMs;
    }

    public String getConclusion() {
        return conclusion;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    /**
     * One row in the experiment table: one house's variant and outcome.
     */
    public static class HouseExperimentRow {
        private final String houseName;
        private final String variant;
        private final int snapshotCount;
        private final long totalLightsOnMs;
        private final double avgLightsOnMs;

        public HouseExperimentRow(String houseName, String variant, int snapshotCount,
                                  long totalLightsOnMs, double avgLightsOnMs) {
            this.houseName = houseName;
            this.variant = variant != null ? variant : "usage_only";
            this.snapshotCount = snapshotCount;
            this.totalLightsOnMs = totalLightsOnMs;
            this.avgLightsOnMs = avgLightsOnMs;
        }

        public String getHouseName() { return houseName; }
        public String getVariant() { return variant; }
        public int getSnapshotCount() { return snapshotCount; }
        public long getTotalLightsOnMs() { return totalLightsOnMs; }
        public double getAvgLightsOnMs() { return avgLightsOnMs; }
    }
}

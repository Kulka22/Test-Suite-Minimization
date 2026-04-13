package minimizer.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class HeuristicResult {
    private final MinimizerAlgorithm algorithm;
    private final String algorithmName;
    private final List<String> selectedTestIds;
    private final double coveragePercent;
    private final long solveNanos;

    HeuristicResult(
        MinimizerAlgorithm algorithm,
        String algorithmName,
        List<String> selectedTestIds,
        double coveragePercent,
        long solveNanos
    ) {
        this.algorithm = algorithm;
        this.algorithmName = algorithmName;
        this.selectedTestIds = Collections.unmodifiableList(new ArrayList<>(selectedTestIds));
        this.coveragePercent = coveragePercent;
        this.solveNanos = solveNanos;
    }

    MinimizerAlgorithm algorithm() {
        return algorithm;
    }

    String algorithmName() {
        return algorithmName;
    }

    List<String> selectedTestIds() {
        return selectedTestIds;
    }

    double coveragePercent() {
        return coveragePercent;
    }

    long solveNanos() {
        return solveNanos;
    }
}

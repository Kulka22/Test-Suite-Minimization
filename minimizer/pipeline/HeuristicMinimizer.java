package minimizer.pipeline;

import minimizer.coverage.CoverageMatrix;
import ru.erofeev.fl.algorithm.GeneticAlgorithm;
import ru.erofeev.fl.algorithm.GreedyEssentialAlgorithm;
import ru.erofeev.fl.algorithm.NaiveAlgorithm;
import ru.erofeev.fl.algorithm.TestSuiteMinimizationAlgorithm;
import ru.erofeev.fl.model.MinimizationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class HeuristicMinimizer {
    HeuristicResult minimize(CoverageMatrix matrix, MinimizerAlgorithm algorithm) {
        TestSuiteMinimizationAlgorithm delegate = createDelegate(algorithm);
        ru.erofeev.fl.model.CoverageMatrix adapted = new ru.erofeev.fl.model.CoverageMatrix(matrix);

        long fallbackStart = System.nanoTime();
        MinimizationResult rawResult = delegate.run(adapted);
        long fallbackNanos = System.nanoTime() - fallbackStart;

        List<Integer> selectedIndices = new ArrayList<>(rawResult.selectedTests());
        Collections.sort(selectedIndices);

        List<String> selectedIds = new ArrayList<>(selectedIndices.size());
        for (Integer testIndex : selectedIndices) {
            if (testIndex == null || testIndex < 0 || testIndex >= matrix.testCount()) {
                continue;
            }
            selectedIds.add(matrix.tests().get(testIndex).uniqueTestId());
        }

        long solveNanos = rawResult.executionTimeNanos() > 0 ? rawResult.executionTimeNanos() : fallbackNanos;
        return new HeuristicResult(
            algorithm,
            rawResult.algorithmName(),
            selectedIds,
            rawResult.coverage(),
            solveNanos
        );
    }

    private static TestSuiteMinimizationAlgorithm createDelegate(MinimizerAlgorithm algorithm) {
        switch (algorithm) {
            case NAIVE:
                return new NaiveAlgorithm();
            case GREEDY_ESSENTIAL:
                return new GreedyEssentialAlgorithm();
            case GENETIC:
                return new GeneticAlgorithm();
            default:
                throw new IllegalArgumentException("Unsupported heuristic algorithm: " + algorithm);
        }
    }
}

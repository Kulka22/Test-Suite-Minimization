package ru.erofeev.fl.algorithm;

import ru.erofeev.fl.model.CoverageMatrix;
import ru.erofeev.fl.model.MinimizationResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GreedyEssentialAlgorithm implements TestSuiteMinimizationAlgorithm {

    @Override
    public String getName() {
        return "Greedy Essential";
    }

    @Override
    public MinimizationResult run(CoverageMatrix matrix) {
        long start = System.nanoTime();

        Set<Integer> selected = new LinkedHashSet<>();
        Set<Integer> uncovered = matrix.getAllRequirements();

        for (int r = 0; r < matrix.getNumRequirements(); r++) {
            Set<Integer> coveringTests = matrix.getTestsCoveringRequirement(r);
            if (coveringTests.size() == 1) {
                int essentialTest = coveringTests.iterator().next();
                selected.add(essentialTest);
            }
        }

        for (int t : selected) {
            uncovered.removeAll(matrix.getRequirementsCoveredByTest(t));
        }

        Set<Integer> remainingTests = new LinkedHashSet<>();
        for (int t = 0; t < matrix.getNumTests(); t++) {
            if (!selected.contains(t)) {
                remainingTests.add(t);
            }
        }

        while (!uncovered.isEmpty()) {
            int bestTest = -1;
            int bestGain = 0;

            for (int t : remainingTests) {
                Set<Integer> covered = matrix.getRequirementsCoveredByTest(t);
                int gain = 0;
                for (int r : covered) {
                    if (uncovered.contains(r)) {
                        gain++;
                    }
                }

                if (gain > bestGain) {
                    bestGain = gain;
                    bestTest = t;
                }
            }

            if (bestTest == -1 || bestGain == 0) {
                break;
            }

            selected.add(bestTest);
            uncovered.removeAll(matrix.getRequirementsCoveredByTest(bestTest));
            remainingTests.remove(bestTest);
        }

        long end = System.nanoTime();
        List<String> names = new ArrayList<>();
        for (int t : selected) {
            names.add(matrix.getTestName(t));
        }

        return new MinimizationResult(
                getName(),
                selected,
                names,
                matrix.coverageOf(selected),
                end - start
        );
    }
}
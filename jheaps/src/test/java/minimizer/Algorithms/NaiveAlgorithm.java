package ru.erofeev.fl.algorithm;

import ru.erofeev.fl.model.CoverageMatrix;
import ru.erofeev.fl.model.MinimizationResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NaiveAlgorithm implements TestSuiteMinimizationAlgorithm {

    @Override
    public String getName() {
        return "Naive";
    }

    @Override
    public MinimizationResult run(CoverageMatrix matrix) {
        long start = System.nanoTime();

        Set<Integer> selected = new LinkedHashSet<>();
        for (int t = 0; t < matrix.getNumTests(); t++) {
            selected.add(t);
        }

        for (int t = 0; t < matrix.getNumTests(); t++) {
            selected.remove(t);
            if (!matrix.coversAllRequirements(selected)) {
                selected.add(t);
            }
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

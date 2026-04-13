package ru.erofeev.fl.algorithm;

import ru.erofeev.fl.model.CoverageMatrix;
import ru.erofeev.fl.model.MinimizationResult;

public interface TestSuiteMinimizationAlgorithm {
    String getName();
    MinimizationResult run(CoverageMatrix matrix);
}
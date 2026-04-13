package ru.erofeev.fl.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MinimizationResult {
    private final String algorithmName;
    private final Set<Integer> selectedTests;
    private final List<String> selectedTestNames;
    private final double coverage;
    private final long executionTimeNanos;

    public MinimizationResult(
        String algorithmName,
        Set<Integer> selectedTests,
        List<String> selectedTestNames,
        double coverage,
        long executionTimeNanos
    ) {
        this.algorithmName = algorithmName;
        this.selectedTests = Collections.unmodifiableSet(new LinkedHashSet<>(selectedTests));
        this.selectedTestNames = Collections.unmodifiableList(new ArrayList<>(selectedTestNames));
        this.coverage = coverage;
        this.executionTimeNanos = executionTimeNanos;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public Set<Integer> getSelectedTests() {
        return selectedTests;
    }

    public List<String> getSelectedTestNames() {
        return selectedTestNames;
    }

    public double getCoverage() {
        return coverage;
    }

    public long getExecutionTimeNanos() {
        return executionTimeNanos;
    }

    public String algorithmName() {
        return algorithmName;
    }

    public Set<Integer> selectedTests() {
        return selectedTests;
    }

    public List<String> selectedTestNames() {
        return selectedTestNames;
    }

    public double coverage() {
        return coverage;
    }

    public long executionTimeNanos() {
        return executionTimeNanos;
    }
}

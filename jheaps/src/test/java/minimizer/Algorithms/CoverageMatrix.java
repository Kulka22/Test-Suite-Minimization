package ru.erofeev.fl.model;

import minimizer.coverage.TestRunRecord;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CoverageMatrix {
    private final minimizer.coverage.CoverageMatrix delegate;
    private final List<Set<Integer>> requirementsCoveredByTest;
    private final List<Set<Integer>> testsCoveringRequirement;
    private final Set<Integer> allRequirements;

    public CoverageMatrix(minimizer.coverage.CoverageMatrix delegate) {
        this.delegate = delegate;
        this.requirementsCoveredByTest = buildRequirementsCoveredByTest(delegate);
        this.testsCoveringRequirement = buildTestsCoveringRequirement(delegate);
        this.allRequirements = buildAllRequirements(delegate.elementCount());
    }

    public int getNumTests() {
        return delegate.testCount();
    }

    public int getNumRequirements() {
        return delegate.elementCount();
    }

    public String getTestName(int testIndex) {
        TestRunRecord record = delegate.tests().get(testIndex);
        return record.testName();
    }

    public Set<Integer> getAllRequirements() {
        return new LinkedHashSet<>(allRequirements);
    }

    public Set<Integer> getTestsCoveringRequirement(int requirementIndex) {
        return new LinkedHashSet<>(testsCoveringRequirement.get(requirementIndex));
    }

    public Set<Integer> getRequirementsCoveredByTest(int testIndex) {
        return new LinkedHashSet<>(requirementsCoveredByTest.get(testIndex));
    }

    public boolean coversAllRequirements(Set<Integer> selectedTests) {
        if (getNumRequirements() == 0) {
            return true;
        }
        BitSet covered = collectCoveredRequirements(selectedTests);
        return covered.cardinality() == getNumRequirements();
    }

    public double coverageOf(Set<Integer> selectedTests) {
        if (getNumRequirements() == 0) {
            return 100.0d;
        }
        BitSet covered = collectCoveredRequirements(selectedTests);
        return (covered.cardinality() * 100.0d) / getNumRequirements();
    }

    private BitSet collectCoveredRequirements(Set<Integer> selectedTests) {
        BitSet covered = new BitSet(getNumRequirements());
        for (Integer testIndex : selectedTests) {
            if (testIndex == null || testIndex < 0 || testIndex >= getNumTests()) {
                continue;
            }
            covered.or(delegate.coverageForTest(testIndex));
        }
        return covered;
    }

    private static List<Set<Integer>> buildRequirementsCoveredByTest(minimizer.coverage.CoverageMatrix delegate) {
        List<Set<Integer>> result = new ArrayList<>(delegate.testCount());
        for (int testIndex = 0; testIndex < delegate.testCount(); testIndex++) {
            BitSet coverage = delegate.coverageForTest(testIndex);
            Set<Integer> coveredRequirements = new LinkedHashSet<>();
            for (int requirement = coverage.nextSetBit(0); requirement >= 0; requirement = coverage.nextSetBit(requirement + 1)) {
                coveredRequirements.add(requirement);
            }
            result.add(Collections.unmodifiableSet(coveredRequirements));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Set<Integer>> buildTestsCoveringRequirement(minimizer.coverage.CoverageMatrix delegate) {
        List<Set<Integer>> result = new ArrayList<>(delegate.elementCount());
        for (int requirement = 0; requirement < delegate.elementCount(); requirement++) {
            Set<Integer> tests = new LinkedHashSet<>(delegate.testsCoveringElement(requirement));
            result.add(Collections.unmodifiableSet(tests));
        }
        return Collections.unmodifiableList(result);
    }

    private static Set<Integer> buildAllRequirements(int requirementCount) {
        Set<Integer> result = new LinkedHashSet<>();
        for (int requirement = 0; requirement < requirementCount; requirement++) {
            result.add(requirement);
        }
        return Collections.unmodifiableSet(result);
    }
}

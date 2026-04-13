package minimizer.coverage;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

public final class CoverageMatrix {
    private final List<TestRunRecord> tests;
    private final List<String> elements;
    private final List<BitSet> testCoverage;
    private final List<List<Integer>> elementToTests;

    public CoverageMatrix(List<TestRunRecord> tests, List<String> elements, List<BitSet> testCoverage, List<List<Integer>> elementToTests) {
        this.tests = Collections.unmodifiableList(new ArrayList<>(tests));
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
        this.testCoverage = Collections.unmodifiableList(cloneBitSets(testCoverage));
        this.elementToTests = Collections.unmodifiableList(copyLists(elementToTests));
    }

    public List<TestRunRecord> tests() {
        return tests;
    }

    public List<String> elements() {
        return elements;
    }

    public int testCount() {
        return tests.size();
    }

    public int elementCount() {
        return elements.size();
    }

    public BitSet coverageForTest(int index) {
        return (BitSet) testCoverage.get(index).clone();
    }

    public List<Integer> testsCoveringElement(int elementIndex) {
        return elementToTests.get(elementIndex);
    }

    private static List<BitSet> cloneBitSets(List<BitSet> source) {
        List<BitSet> copy = new ArrayList<>(source.size());
        for (BitSet bitSet : source) {
            copy.add((BitSet) bitSet.clone());
        }
        return copy;
    }

    private static List<List<Integer>> copyLists(List<List<Integer>> source) {
        List<List<Integer>> copy = new ArrayList<>(source.size());
        for (List<Integer> list : source) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(list)));
        }
        return copy;
    }
}

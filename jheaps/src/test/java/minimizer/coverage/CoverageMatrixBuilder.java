package minimizer.coverage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class CoverageMatrixBuilder {
    public CoverageMatrix build(Path testsCsv, Path classesDir, CoverageMetric metric, boolean includeOnlySuccessful) throws IOException {
        List<TestRunRecord> allRecords = TestsCsv.read(testsCsv);
        CoverageAnalyzer analyzer = new CoverageAnalyzer(classesDir, metric);

        List<TestRunRecord> tests = new ArrayList<>();
        List<Set<String>> perTestCoverage = new ArrayList<>();
        Set<String> universe = new TreeSet<>();

        for (TestRunRecord record : allRecords) {
            if (includeOnlySuccessful && record.status() != TestStatus.SUCCESS) {
                continue;
            }
            Path execPath = record.resolveExecPath(testsCsv);
            Set<String> covered = analyzer.analyzeExec(execPath);
            tests.add(record);
            perTestCoverage.add(covered);
            universe.addAll(covered);
        }

        List<String> elements = new ArrayList<>(universe);
        Map<String, Integer> elementIndex = new HashMap<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            elementIndex.put(elements.get(i), i);
        }

        List<BitSet> testCoverage = new ArrayList<>(tests.size());
        for (Set<String> covered : perTestCoverage) {
            BitSet bitSet = new BitSet(elements.size());
            for (String element : covered) {
                Integer index = elementIndex.get(element);
                if (index != null) {
                    bitSet.set(index);
                }
            }
            testCoverage.add(bitSet);
        }

        List<List<Integer>> elementToTests = new ArrayList<>(elements.size());
        for (int element = 0; element < elements.size(); element++) {
            List<Integer> coveringTests = new ArrayList<>();
            for (int test = 0; test < testCoverage.size(); test++) {
                if (testCoverage.get(test).get(element)) {
                    coveringTests.add(test);
                }
            }
            elementToTests.add(coveringTests);
        }

        return new CoverageMatrix(tests, elements, testCoverage, elementToTests);
    }
}

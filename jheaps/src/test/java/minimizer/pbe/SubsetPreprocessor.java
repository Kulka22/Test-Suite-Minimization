package minimizer.pbe;

import minimizer.coverage.CoverageMatrix;

import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.Set;

final class SubsetPreprocessor {
    private SubsetPreprocessor() {
    }

    static Set<Integer> filterCandidates(CoverageMatrix matrix) {
        Set<Integer> active = new LinkedHashSet<>();
        for (int i = 0; i < matrix.testCount(); i++) {
            active.add(i);
        }

        for (int i = 0; i < matrix.testCount(); i++) {
            if (!active.contains(i)) {
                continue;
            }
            for (int j = i + 1; j < matrix.testCount(); j++) {
                if (!active.contains(j)) {
                    continue;
                }
                BitSet iCoverage = matrix.coverageForTest(i);
                BitSet jCoverage = matrix.coverageForTest(j);

                if (isSubset(iCoverage, jCoverage)) {
                    active.remove(i);
                    break;
                }
                if (isSubset(jCoverage, iCoverage)) {
                    active.remove(j);
                }
            }
        }
        return active;
    }

    private static boolean isSubset(BitSet left, BitSet right) {
        BitSet diff = (BitSet) left.clone();
        diff.andNot(right);
        return diff.isEmpty();
    }
}

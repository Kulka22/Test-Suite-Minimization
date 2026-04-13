package minimizer.coverage;

import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.IMethodCoverage;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

final class CoverageElementExtractor {
    private CoverageElementExtractor() {
    }

    static Set<String> extract(Collection<IClassCoverage> classes, CoverageMetric metric) {
        Set<String> elements = new TreeSet<>();
        for (IClassCoverage classCoverage : classes) {
            String className = classCoverage.getName().replace('/', '.');
            if (metric == CoverageMetric.METHOD) {
                addCoveredMethods(elements, classCoverage, className);
            } else {
                addCoveredLines(elements, classCoverage, className);
            }
        }
        return elements;
    }

    private static void addCoveredLines(Set<String> elements, IClassCoverage classCoverage, String className) {
        int first = classCoverage.getFirstLine();
        int last = classCoverage.getLastLine();
        if (first == IClassCoverage.UNKNOWN_LINE || last == IClassCoverage.UNKNOWN_LINE) {
            return;
        }
        for (int line = first; line <= last; line++) {
            int status = classCoverage.getLine(line).getStatus();
            if (isCovered(status)) {
                elements.add(className + ":" + line);
            }
        }
    }

    private static void addCoveredMethods(Set<String> elements, IClassCoverage classCoverage, String className) {
        for (IMethodCoverage method : classCoverage.getMethods()) {
            if (method.getMethodCounter().getCoveredCount() > 0) {
                elements.add(className + "#" + method.getName() + method.getDesc());
            }
        }
    }

    private static boolean isCovered(int status) {
        return status == ICounter.PARTLY_COVERED || status == ICounter.FULLY_COVERED;
    }
}

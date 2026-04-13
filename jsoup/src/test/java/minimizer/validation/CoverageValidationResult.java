package minimizer.validation;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public final class CoverageValidationResult {
    private final boolean coveragePreserved;
    private final int baselineElementCount;
    private final int selectedElementCount;
    private final Set<String> missingElements;

    public CoverageValidationResult(boolean coveragePreserved, int baselineElementCount, int selectedElementCount, Set<String> missingElements) {
        this.coveragePreserved = coveragePreserved;
        this.baselineElementCount = baselineElementCount;
        this.selectedElementCount = selectedElementCount;
        this.missingElements = Collections.unmodifiableSet(new TreeSet<>(missingElements));
    }

    public boolean coveragePreserved() {
        return coveragePreserved;
    }

    public int baselineElementCount() {
        return baselineElementCount;
    }

    public int selectedElementCount() {
        return selectedElementCount;
    }

    public int missingElementsCount() {
        return missingElements.size();
    }

    public Set<String> missingElements() {
        return missingElements;
    }
}

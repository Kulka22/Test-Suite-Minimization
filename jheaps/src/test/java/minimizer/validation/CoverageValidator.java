package minimizer.validation;

import java.util.HashSet;
import java.util.Set;

public final class CoverageValidator {
    public CoverageValidationResult validate(Set<String> baselineCoveredElements, Set<String> selectedCoveredElements) {
        Set<String> missing = new HashSet<>(baselineCoveredElements);
        missing.removeAll(selectedCoveredElements);
        boolean preserved = missing.isEmpty();
        return new CoverageValidationResult(
            preserved,
            baselineCoveredElements.size(),
            selectedCoveredElements.size(),
            missing
        );
    }
}

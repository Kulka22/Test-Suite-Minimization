package minimizer.lp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LpResult {
    private final List<String> selectedTestIds;
    private final int essentialCount;
    private final int solverCount;
    private final int preprocessedAwayCount;
    private final int emptyTestsRemovedCount;
    private final int candidateTestsCount;
    private final int remainingTestsForSolverCount;
    private final int remainingElementsForSolverCount;
    private final int constraintsBeforeReductionCount;
    private final int constraintsAfterReductionCount;

    public LpResult(
        List<String> selectedTestIds,
        int essentialCount,
        int solverCount,
        int preprocessedAwayCount,
        int emptyTestsRemovedCount,
        int candidateTestsCount,
        int remainingTestsForSolverCount,
        int remainingElementsForSolverCount,
        int constraintsBeforeReductionCount,
        int constraintsAfterReductionCount
    ) {
        this.selectedTestIds = Collections.unmodifiableList(new ArrayList<>(selectedTestIds));
        this.essentialCount = essentialCount;
        this.solverCount = solverCount;
        this.preprocessedAwayCount = preprocessedAwayCount;
        this.emptyTestsRemovedCount = emptyTestsRemovedCount;
        this.candidateTestsCount = candidateTestsCount;
        this.remainingTestsForSolverCount = remainingTestsForSolverCount;
        this.remainingElementsForSolverCount = remainingElementsForSolverCount;
        this.constraintsBeforeReductionCount = constraintsBeforeReductionCount;
        this.constraintsAfterReductionCount = constraintsAfterReductionCount;
    }

    public List<String> selectedTestIds() {
        return selectedTestIds;
    }

    public int essentialCount() {
        return essentialCount;
    }

    public int solverCount() {
        return solverCount;
    }

    public int preprocessedAwayCount() {
        return preprocessedAwayCount;
    }

    public int emptyTestsRemovedCount() {
        return emptyTestsRemovedCount;
    }

    public int candidateTestsCount() {
        return candidateTestsCount;
    }

    public int remainingTestsForSolverCount() {
        return remainingTestsForSolverCount;
    }

    public int remainingElementsForSolverCount() {
        return remainingElementsForSolverCount;
    }

    public int constraintsBeforeReductionCount() {
        return constraintsBeforeReductionCount;
    }

    public int constraintsAfterReductionCount() {
        return constraintsAfterReductionCount;
    }
}

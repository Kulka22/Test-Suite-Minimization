package minimizer.lp;

import minimizer.coverage.CoverageMatrix;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

public final class LpMinimizer {
    public LpResult minimize(CoverageMatrix matrix, LpConfig config) {
        CandidateInitialization candidates = initializeCandidates(matrix, config.enableSubsetPreprocessing());
        int preprocessedAway = matrix.testCount() - candidates.activeCandidates().size();

        EssentialExtraction essentialExtraction = extractEssential(matrix, candidates.activeCandidates());
        Set<Integer> essentialTests = essentialExtraction.essentialTests();
        List<Integer> remainingElements = essentialExtraction.remainingElements();
        Set<Integer> remainingTests = essentialExtraction.remainingTests();

        SolverSelection solverSelection = SolverSelection.empty();
        if (!remainingElements.isEmpty()) {
            solverSelection = solveRemaining(matrix, remainingTests, remainingElements, config.solverTimeoutSeconds(), config.enableConstraintReduction());
        }

        Set<Integer> finalSelection = new LinkedHashSet<>(essentialTests);
        finalSelection.addAll(solverSelection.selectedTests());

        List<Integer> sorted = new ArrayList<>(finalSelection);
        Collections.sort(sorted);

        List<String> selectedTestIds = new ArrayList<>(sorted.size());
        for (Integer index : sorted) {
            selectedTestIds.add(matrix.tests().get(index).uniqueTestId());
        }

        return new LpResult(
            selectedTestIds,
            essentialTests.size(),
            solverSelection.selectedTests().size(),
            preprocessedAway,
            candidates.emptyTestsRemovedCount(),
            candidates.activeCandidates().size(),
            remainingTests.size(),
            remainingElements.size(),
            solverSelection.constraintsBeforeReduction(),
            solverSelection.constraintsAfterReduction()
        );
    }

    private CandidateInitialization initializeCandidates(CoverageMatrix matrix, boolean withSubsetPreprocessing) {
        Set<Integer> active = new LinkedHashSet<>();
        int emptyTestsRemoved = 0;
        for (int i = 0; i < matrix.testCount(); i++) {
            if (matrix.coverageForTest(i).isEmpty()) {
                emptyTestsRemoved++;
            } else {
                active.add(i);
            }
        }
        if (withSubsetPreprocessing) {
            active = filterSubsetCandidates(matrix, active);
        }
        return new CandidateInitialization(active, emptyTestsRemoved);
    }

    private Set<Integer> filterSubsetCandidates(CoverageMatrix matrix, Set<Integer> candidates) {
        Set<Integer> active = new LinkedHashSet<>(candidates);
        List<Integer> ordered = new ArrayList<>(active);
        Collections.sort(ordered);

        for (int i = 0; i < ordered.size(); i++) {
            Integer left = ordered.get(i);
            if (!active.contains(left)) {
                continue;
            }
            for (int j = i + 1; j < ordered.size(); j++) {
                Integer right = ordered.get(j);
                if (!active.contains(right)) {
                    continue;
                }
                BitSet leftCoverage = matrix.coverageForTest(left);
                BitSet rightCoverage = matrix.coverageForTest(right);
                if (isSubset(leftCoverage, rightCoverage)) {
                    active.remove(left);
                    break;
                }
                if (isSubset(rightCoverage, leftCoverage)) {
                    active.remove(right);
                }
            }
        }
        return active;
    }

    private EssentialExtraction extractEssential(CoverageMatrix matrix, Set<Integer> candidates) {
        Set<Integer> remainingTests = new LinkedHashSet<>(candidates);
        List<Integer> remainingElements = new ArrayList<>();
        for (int i = 0; i < matrix.elementCount(); i++) {
            remainingElements.add(i);
        }
        Set<Integer> essential = new LinkedHashSet<>();

        boolean changed;
        do {
            changed = false;
            Set<Integer> newlyEssential = new LinkedHashSet<>();
            for (Integer element : remainingElements) {
                Integer uniqueCoveringTest = null;
                for (Integer test : matrix.testsCoveringElement(element)) {
                    if (!remainingTests.contains(test)) {
                        continue;
                    }
                    if (uniqueCoveringTest != null) {
                        uniqueCoveringTest = null;
                        break;
                    }
                    uniqueCoveringTest = test;
                }
                if (uniqueCoveringTest != null) {
                    newlyEssential.add(uniqueCoveringTest);
                }
            }
            if (!newlyEssential.isEmpty()) {
                changed = true;
                essential.addAll(newlyEssential);
                remainingTests.removeAll(newlyEssential);
                BitSet coveredByNewEssential = new BitSet(matrix.elementCount());
                for (Integer test : newlyEssential) {
                    coveredByNewEssential.or(matrix.coverageForTest(test));
                }
                List<Integer> stillUncovered = new ArrayList<>();
                for (Integer element : remainingElements) {
                    if (!coveredByNewEssential.get(element)) {
                        stillUncovered.add(element);
                    }
                }
                remainingElements = stillUncovered;
            }
        } while (changed);

        return new EssentialExtraction(essential, remainingTests, remainingElements);
    }

    private SolverSelection solveRemaining(
        CoverageMatrix matrix,
        Set<Integer> remainingTests,
        List<Integer> remainingElements,
        int timeoutSeconds,
        boolean withConstraintReduction
    ) {
        List<Integer> tests = new ArrayList<>(remainingTests);
        Collections.sort(tests);
        Map<Integer, Integer> testIndexToVar = new HashMap<>();
        for (int i = 0; i < tests.size(); i++) {
            testIndexToVar.put(tests.get(i), i);
        }

        List<BitSet> constraints = buildConstraintSupports(matrix, remainingElements, testIndexToVar);
        int constraintsBeforeReduction = constraints.size();
        if (withConstraintReduction) {
            constraints = reduceConstraints(constraints);
        }
        int constraintsAfterReduction = constraints.size();

        Set<Integer> selectedVarIndexes = solveWithMip(constraints, tests.size(), timeoutSeconds);
        Set<Integer> selectedTests = new LinkedHashSet<>();
        for (Integer varIndex : selectedVarIndexes) {
            selectedTests.add(tests.get(varIndex));
        }

        return new SolverSelection(selectedTests, constraintsBeforeReduction, constraintsAfterReduction);
    }

    private List<BitSet> buildConstraintSupports(
        CoverageMatrix matrix,
        List<Integer> remainingElements,
        Map<Integer, Integer> testIndexToVar
    ) {
        List<BitSet> constraints = new ArrayList<>(remainingElements.size());
        for (Integer element : remainingElements) {
            BitSet support = new BitSet(testIndexToVar.size());
            for (Integer testIndex : matrix.testsCoveringElement(element)) {
                Integer var = testIndexToVar.get(testIndex);
                if (var != null) {
                    support.set(var);
                }
            }
            if (support.isEmpty()) {
                throw new IllegalStateException("Uncovered element encountered in LP model for element index: " + element);
            }
            constraints.add(support);
        }
        return constraints;
    }

    private List<BitSet> reduceConstraints(List<BitSet> constraints) {
        if (constraints.size() <= 1) {
            return constraints;
        }

        Map<String, BitSet> unique = new HashMap<>();
        for (BitSet constraint : constraints) {
            BitSet copy = (BitSet) constraint.clone();
            unique.put(toKey(copy), copy);
        }

        List<BitSet> deduped = new ArrayList<>(unique.values());
        deduped.sort((left, right) -> {
            int cardinalityCompare = Integer.compare(left.cardinality(), right.cardinality());
            if (cardinalityCompare != 0) {
                return cardinalityCompare;
            }
            return Integer.compare(left.length(), right.length());
        });

        List<BitSet> reduced = new ArrayList<>();
        for (BitSet candidate : deduped) {
            boolean redundant = false;
            for (BitSet kept : reduced) {
                if (isSubset(kept, candidate)) {
                    redundant = true;
                    break;
                }
            }
            if (!redundant) {
                reduced.add(candidate);
            }
        }
        return reduced;
    }

    private Set<Integer> solveWithMip(List<BitSet> constraints, int varCount, int timeoutSeconds) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        List<Variable> variables = new ArrayList<>(varCount);
        for (int i = 0; i < varCount; i++) {
            Variable variable = Variable.make("x" + i).binary().weight(BigDecimal.ONE);
            model.addVariable(variable);
            variables.add(variable);
        }

        for (int row = 0; row < constraints.size(); row++) {
            BitSet support = constraints.get(row);
            Expression expression = model.addExpression("cover_" + row).lower(BigDecimal.ONE);
            for (int var = support.nextSetBit(0); var >= 0; var = support.nextSetBit(var + 1)) {
                expression.set(variables.get(var), BigDecimal.ONE);
            }
        }

        Optimisation.Result result = solveWithTimeout(model, timeoutSeconds);
        if (!result.getState().isFeasible()) {
            throw new IllegalStateException("LP solver failed to find feasible solution: " + result.getState());
        }

        Set<Integer> selected = new LinkedHashSet<>();
        for (int i = 0; i < varCount; i++) {
            double value = result.doubleValue(i);
            if (value >= 0.5d) {
                selected.add(i);
            }
        }
        return selected;
    }

    private Optimisation.Result solveWithTimeout(ExpressionsBasedModel model, int timeoutSeconds) {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "lp-mip-solver");
            thread.setDaemon(true);
            return thread;
        };
        ExecutorService executor = Executors.newSingleThreadExecutor(factory);
        try {
            Callable<Optimisation.Result> task = model::minimise;
            Future<Optimisation.Result> future = executor.submit(task);
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                "LP solver timeout after " + timeoutSeconds + "s. " +
                "Try increasing -Dminimizer.lp.timeoutSeconds or use -Dminimizer.metric=METHOD.",
                e
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("LP solver failed.", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LP solver interrupted.", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static String toKey(BitSet bitSet) {
        return Arrays.toString(bitSet.toLongArray());
    }

    private static boolean isSubset(BitSet left, BitSet right) {
        BitSet diff = (BitSet) left.clone();
        diff.andNot(right);
        return diff.isEmpty();
    }

    private static final class CandidateInitialization {
        private final Set<Integer> activeCandidates;
        private final int emptyTestsRemovedCount;

        private CandidateInitialization(Set<Integer> activeCandidates, int emptyTestsRemovedCount) {
            this.activeCandidates = activeCandidates;
            this.emptyTestsRemovedCount = emptyTestsRemovedCount;
        }

        private Set<Integer> activeCandidates() {
            return activeCandidates;
        }

        private int emptyTestsRemovedCount() {
            return emptyTestsRemovedCount;
        }
    }

    private static final class EssentialExtraction {
        private final Set<Integer> essentialTests;
        private final Set<Integer> remainingTests;
        private final List<Integer> remainingElements;

        private EssentialExtraction(Set<Integer> essentialTests, Set<Integer> remainingTests, List<Integer> remainingElements) {
            this.essentialTests = essentialTests;
            this.remainingTests = remainingTests;
            this.remainingElements = remainingElements;
        }

        private Set<Integer> essentialTests() {
            return essentialTests;
        }

        private Set<Integer> remainingTests() {
            return remainingTests;
        }

        private List<Integer> remainingElements() {
            return remainingElements;
        }
    }

    private static final class SolverSelection {
        private final Set<Integer> selectedTests;
        private final int constraintsBeforeReduction;
        private final int constraintsAfterReduction;

        private SolverSelection(Set<Integer> selectedTests, int constraintsBeforeReduction, int constraintsAfterReduction) {
            this.selectedTests = selectedTests;
            this.constraintsBeforeReduction = constraintsBeforeReduction;
            this.constraintsAfterReduction = constraintsAfterReduction;
        }

        private static SolverSelection empty() {
            return new SolverSelection(Collections.<Integer>emptySet(), 0, 0);
        }

        private Set<Integer> selectedTests() {
            return selectedTests;
        }

        private int constraintsBeforeReduction() {
            return constraintsBeforeReduction;
        }

        private int constraintsAfterReduction() {
            return constraintsAfterReduction;
        }
    }
}

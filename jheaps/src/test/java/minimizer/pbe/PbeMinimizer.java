package minimizer.pbe;

import minimizer.coverage.CoverageMatrix;
import org.sat4j.core.Vec;
import org.sat4j.core.VecInt;
import org.sat4j.pb.*;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;

import java.math.BigInteger;
import java.util.*;

public final class PbeMinimizer {
    public PbeResult minimize(CoverageMatrix matrix, PbeConfig config) {
        Set<Integer> activeCandidates = initializeCandidates(matrix, config.enableSubsetPreprocessing());
        int preprocessedAway = matrix.testCount() - activeCandidates.size();

        EssentialExtraction essentialExtraction = extractEssential(matrix, activeCandidates);
        Set<Integer> essentialTests = essentialExtraction.essentialTests();
        List<Integer> remainingElements = essentialExtraction.remainingElements();
        Set<Integer> remainingTests = essentialExtraction.remainingTests();

        SolverSelection solverSelection = SolverSelection.empty();
        if (!remainingElements.isEmpty()) {
            solverSelection = solveRemaining(matrix, remainingTests, remainingElements, config);
        }

        Set<Integer> finalSelection = new LinkedHashSet<>(essentialTests);
        finalSelection.addAll(solverSelection.selectedTests());

        List<Integer> sorted = new ArrayList<>(finalSelection);
        Collections.sort(sorted);

        List<String> selectedTestIds = new ArrayList<>(sorted.size());
        for (Integer index : sorted) {
            selectedTestIds.add(matrix.tests().get(index).uniqueTestId());
        }

        return new PbeResult(
            selectedTestIds,
            essentialTests.size(),
            solverSelection.selectedTests().size(),
            preprocessedAway,
            activeCandidates.size(),
            remainingTests.size(),
            remainingElements.size(),
            solverSelection.constraintsBeforeReduction(),
            solverSelection.constraintsAfterReduction()
        );
    }

    private Set<Integer> initializeCandidates(CoverageMatrix matrix, boolean withSubsetPreprocessing) {
        if (withSubsetPreprocessing) {
            return SubsetPreprocessor.filterCandidates(matrix);
        }
        Set<Integer> all = new LinkedHashSet<>();
        for (int i = 0; i < matrix.testCount(); i++) {
            all.add(i);
        }
        return all;
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
        PbeConfig config
    ) {
        List<Integer> tests = new ArrayList<>(remainingTests);
        Collections.sort(tests);
        Map<Integer, Integer> testIndexToVar = new HashMap<>();
        for (int i = 0; i < tests.size(); i++) {
            testIndexToVar.put(tests.get(i), i + 1);
        }

        List<BitSet> constraints = buildConstraintSupports(matrix, remainingElements, testIndexToVar);
        int constraintsBeforeReduction = constraints.size();
        if (config.enableConstraintReduction()) {
            constraints = reduceConstraints(constraints);
        }
        int constraintsAfterReduction = constraints.size();
        int[] bestModel = solveWithOptimization(constraints, testIndexToVar.size(), config.solverTimeoutSeconds());

        Set<Integer> selected = new LinkedHashSet<>();
        for (int literal : bestModel) {
            if (literal > 0 && literal <= tests.size()) {
                Integer testIndex = tests.get(literal - 1);
                selected.add(testIndex);
            }
        }
        return new SolverSelection(selected, constraintsBeforeReduction, constraintsAfterReduction);
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
                    support.set(var - 1);
                }
            }
            if (support.isEmpty()) {
                throw new IllegalStateException("Uncovered element encountered in PBE model for element index: " + element);
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

    private static String toKey(BitSet bitSet) {
        return Arrays.toString(bitSet.toLongArray());
    }

    private static boolean isSubset(BitSet left, BitSet right) {
        BitSet diff = (BitSet) left.clone();
        diff.andNot(right);
        return diff.isEmpty();
    }

    private int[] solveWithOptimization(
        List<BitSet> constraints,
        int varCount,
        int timeoutSeconds
    ) {
        IPBSolver baseSolver = SolverFactory.newDefaultOptimizer();
        PseudoOptDecorator optimizer = new PseudoOptDecorator(baseSolver);
        optimizer.newVar(varCount);
        optimizer.setTimeout(timeoutSeconds);

        try {
            for (BitSet support : constraints) {
                VecInt clause = new VecInt();
                for (int var = support.nextSetBit(0); var >= 0; var = support.nextSetBit(var + 1)) {
                    clause.push(var + 1);
                }
                if (clause.isEmpty()) {
                    throw new IllegalStateException("Uncovered element encountered in reduced PBE constraint set.");
                }
                optimizer.addClause(clause);
            }

            VecInt objectiveVars = new VecInt();
            Vec<BigInteger> objectiveCoefficients = new Vec<>();
            for (int var = 1; var <= varCount; var++) {
                objectiveVars.push(var);
                objectiveCoefficients.push(BigInteger.ONE);
            }
            optimizer.setObjectiveFunction(new ObjectiveFunction(objectiveVars, objectiveCoefficients));

            OptToPBSATAdapter adapter = new OptToPBSATAdapter(optimizer);
            if (adapter.isSatisfiable()) {
                return adapter.model();
            }
            throw new IllegalStateException("PBE solver: no satisfiable model for remaining coverage.");
        } catch (ContradictionException e) {
            throw new IllegalStateException("PBE solver contradiction while building model.", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                "PBE solver timeout after " + timeoutSeconds + "s. " +
                "Try increasing -Dminimizer.pbe.timeoutSeconds or use -Dminimizer.metric=METHOD.",
                e
            );
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

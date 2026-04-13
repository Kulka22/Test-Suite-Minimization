package ru.erofeev.fl.algorithm;

import ru.erofeev.fl.model.CoverageMatrix;
import ru.erofeev.fl.model.MinimizationResult;

import java.util.*;

public class GeneticAlgorithm implements TestSuiteMinimizationAlgorithm {

    private final int    populationSize  = 16;
    private final int    iterations      = 48;
    private final int    tournamentSize  = 3;
    private final double elitismRate     = 0.45;
    private final double mutationRate    = 0.01;
    private final double crossoverRate   = 0.20;
    private final int    numRuns         = 5;
    private final double initSelectProb  = 0.20;

    @Override
    public String getName() {
        return "GeneticAlgorithm";
    }

    @Override
    public MinimizationResult run(CoverageMatrix matrix) {
        long start = System.nanoTime();

        // === Step 1: Essential cases strategy (ATSM Section 3.3.4) ===
        Set<Integer> essential = new LinkedHashSet<>();
        Set<Integer> coveredByEssential = new HashSet<>();

        for (int r = 0; r < matrix.getNumRequirements(); r++) {
            Set<Integer> covering = matrix.getTestsCoveringRequirement(r);
            if (covering.size() == 1) {
                essential.add(covering.iterator().next());
            }
        }
        for (int t : essential) {
            coveredByEssential.addAll(matrix.getRequirementsCoveredByTest(t));
        }

        Set<Integer> remainingReqs = new LinkedHashSet<>(matrix.getAllRequirements());
        remainingReqs.removeAll(coveredByEssential);

        List<Integer> candidates = new ArrayList<>();
        for (int t = 0; t < matrix.getNumTests(); t++) {
            if (!essential.contains(t)) candidates.add(t);
        }

        Set<Integer> bestSelected = new LinkedHashSet<>(essential);

        if (!remainingReqs.isEmpty() && !candidates.isEmpty()) {
            // === Step 2: несколько независимых запусков GA, каждый со своим seed ===
            boolean[] bestBits = null;
            double bestFitness = Double.MAX_VALUE;

            for (int run = 0; run < numRuns; run++) {
                // Разный seed для каждого run — обеспечивает статистическое разнообразие
                // как рекомендовано в ATSM (best of 15 runs)
                Random runRandom = new Random(42L + run);
                boolean[] runBest = runGA(candidates, matrix, remainingReqs, runRandom);
                double runFitness = fitness(runBest, candidates, matrix, remainingReqs);
                if (runFitness < bestFitness) {
                    bestFitness = runFitness;
                    bestBits = Arrays.copyOf(runBest, runBest.length);
                }
            }

            if (bestBits != null) {
                for (int i = 0; i < bestBits.length; i++) {
                    if (bestBits[i]) bestSelected.add(candidates.get(i));
                }
            }
        } else if (!remainingReqs.isEmpty()) {
            // Нет кандидатов, но требования не покрыты — включаем всех
            bestSelected.addAll(candidates);
        }
        // else: essential покрывают всё — GA не нужен

        long end = System.nanoTime();

        List<String> names = new ArrayList<>();
        for (int t : bestSelected) names.add(matrix.getTestName(t));

        return new MinimizationResult(getName(), bestSelected, names,
                matrix.coverageOf(bestSelected), end - start);
    }

    private boolean[] runGA(List<Integer> candidates, CoverageMatrix matrix,
                             Set<Integer> remainingReqs, Random random) {
        int n = candidates.size();

        // Инициализация популяции
        List<boolean[]> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            boolean[] chromosome = new boolean[n];
            for (int j = 0; j < n; j++) {
                chromosome[j] = random.nextDouble() < initSelectProb;
            }
            repair(chromosome, candidates, matrix, remainingReqs);
            population.add(chromosome);
        }

        boolean[] best = null;
        double bestFitness = Double.MAX_VALUE;

        for (int iter = 0; iter < iterations; iter++) {

            // Оценка fitness
            double[] scores = new double[populationSize];
            for (int i = 0; i < populationSize; i++) {
                scores[i] = fitness(population.get(i), candidates, matrix, remainingReqs);
                if (scores[i] < bestFitness) {
                    bestFitness = scores[i];
                    best = Arrays.copyOf(population.get(i), n);
                }
            }

            // Элитизм: лучшие elitismRate% переходят без изменений (ATSM GA-1 = 45%)
            int eliteCount = Math.max(1, (int) Math.round(elitismRate * populationSize));
            Integer[] idx = new Integer[populationSize];
            for (int i = 0; i < populationSize; i++) idx[i] = i;
            Arrays.sort(idx, Comparator.comparingDouble(i -> scores[i]));

            List<boolean[]> newPopulation = new ArrayList<>();
            for (int i = 0; i < eliteCount; i++) {
                newPopulation.add(Arrays.copyOf(population.get(idx[i]), n));
            }

            // Генерация потомков
            while (newPopulation.size() < populationSize) {
                boolean[] p1 = tournament(population, scores, random);
                boolean[] p2 = tournament(population, scores, random);

                boolean[] c1 = Arrays.copyOf(p1, n);
                boolean[] c2 = Arrays.copyOf(p2, n);

                // Одноточечный кроссовер (защита от n <= 1)
                if (n > 1 && random.nextDouble() < crossoverRate) {
                    int point = 1 + random.nextInt(n - 1);
                    for (int i = point; i < n; i++) {
                        boolean tmp = c1[i]; c1[i] = c2[i]; c2[i] = tmp;
                    }
                }

                mutate(c1, random);
                mutate(c2, random);

                repair(c1, candidates, matrix, remainingReqs);
                repair(c2, candidates, matrix, remainingReqs);

                newPopulation.add(c1);
                if (newPopulation.size() < populationSize) newPopulation.add(c2);
            }

            population = newPopulation;
        }

        return best != null ? best : population.get(0);
    }

    /**
     * Fitness = selectedCount + M * uncoveredCount.
     * Минимизируем: сначала устраняем непокрытые требования (штраф M=1000),
     * затем уменьшаем количество выбранных тестов.
     */
    private double fitness(boolean[] chromosome, List<Integer> candidates,
                           CoverageMatrix matrix, Set<Integer> remainingReqs) {
        Set<Integer> selected = new HashSet<>();
        for (int i = 0; i < chromosome.length; i++) {
            if (chromosome[i]) selected.add(candidates.get(i));
        }

        Set<Integer> covered = new HashSet<>();
        for (int t : selected) covered.addAll(matrix.getRequirementsCoveredByTest(t));

        int uncovered = 0;
        for (int r : remainingReqs) {
            if (!covered.contains(r)) uncovered++;
        }

        return selected.size() + 1000.0 * uncovered;
    }

    private boolean[] tournament(List<boolean[]> population, double[] scores, Random random) {
        int best = -1;
        for (int i = 0; i < tournamentSize; i++) {
            int candidate = random.nextInt(population.size());
            if (best == -1 || scores[candidate] < scores[best]) {
                best = candidate;
            }
        }
        return Arrays.copyOf(population.get(best), population.get(best).length);
    }

    private void mutate(boolean[] chromosome, Random random) {
        for (int i = 0; i < chromosome.length; i++) {
            if (random.nextDouble() < mutationRate) chromosome[i] = !chromosome[i];
        }
    }

    /**
     * Greedy repair: если хромосома не покрывает все оставшиеся требования,
     * жадно добавляет тест, покрывающий максимум непокрытых требований.
     * Реализует оператор "greedy reparation" из ATSM Section 3.3.4.
     */
    private void repair(boolean[] chromosome, List<Integer> candidates,
                        CoverageMatrix matrix, Set<Integer> remainingReqs) {
        Set<Integer> selected = new LinkedHashSet<>();
        for (int i = 0; i < chromosome.length; i++) {
            if (chromosome[i]) selected.add(candidates.get(i));
        }

        Set<Integer> uncovered = new LinkedHashSet<>(remainingReqs);
        for (int t : selected) uncovered.removeAll(matrix.getRequirementsCoveredByTest(t));

        while (!uncovered.isEmpty()) {
            int bestIdx = -1;
            int bestGain = 0;

            for (int i = 0; i < candidates.size(); i++) {
                if (chromosome[i]) continue;
                int gain = 0;
                for (int r : matrix.getRequirementsCoveredByTest(candidates.get(i))) {
                    if (uncovered.contains(r)) gain++;
                }
                if (gain > bestGain) {
                    bestGain = gain;
                    bestIdx = i;
                }
            }

            if (bestIdx == -1) break;

            chromosome[bestIdx] = true;
            selected.add(candidates.get(bestIdx));
            uncovered.removeAll(matrix.getRequirementsCoveredByTest(candidates.get(bestIdx)));
        }
    }
}
import java.util.*;

public class GreedyMaterialization {

    // Get all dependent views (including the view itself)
    private static Set<String> getDependentViews(String startView, Map<String, List<String>> dependencyGraph) {
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startView);
        visited.add(startView);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String childView : dependencyGraph.getOrDefault(current, Collections.emptyList())) {
                if (!visited.contains(childView)) {
                    visited.add(childView);
                    queue.add(childView);
                }
            }
        }
        return visited;
    }

    // Compute the benefit of materializing a candidate view
    private static int calculateBenefit(String candidateView,
                                        Map<String, Integer> currentEvaluationCost,
                                        Map<String, Integer> materializationCost,
                                        Map<String, List<String>> dependencyGraph) {
        int totalBenefit = 0;
        Set<String> dependentViews = getDependentViews(candidateView, dependencyGraph);

        for (String view : dependentViews) {
            int oldCost = currentEvaluationCost.get(view);
            int newCost = Math.min(oldCost, materializationCost.get(candidateView));
            totalBenefit += (oldCost - newCost);
        }
        return totalBenefit;
    }

    // Update the current evaluation costs after materializing a view
    private static void updateEvaluationCosts(String selectedView,
                                              Map<String, Integer> currentEvaluationCost,
                                              Map<String, Integer> materializationCost,
                                              Map<String, List<String>> dependencyGraph) {
        Set<String> dependentViews = getDependentViews(selectedView, dependencyGraph);
        for (String view : dependentViews) {
            currentEvaluationCost.put(view, Math.min(currentEvaluationCost.get(view), materializationCost.get(selectedView)));
        }
    }

    public static void main(String[] args) {
        // All views in the lattice
        List<String> allViews = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h");

        // Dependency graph (parent -> children)
        Map<String, List<String>> dependencyGraph = new HashMap<>();
        for (String view : allViews) dependencyGraph.put(view, new ArrayList<>());
        dependencyGraph.get("a").addAll(Arrays.asList("b", "c"));
        dependencyGraph.get("b").addAll(Arrays.asList("d", "e"));
        dependencyGraph.get("c").addAll(Arrays.asList("e", "f"));
        dependencyGraph.get("d").add("g");
        dependencyGraph.get("e").addAll(Arrays.asList("g", "h"));
        dependencyGraph.get("f").add("h");

        // Cost if the view is materialized
        Map<String, Integer> materializationCost = new HashMap<>();
        materializationCost.put("a", 100);
        materializationCost.put("b", 50);
        materializationCost.put("c", 75);
        materializationCost.put("d", 20);
        materializationCost.put("e", 30);
        materializationCost.put("f", 40);
        materializationCost.put("g", 1);
        materializationCost.put("h", 10);

        // Current evaluation cost (initially using only top view 'a')
        Map<String, Integer> currentEvaluationCost = new LinkedHashMap<>();
        for (String view : allViews) currentEvaluationCost.put(view, 100);

        // Selected views (top view is always materialized)
        Set<String> selectedViews = new LinkedHashSet<>();
        selectedViews.add("a");
        updateEvaluationCosts("a", currentEvaluationCost, materializationCost, dependencyGraph);

        int additionalSelections = 3; // number of extra views to pick

        for (int round = 1; round <= additionalSelections; round++) {
            String bestCandidate = null;
            int maxBenefit = -1;

            for (String candidate : allViews) {
                if (selectedViews.contains(candidate)) continue;
                int benefit = calculateBenefit(candidate, currentEvaluationCost, materializationCost, dependencyGraph);
                if (benefit > maxBenefit) {
                    maxBenefit = benefit;
                    bestCandidate = candidate;
                }
            }

            if (bestCandidate != null) {
                selectedViews.add(bestCandidate);
                updateEvaluationCosts(bestCandidate, currentEvaluationCost, materializationCost, dependencyGraph);
                System.out.println("Round " + round + " pick: " + bestCandidate + " (Benefit: " + maxBenefit + ")");
            } else {
                System.out.println("No beneficial candidate found in round " + round);
            }
        }

        // Calculate final total cost
        int totalCost = 0;
        for (String view : allViews) totalCost += currentEvaluationCost.get(view);

        System.out.println("Selected views: " + selectedViews);
        System.out.println("Total cost after selection: " + totalCost);
    }
}

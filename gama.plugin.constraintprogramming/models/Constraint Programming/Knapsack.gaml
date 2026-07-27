/**
* Name: Knapsack
* Author: gama.plugin.constraintprogramming
* Description: Selecting a subset of items whose total value is largest, under a weight limit.
*   The archetype of a selection problem: one boolean variable per item, a weighted sum bounded
*   from above for the capacity, and another weighted sum as the objective.
* Tags: constraint, optimization
*/

model knapsack

global {

	list<int> weights <- [12, 2, 1, 1, 4];
	list<int> values <- [4, 2, 2, 1, 10];
	int capacity <- 15 min: 1 max: 30;

	init {
		problem p <- problem("knapsack");
		list<pb_variable> taken <- bool_vars(p, "take", length(weights));
		pb_variable total <- int_var(p, "total", 0, sum(values));

		do post(scalar(taken, weights, "<=", capacity));
		do post(scalar(taken, values, "=", total));

		solution best <- maximize(p, total);

		if (best.exists) {
			list<int> selection <- values_of(best, taken);
			write "Best value: " + value_of(best, total) + " for a capacity of " + capacity;
			loop i from: 0 to: length(weights) - 1 {
				if (selection[i] = 1) {
					write "  item " + i + " (weight " + weights[i] + ", value " + values[i] + ")";
				}
			}
		} else {
			write "No solution";
		}
	}

}

experiment knapsack_solving type: gui title: "Knapsack" {
	parameter "Capacity" var: capacity;
}

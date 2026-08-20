/**
* Name: Engine Benchmark
* Author: Baptiste Lesquoy
* Description: Solves the same family of problems with each engine, on instances that grow one step
*   at a time, and charts what each of them does with them.
*
*   The instance is a multidimensional knapsack: pick items to maximise their total value under
*   several capacity constraints at once. It is linear, so every engine can express it, and it is
*   NP-hard, so it separates them quickly. Each step adds items_per_step items to the instance.
*
*   Read the two charts together. The first says how long each engine took, the second what it came
*   back with. An engine that looks fast because it gave up only shows up in the second one.
* Tags: constraint, optimization, lp, benchmark
*/

model engine_benchmark

global {

	// One instance per cycle, growing by this many items each time
	int items_per_step <- 10 min: 1 max: 50;

	// The number of capacity constraints the items have to fit under
	int nb_capacities <- 5 min: 1 max: 20;

	// No engine is given longer than this on one instance, except the lp one, which has no way of
	// stopping itself: its branch and bound runs to the end whatever budget it is given
	float budget <- 2.0 #s;

	// The lp engine works on a dense tableau and cannot be interrupted, so it is dropped past this
	// size rather than left to freeze the whole run
	int lp_limit <- 30 min: 0 max: 500;

	// The size of the instance solved at this cycle
	int nb_items <- 0;

	// What the last instance cost each engine, in milliseconds, and what each of them found
	float time_choco <- 0.0;
	float time_lp <- 0.0;
	float time_highs <- 0.0;
	float value_choco <- 0.0;
	float value_lp <- 0.0;
	float value_highs <- 0.0;

	// The instance itself, drawn once per cycle and given to every engine
	list<int> item_value;
	list<list<int>> item_weight;
	list<int> capacity;

	reflex benchmark {
		nb_items <- (cycle + 1) * items_per_step;
		do draw_instance();

		do solve_with("highs");
		do solve_with("choco");
		if (nb_items <= lp_limit) {
			do solve_with("lp");
		} else {
			time_lp <- 0.0;
			value_lp <- 0.0;
		}

		write "" + nb_items + " items: highs " + time_highs + " ms (" + value_highs
			+ "), choco " + time_choco + " ms (" + value_choco + ")"
			+ (nb_items <= lp_limit ? ", lp " + time_lp + " ms (" + value_lp + ")" : "");
	}

	/** Draws a fresh instance: a value per item, and a weight per item and per capacity. */
	action draw_instance(){
		item_value <- [];
		loop times: nb_items { item_value <- item_value + rnd(10, 100); }

		item_weight <- [];
		capacity <- [];
		loop c from: 0 to: nb_capacities - 1 {
			list<int> weights <- [];
			loop times: nb_items { weights <- weights + rnd(1, 50); }
			item_weight <- item_weight + [weights];
			// Half the total weight, which keeps the instance from being trivially feasible
			capacity <- capacity + int(sum(weights) / 2);
		}
	}

	/** Builds the instance for one engine, solves it, and records the time and the value found. */
	action solve_with (string engine) {
		float started <- gama.machine_time;

		problem p <- problem("knapsack_" + nb_items, engine);
		list<pb_variable> taken <- bool_vars(p, "take", nb_items);
		pb_variable total <- int_var(p, "total", 0, sum(item_value));

		loop c from: 0 to: nb_capacities - 1 {
			do post(scalar(taken, item_weight[c], "<=", capacity[c]));
		}
		do post(scalar(taken, item_value, "=", total));

		solution best <- maximize(p, total, budget);

		float elapsed <- gama.machine_time - started;
		float found <- best.exists ? float(value_of(best, total)) : 0.0;

		if (engine = "highs") {
			time_highs <- elapsed;
			value_highs <- found;
		}
		if (engine = "choco") {
			time_choco <- elapsed;
			value_choco <- found;
		}
		if (engine = "lp") {
			time_lp <- elapsed;
			value_lp <- found;
		}
	}

}

experiment compare type: gui title: "Compare the engines" {

	parameter "Items added per step" var: items_per_step;
	parameter "Capacity constraints" var: nb_capacities;
	parameter "Budget per instance" var: budget min: 0.1 #s max: 30.0 #s;
	parameter "Drop the lp engine past" var: lp_limit;

	output {
		display "Time" type: 2d {
			chart "Milliseconds spent per instance" type: series x_label: "step" y_label: "ms" {
				data "highs" value: time_highs color: #green marker: false;
				data "choco" value: time_choco color: #red marker: false;
				data "lp" value: time_lp color: #orange marker: false;
			}
		}
		display "Value" type: 2d {
			chart "Best value found per instance" type: series x_label: "step" y_label: "value" {
				data "highs" value: value_highs color: #green marker: false;
				data "choco" value: value_choco color: #red marker: false;
				data "lp" value: value_lp color: #orange marker: false;
			}
		}
	}
}

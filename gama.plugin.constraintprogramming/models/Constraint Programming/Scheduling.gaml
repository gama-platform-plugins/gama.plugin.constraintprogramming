/**
* Name: Scheduling
* Author: gama.plugin.constraintprogramming
* Description: Placing tasks in time so that every precedence is respected and the whole project
*   ends as early as possible. The archetype of a sequencing problem: one start variable per task,
*   one inequality per precedence, and the makespan as objective.
*   Limiting a shared resource would require the cumulative constraint, not exposed yet.
* Tags: constraint, optimization
*/

model scheduling

global {

	list<int> durations <- [3, 2, 5, 1, 4];
	// each pair means "the first task must be over before the second one starts"
	list<list<int>> precedences <- [[0, 1], [0, 2], [1, 3], [2, 3], [3, 4]];

	init {
		int horizon <- sum(durations);
		problem p <- problem("schedule");
		list<pb_variable> starts <- int_vars(p, "start", length(durations), 0, horizon);

		loop link over: precedences {
			int before <- link[0];
			int after <- link[1];
			// start[before] + duration[before] <= start[after]
			do post(arithm(starts[before], "-", starts[after], "<=", -durations[before]));
		}

		// The completion times are views over the starts: they cost the solver neither a
		// propagator nor a search decision
		list<pb_variable> ends <- [];
		loop i from: 0 to: length(durations) - 1 {
			ends <- ends + offset_var(starts[i], durations[i]);
		}
		pb_variable makespan <- max_var(ends);

		solution best <- minimize(p, makespan);

		if (best.exists) {
			write "Makespan: " + value_of(best, makespan);
			loop i from: 0 to: length(durations) - 1 {
				int begin <- value_of(best, starts[i]);
				write "  task " + i + ": " + begin + " -> " + (begin + durations[i]);
			}
		} else {
			write "No solution";
		}
	}

}

experiment scheduling_solving type: gui title: "Scheduling" { }

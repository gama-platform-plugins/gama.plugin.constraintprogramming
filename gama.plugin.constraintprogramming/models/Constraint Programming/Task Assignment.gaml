/**
* Name: Task Assignment
* Author: gama.plugin.constraintprogramming
* Description: Assigns one distinct task to each worker agent, minimising the total cost.
*   Shows the two things that make constraint programming useful inside an agent-based model:
*   building one decision variable per agent in an ordinary GAML loop, and writing the solution
*   back into the attributes of the agents once it has been found.
* Tags: constraint, optimization
*/

model task_assignment

global {

	int nb_workers <- 4 min: 2 max: 8;

	init {
		create worker number: nb_workers {
			// The cost of each task for this worker
			loop times: nb_workers { costs <- costs + rnd(1, 9); }
		}
		do assign;
	}

	/** Builds the problem from the current population of workers, searches it, and stores the
	 * result in the agents themselves. The number of variables is decided at runtime, from the
	 * number of agents: nothing has to be declared in advance. */
	action assign {
		problem p <- problem("assignment");

		list<pb_variable> chosen_task <- [];
		list<pb_variable> individual_cost <- [];
		loop w over: worker {
			pb_variable t <- int_var(p, "task_of_" + w.name, 0, nb_workers - 1);
			chosen_task <- chosen_task + t;
			individual_cost <- individual_cost + element_var(w.costs, t);
		}

		// Two workers cannot be given the same task
		do post(all_different(chosen_task));

		pb_variable total <- sum_var(individual_cost);
		solution best <- minimize(p, total, 5 #s);

		if (!best.exists) {
			write "No assignment found";
			return;
		}

		// Writing the solution back into the agents
		loop i from: 0 to: length(worker) - 1 {
			worker[i].assigned_task <- value_of(best, chosen_task[i]);
		}
		write "Total cost: " + value_of(best, total) + " (" + p.nodes + " nodes, " + p.search_time + "s)";
		ask worker {
			write name + " -> task " + assigned_task + " (cost " + costs[assigned_task] + ")";
		}
	}

}

species worker {
	list<int> costs;
	int assigned_task <- -1;
}

experiment assignment_solving type: gui title: "Task assignment" {
	parameter "Number of workers" var: nb_workers;
}

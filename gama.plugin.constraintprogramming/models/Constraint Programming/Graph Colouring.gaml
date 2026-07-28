/**
* Name: Graph Colouring
* Author: Baptiste Lesquoy
* Description: Colouring the nodes of a graph so that no two adjacent nodes share a colour,
*   using as few colours as possible. The archetype of a partitioning problem: one variable per
*   node, an inequality per edge, and the largest colour used as objective.
* Tags: constraint, optimization
*/

model graph_colouring

global {

	int nb_nodes <- 6;
	list<list<int>> edges <- [[0, 1], [0, 2], [1, 2], [1, 3], [2, 4], [3, 4], [3, 5], [4, 5]];

	init {
		problem p <- problem("colouring");
		list<pb_variable> colours <- int_vars(p, "colour", nb_nodes, 1, nb_nodes);

		loop edge over: edges {
			do post(arithm(colours[edge[0]], "!=", colours[edge[1]]));
		}
		// Symmetry breaking: colours are interchangeable, so the first node can always be
		// given the first colour. Without this the solver explores many equivalent solutions.
		do post(arithm(colours[0], "=", 1));

		// Minimising the largest colour used amounts to minimising the number of colours
		pb_variable nb_colours <- max_var(colours);
		solution best <- minimize(p, nb_colours);

		if (best.exists) {
			write '' + value_of(best, nb_colours) + " colours needed";
			list<int> assigned <- values_of(best, colours);
			loop i from: 0 to: nb_nodes - 1 { write "  node " + i + ": colour " + assigned[i]; }
		} else {
			write "No solution";
		}
	}

}

experiment colouring_solving type: gui title: "Graph colouring" { }

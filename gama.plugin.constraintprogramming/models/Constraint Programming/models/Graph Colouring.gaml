/**
* Name: Graph Colouring
* Author: Baptiste Lesquoy
* Description: Colouring the nodes of a graph so that no two adjacent nodes share a colour, using as
*   few colours as possible. The archetype of a partitioning problem: one variable per node, an
*   inequality per edge, and the largest colour used as objective.
*
*   The nodes are laid out on a circle and painted with the colours the solver chose, so the
*   constraint can be read off the display: no edge joins two nodes of the same colour.
* Tags: constraint, optimization, agents
*/

model graph_colouring

global {

	int nb_nodes <- 8 min: 3 max: 20;

	// How likely any two nodes are to be joined. A denser graph needs more colours
	float density <- 0.35 min: 0.05 max: 1.0;

	// The pairs of nodes that must differ, drawn once at startup
	list<list<int>> edges <- [];

	// One entry per colour the solver may use
	list<rgb> palette <- [#firebrick, #steelblue, #darkseagreen, #goldenrod, #mediumpurple,
		#lightsalmon, #teal, #hotpink, #sienna, #cadetblue];

	init {
		do draw_graph();

		problem p <- problem("colouring");
		list<pb_variable> colours <- int_vars(p, "colour", nb_nodes, 1, nb_nodes);

		loop edge over: edges {
			do post(colours[edge[0]] != colours[edge[1]]);
		}
		// Symmetry breaking: colours are interchangeable, so the first node can always be given the
		// first colour. Without this the solver explores many equivalent solutions.
		do post(colours[0] = 1);

		// Minimising the largest colour used amounts to minimising the number of colours
		pb_variable nb_colours <- max_var(colours);
		solution best <- minimize(p, nb_colours);

		if (best.exists) {
			list<int> assigned <- values_of(best, colours);
			write "" + length(edges) + " edges over " + nb_nodes + " nodes, "
				+ value_of(best, nb_colours) + " colours needed";
			loop i from: 0 to: nb_nodes - 1 {
				ask node_agent[i] { chosen <- assigned[i]; }
			}
		} else {
			write "No solution";
		}
	}

	/** Places the nodes evenly on a circle, draws the edges at random, and creates one agent each. */
	action draw_graph(){
		float radius <- 38.0;
		loop i from: 0 to: nb_nodes - 1 {
			create node_agent {
				id <- i;
				location <- {50 + radius * cos(360 * i / nb_nodes),
					50 + radius * sin(360 * i / nb_nodes)};
			}
		}
		edges <- [];
		loop i from: 0 to: nb_nodes - 2 {
			loop j from: i + 1 to: nb_nodes - 1 {
				if (rnd(1.0) < density) {
					edges <- edges + [[i, j]];
					create edge_agent {
						from <- node_agent[i].location;
						to <- node_agent[j].location;
					}
				}
			}
		}
	}

}

/** A node of the graph. Its colour is the value the solver gave to its variable. */
species node_agent {

	// The index of the node, matching the index of its variable
	int id;

	// The colour it was given, from 1, or 0 while the problem has not been solved
	int chosen <- 0;

	aspect default {
		draw circle(3.5) color: chosen = 0 ? #lightgray : palette[(chosen - 1) mod length(palette)]
			border: #black;
		draw "" + id color: #black font: font("Arial", 11, #bold) at: location + {-1.2, 6.5};
	}
}

/** An edge, drawn as a plain segment between the two nodes it joins. */
species edge_agent {
	point from;
	point to;

	aspect default {
		draw line([from, to]) color: rgb(150, 150, 150) width: 2;
	}
}

experiment colouring_solving type: gui title: "Graph colouring" {
	parameter "Number of nodes" var: nb_nodes;
	parameter "Edge density" var: density;

	output {
		display "Graph" type: 2d {
			species edge_agent aspect: default;
			species node_agent aspect: default;
		}
	}
}

/**
* Name: Travelling Salesman
* Author: Baptiste Lesquoy
* Description: Finding the shortest tour visiting every city exactly once. The archetype of a
*   routing problem: one successor variable per city, the circuit constraint to forbid sub-tours,
*   and element_var to read the length of a leg in the distance matrix at a variable index.
* Tags: constraint, optimization
*/

model travelling_salesman

global {

	// One row per city. This matrix is symmetric, so the row and column convention of
	// element_var does not matter here; it would on an asymmetric cost matrix.
	matrix<int> distances <- matrix([
		[0, 4, 8, 9, 12],
		[4, 0, 6, 8, 9],
		[8, 6, 0, 10, 11],
		[9, 8, 10, 0, 7],
		[12, 9, 11, 7, 0]
	]);

	int nb_cities <- 5;

	init {
		problem p <- problem("tsp");

		// next[i] = j means that the tour goes from city i to city j. circuit forces these
		// successors to form one single tour rather than several disjoint loops.
		list<pb_variable> next <- int_vars(p, "next", nb_cities, 0, nb_cities - 1);
		do post(circuit(next));

		list<pb_variable> legs <- [];
		loop i from: 0 to: nb_cities - 1 {
			legs <- legs + element_var(distances, i, next[i]);
		}
		pb_variable tour_length <- sum_var(legs);

		solution best <- minimize(p, tour_length, 5 #s);

		if (best.exists) {
			list<int> successor <- values_of(best, next);
			string tour <- "0";
			int current <- successor[0];
			loop while: current != 0 {
				tour <- tour + " -> " + current;
				current <- successor[current];
			}
			write "Shortest tour: " + tour + " -> 0";
			write "Length: " + value_of(best, tour_length);
		} else {
			write "No solution";
		}
	}

}

experiment tsp_solving type: gui title: "Travelling salesman" { }

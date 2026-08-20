/**
* Name: Travelling Salesman
* Author: Baptiste Lesquoy
* Description: Finding the shortest tour visiting every city exactly once. The archetype of a routing
*   problem: one successor variable per city, the circuit constraint to forbid sub-tours, and
*   element_var to read the length of a leg in the distance matrix at a variable index.
*
*   The cities are scattered over the world and the tour is drawn between them, so the answer can be
*   judged by eye: a shorter tour is a tour without crossings.
* Tags: constraint, optimization, agents
*/

model travelling_salesman

global {

	int nb_cities <- 8 min: 4 max: 14;

	// How long the solver is given. Past a dozen cities it is unlikely to prove optimality, and
	// returns the best tour it found instead
	float budget <- 5.0 #s;

	// The distance from each city to every other, rounded to integers for the solver
	list<list<int>> distances <- [];

	// The length of the tour found, in distance units
	int tour_length <- 0;

	init {
		do scatter_cities();

		problem p <- problem("tsp");

		// next[i] = j means the tour goes from city i to city j. circuit forces these successors to
		// form one single tour rather than several disjoint loops.
		list<pb_variable> next <- int_vars(p, "next", nb_cities, 0, nb_cities - 1);
		do post(circuit(next));

		list<pb_variable> legs <- [];
		loop i from: 0 to: nb_cities - 1 {
			legs <- legs + element_var(distances[i], next[i]);
		}
		pb_variable total <- sum_var(legs);

		solution best <- minimize(p, total, budget);

		if (best.exists) {
			list<int> successor <- values_of(best, next);
			tour_length <- value_of(best, total);
			loop i from: 0 to: nb_cities - 1 {
				ask city[i] { follows <- successor[i]; }
			}
			write "Tour of length " + tour_length + " over " + nb_cities + " cities";
			write "  " + p.nodes + " nodes explored in " + p.search_time + "s";
		} else {
			write "No tour found within the budget";
		}
	}

	/** Scatters the cities, then measures the distance between every pair of them. */
	action scatter_cities(){
		loop i from: 0 to: nb_cities - 1 {
			create city {
				id <- i;
				location <- {rnd(10.0, 90.0), rnd(10.0, 90.0)};
			}
		}
		distances <- [];
		loop i from: 0 to: nb_cities - 1 {
			list<int> row <- [];
			loop j from: 0 to: nb_cities - 1 {
				row <- row + int(city[i].location distance_to city[j].location);
			}
			distances <- distances + [row];
		}
	}

}

/** A city. Once the tour is known it also knows which city it leads to. */
species city {

	// The index of the city, matching the index of its variable
	int id;

	// The city the tour goes to from here, or -1 while the problem has not been solved
	int follows <- -1;

	aspect default {
		if (follows >= 0) {
			draw line([location, city[follows].location]) color: #steelblue width: 3;
		}
		draw circle(2) color: id = 0 ? #firebrick : #steelblue border: #black;
		draw "" + id color: #black font: font("Arial", 11, #bold) at: location + {2.5, -2.5};
	}
}

experiment tsp_solving type: gui title: "Travelling salesman" {
	parameter "Number of cities" var: nb_cities;
	parameter "Time budget" var: budget min: 0.5 #s max: 60.0 #s;

	output {
		display "Tour" type: 2d {
			species city aspect: default;
		}
		monitor "Tour length" value: tour_length;
		monitor "Cities" value: nb_cities;
	}
}

/**
* Name: Linear System
* Author: Baptiste Lesquoy
* Description: A system of linear inequations, written the way it would be written on paper. Nothing
*   here declares a constraint through a named operator: the variables are combined with +, - and *,
*   compared with <= and >=, and the objective is an expression of the same kind rather than a
*   variable declared for the occasion.
*
*     maximise  3x + 4y
*     subject to   x + 2y <= 14
*                 3x -  y >=  0
*                  x -  y <=  2
*                  x, y in [0, 10]
*
*   The display shades the region the inequations allow, darker where the objective is larger, and
*   marks the point the solver returned. The optimum sits on a vertex of the region, which is what
*   the picture is there to make obvious.
* Tags: constraint, optimization, lp, expressions
*/

model linear_system

global {

	// The side of the square the two variables live in, and of the world the region is drawn on
	float span <- 10.0;

	// "highs" for the native linear solver, "lp" for the one bundled with Choco. The constraint
	// engine takes these constraints as well, but not a continuous objective: its arithmetic
	// expressions are integer ones. Declaring x and y with int_var instead of real_var makes the
	// same text run on "choco" too, and here the optimum falls on integers anyway.
	string engine <- "highs" among: ["highs", "lp"];

	// What the solver came back with
	float best_x <- 0.0;
	float best_y <- 0.0;
	float best_objective <- 0.0;
	bool solved <- false;

	geometry shape <- square(span);

	init {
		problem p <- problem("system", engine);

		pb_variable x <- real_var(p, "x", 0.0, span);
		pb_variable y <- real_var(p, "y", 0.0, span);

		// The system, one line per inequation, in the order it is stated above
		do post(x + 2 * y <= 14);
		do post(3 * x - y >= 0);
		do post(x - y <= 2);

		// The objective is an expression as well. It is never declared as a variable and never tied
		// to one by an equality: the search is given the expression itself.
		pb_variable objective <- 3 * x + 4 * y;

		solution best <- maximize(p, objective);

		if (best.exists) {
			best_x <- real_value_of(best, x);
			best_y <- real_value_of(best, y);
			best_objective <- real_value_of(best, objective);
			solved <- true;

			write "engine: " + engine;
			write "  x = " + best_x + ", y = " + best_y;
			write "  3x + 4y = " + best_objective;
			write "  x + 2y = " + (best_x + 2 * best_y) + " (<= 14)";
			write "  3x - y = " + (3 * best_x - best_y) + " (>= 0)";
			write "  x - y  = " + (best_x - best_y) + " (<= 2)";
			write "  two of the three are tight, which is what puts the optimum on a vertex";
		} else {
			write "The system has no solution";
		}

		do shade_region();
	}

	/** Paints each cell according to whether its centre satisfies the system, and to the objective there. */
	action shade_region() {
		ask cell {
			// The cell knows where it is in world units, which are the units of the two variables
			float px <- location.x;
			float py <- span - location.y;
			feasible <- px + 2 * py <= 14 and 3 * px - py >= 0 and px - py <= 2;
			objective_here <- 3 * px + 4 * py;
		}
		float top <- max(cell collect (each.objective_here));
		ask cell {
			if (feasible) {
				// Darker where the objective is larger, so the direction the solver climbs is visible
				int shade <- int(200 - 150 * objective_here / top);
				color <- rgb(shade, 255 - (200 - shade) / 3, shade);
			} else {
				color <- rgb(248, 248, 248);
			}
		}
	}

}

/** The square the two variables live in, sampled to show which points the system allows. */
grid cell width: 60 height: 60 {
	bool feasible <- false;
	float objective_here <- 0.0;
	rgb color <- #white;
}

experiment solve_system type: gui title: "System of inequations" {

	parameter "Engine" var: engine;

	output {
		display "Feasible region" type: 2d antialias: false {
			grid cell;
			graphics "solution" {
				if (solved) {
					// The world draws y downwards, so the point is mirrored to read as a graph would
					point at <- {best_x, span - best_y};
					draw circle(0.22) at: at color: #firebrick border: #black;
					draw "(" + (best_x with_precision 2) + ", " + (best_y with_precision 2) + ")"
						color: #black font: font("Arial", 12, #bold) at: at + {0.35, -0.25};
				}
			}
		}
		monitor "x" value: best_x with_precision 3;
		monitor "y" value: best_y with_precision 3;
		monitor "3x + 4y" value: best_objective with_precision 3;
	}
}

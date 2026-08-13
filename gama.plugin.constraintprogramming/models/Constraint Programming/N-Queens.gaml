/**
* Name: N-Queens
* Author: Baptiste Lesquoy
* Description: The classic n-queens problem, written with the constraint programming plugin.
*   One variable per column, holding the row of the queen placed in that column. Two queens
*   are on the same diagonal when the difference of their rows equals the difference of their
*   columns, which is what the two arithm constraints forbid.
* Tags: constraint, optimization
*/

model n_queens

global {

	int n <- 8 min: 4 max: 24;

	/** Builds a n-queens problem. A problem is a mutable object carrying a propagation engine
	 * and a backtracking trail: it is built, searched, and dropped, rather than kept between
	 * cycles. */
	action build_queens (problem p, list<pb_variable> queens, int size) {
		do post(all_different(queens));
		loop i from: 0 to: size - 2 {
			loop j from: i + 1 to: size - 1 {
				do post(queens[i] - queens[j] != j - i);
				do post(queens[i] - queens[j] != i - j);
			}
		}
	}

	init {
		problem p <- problem("n_queens");
		list<pb_variable> queens <- int_vars(p, "Q", n, 1, n);
		do build_queens(p, queens, n);

		solution sol <- search(p, 10 #s);

		if (sol.exists) {
			write "Rows of the queens, column by column: " + values_of(sol, queens);
		} else {
			write "No solution found (or search interrupted)";
		}
		write "" + p.nodes + " nodes explored, " + p.fails + " failures, in " + p.search_time + "s";

		// Enumerating several solutions. A fresh problem is used, since the previous search
		// left its solver where it stopped. The bound is not optional in practice: a
		// constraint problem usually has far more solutions than one wants to materialise.
		problem q <- problem("n_queens_bis");
		list<pb_variable> others <- int_vars(q, "Q", 6, 1, 6);
		do build_queens(q, others, 6);

		loop one over: all_solutions(q, 5) {
			write "Solution: " + values_of(one, others);
		}
	}

}

experiment n_queens_solving type: gui title: "N-Queens" {
	parameter "Number of queens" var: n;
}

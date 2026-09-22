/**
* Name: N-Queens
* Author: Baptiste Lesquoy
* Description: The classic n-queens problem, written with the constraint programming plugin, and
*   shown on a board. One variable per column, holding the row of the queen placed in that column.
*   Two queens share a diagonal when the difference of their rows equals the difference of their
*   columns, which is what the two constraints forbid.
* Tags: constraint, grid
*/

model n_queens

global {

	image_file queen_image <- image_file("../includes/queen.png");

	int n <- 8 min: 4 max: 24;

	// Where each queen ended up, read from the solution and drawn on the board
	list<int> queen_row <- [];

	/** Builds a n-queens problem over the columns given. */
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
			queen_row <- values_of(sol, queens);
			write "Rows of the queens, column by column: " + queen_row;
			do show_on_board();
		} else {
			write "No solution found (or search interrupted)";
		}
		write "" + p.nodes + " nodes explored, " + p.fails + " failures, in " + p.search_time + "s";
	}

	/** Paints the board: the squares alternate, and the cell holding a queen turns red. */
	action show_on_board(){
		ask squares {
			occupied <- false;
			color <- (grid_x + grid_y) mod 2 = 0 ? rgb(245, 240, 230) : rgb(170, 155, 135);
		}
		loop i from: 0 to: n - 1 {
			// The variables count rows from 1, the grid from 0
			ask first(squares where (each.grid_x = i and each.grid_y = queen_row[i] - 1)) {
				occupied <- true;
				color <- #firebrick;
			}
		}
	}

}

/** The board. Its size follows n, so the display always shows the whole problem. */
grid squares width: n height: n neighbors: 8 {
	bool occupied <- false;
	rgb color <- #white;
	
	aspect default {
		draw shape color:color;
		draw shape wireframe:true color:rgb(120,120,120);
		if occupied {
			draw queen_image size:{shape.width, shape.height};
		}
	}
}

experiment n_queens_solving type: gui title: "N-Queens" {
	parameter "Number of queens" var: n;

	output {
		display "Board" type: 2d antialias:false{
			species squares;
		}
	}
}

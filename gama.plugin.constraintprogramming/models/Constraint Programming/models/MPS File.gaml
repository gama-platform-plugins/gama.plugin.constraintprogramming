/**
* Name: MPS File
* Author: Baptiste Lesquoy
* Description: Reads a linear problem written in the MPS format, the interchange format of linear
*   and mixed integer programming, and solves it. Nothing is declared in the model: the variables,
*   their bounds, the constraints and the objective all come from the file.
*
*   testprob.mps is the example of the format specification, small enough to be checked by hand:
*   its optimum is 16, at XONE = 0, YTWO = -1, ZTHREE = 6.
* Tags: constraint, optimization, lp
*/

model mps_file

global {

	// The path is resolved relative to the model. Compressed files are not read: uncompress them
	// first, whether they use gzip or the packed form Netlib distributes for its own test set.
	string file_path <- "../includes/testprob.mps"
		among: ["../includes/testprob.mps", "../includes/big_lp_problem-80bau3b.mps"];

	// Above this many variables, the values are counted rather than listed
	int listing_limit <- 20;

	init {
		problem p <- read_mps(file_path);

		write "problem " + p.name + " read from " + file_path;
		write "  " + length(p.variables) + " variables, " + p.nb_constraints + " constraints";
		write "  objective: " + (length(p.variables) <= listing_limit ? objective_of(p) : "" + length(p.variables) + " terms")
			+ (maximises(p) ? " (to maximise)" : " (to minimise)");

		solution best <- optimize(p);

		if (best.exists) {
			write "  optimum: " + objective_value(best);
			if (length(p.variables) <= listing_limit) {
				loop v over: p.variables {
					write "    " + v.name + " = " + real_value_of(best, v);
				}
			} else {
				write "  (values not listed, the problem has more than " + listing_limit + " variables)";
			}
		} else {
			write "  no solution: the constraints of the file are contradictory";
		}
	}

}

experiment read_and_solve type: gui title: "Solve an MPS file" {
	parameter "File" var: file_path;
}

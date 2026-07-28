/**
* Name: Cryptarithm
* Author: Baptiste Lesquoy
* Description: SEND + MORE = MONEY. Each letter stands for a distinct digit. The archetype of a
*   pure satisfaction problem: no objective, a single assignment to find. The whole addition is
*   written as one weighted sum, which is what scalar expresses.
* Tags: constraint
*/

model cryptarithm

global {

	init {
		problem p <- problem("send_more_money");
		// the letters are, in order: S E N D M O R Y
		list<pb_variable> letters <- int_vars(p, "L", 8, 0, 9);

		do post(all_different(letters));
		do post(arithm(letters[0], "!=", 0)); // S
		do post(arithm(letters[4], "!=", 0)); // M
		// 1000S + 100E + 10N + D  +  1000M + 100O + 10R + E  =  10000M + 1000O + 100N + 10E + Y
		do post(scalar(letters, [1000, 91, -90, 1, -9000, -900, 10, -1], "=", 0));
		solution sol <- search(p);

		if (sol.exists) {
			list<string> names <- ["S", "E", "N", "D", "M", "O", "R", "Y"];
			list<int> digits <- values_of(sol, letters);
			loop i from: 0 to: 7 { write names[i] + " = " + digits[i]; }
		} else {
			write "No solution";
		}
	}

}

experiment cryptarithm_solving type: gui title: "SEND + MORE = MONEY" { }

/**
* Name: Non Linear
* Author: Baptiste Lesquoy
* Description: Two problems that are not linear, solved by the constraint engine alone.
*
*   Choco propagates products, quotients, remainders and powers of integer variables natively. Ibex,
*   the library it delegates to and which this plugin does not ship, is only needed for continuous
*   non-linear relations. Over integers nothing is missing.
*
*   The first problem chooses a price and a quantity at the same time, so the revenue is a product of
*   two unknowns. That is the shape most agent models run into, as soon as a decision multiplies
*   another decision rather than a constant. The second enumerates Pythagorean triples, which needs
*   squares.
*
*   Neither runs on a linear engine, and neither is refused silently: 'lp' and 'highs' say which
*   sub-expression they cannot represent.
* Tags: constraint, optimization, non-linear
*/

model non_linear

global {

	// The unit cost of one item, and the largest price the market would bear
	int unit_cost <- 3 min: 0 max: 50;
	int max_price <- 20 min: 1 max: 100;

	// Demand falls with the price: quantity <= base_demand - slope * price
	int base_demand <- 100 min: 10 max: 500;
	int slope <- 4 min: 1 max: 20;

	// The longest side allowed in a Pythagorean triple
	int max_side <- 20 min: 5 max: 60;

	init {
		do pricing();
		do triples();
	}

	/**
	 * Choosing a price and a quantity together. Revenue is price times quantity, a product of two
	 * decisions, so the problem is not linear however it is written.
	 *
	 * With the values above the optimum is 484, at a price of 14 and a quantity of 44: profit as a
	 * function of the price is -4p^2 + 112p - 300, whose maximum falls on 14.
	 */
	action pricing(){
		problem p <- problem("pricing");

		pb_variable price <- int_var(p, "price", 1, max_price);
		pb_variable quantity <- int_var(p, "quantity", 0, base_demand);
		pb_variable profit <- int_var(p, "profit", 0, base_demand * max_price);

		// What the market takes at that price
		do post(quantity <= base_demand - slope * price);

		// The non-linear part: price * quantity, two unknowns multiplied together
		do post(price * quantity - unit_cost * quantity = profit);

		solution best <- maximize(p, profit);

		write "--- Pricing ---";
		if (best.exists) {
			write "  price " + value_of(best, price) + ", quantity " + value_of(best, quantity);
			write "  revenue " + (value_of(best, price) * value_of(best, quantity))
				+ ", cost " + (unit_cost * value_of(best, quantity))
				+ ", profit " + value_of(best, profit);
		} else {
			write "  no solution";
		}
	}

	/**
	 * Every triple of sides of a right triangle up to max_side, ordered so that permutations of the
	 * same triple are not counted twice. Up to 20 there are six of them.
	 */
	action triples(){
		problem p <- problem("pythagoras");

		pb_variable a <- int_var(p, "a", 1, max_side);
		pb_variable b <- int_var(p, "b", 1, max_side);
		pb_variable c <- int_var(p, "c", 1, max_side);

		// Ordering the sides keeps one triple from appearing under several guises
		do post(a <= b);
		do post(b <= c);

		// The non-linear part: three squares
		do post(a ^ 2 + b ^ 2 = c ^ 2);

		write "--- Pythagorean triples up to " + max_side + " ---";
		list<solution> found <- all_solutions(p);
		loop s over: found {
			write "  " + value_of(s, a) + ", " + value_of(s, b) + ", " + value_of(s, c);
		}
		write "  " + length(found) + " triples";
	}

}

experiment non_linear_solving type: gui title: "Non-linear problems" {
	parameter "Unit cost" var: unit_cost;
	parameter "Highest price" var: max_price;
	parameter "Demand at price zero" var: base_demand;
	parameter "Demand slope" var: slope;
	parameter "Longest side" var: max_side;
}

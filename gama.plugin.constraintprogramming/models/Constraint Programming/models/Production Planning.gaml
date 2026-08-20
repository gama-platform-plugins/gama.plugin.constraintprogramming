/**
* Name: Production Planning
* Author: Baptiste Lesquoy
* Description: How many units of each product to make, given limited machine time and material,
*   so that the profit is largest. The archetype of a linear model: every constraint is a weighted
*   sum, which is exactly what a linear engine settles in one go and what a constraint engine has
*   to search for.
*
*   The same model text runs on both engines. Only the name given at creation changes.
* Tags: constraint, optimization, lp
*/

model production_planning

global {

	// Machine hours and units of material consumed by one unit of each product
	list<int> machine_time <- [2, 1];
	list<int> material <- [1, 3];
	list<int> unit_profit <- [5, 4];

	int machine_capacity <- 100;
	int material_stock <- 90;

	// "highs" for the native linear solver, "lp" for the one bundled with Choco, "choco" for the
	// constraint engine. The model text is the same for all three.
	string engine <- "highs" among: ["highs", "lp", "choco"];

	init {
		problem p <- problem("production", engine);

		// One quantity per product. An upper bound is needed by both engines, and the capacity
		// gives a natural one: no product can exceed what the scarcest resource allows.
		pb_variable make_a <- int_var(p, "make_a", 0, machine_capacity);
		pb_variable make_b <- int_var(p, "make_b", 0, machine_capacity);
		pb_variable profit <- int_var(p, "profit", 0, machine_capacity * max(unit_profit));

		do post(machine_time[0] * make_a + machine_time[1] * make_b <= machine_capacity);
		do post(material[0] * make_a + material[1] * make_b <= material_stock);
		do post(unit_profit[0] * make_a + unit_profit[1] * make_b = profit);

		solution best <- maximize(p, profit);

		if (best.exists) {
			write "engine: " + engine;
			write "  make " + value_of(best, make_a) + " of A and " + value_of(best, make_b) + " of B";
			write "  profit " + value_of(best, profit);
			write "  machine hours used: "
				+ (machine_time[0] * value_of(best, make_a) + machine_time[1] * value_of(best, make_b))
				+ " of " + machine_capacity;
			write "  material used: "
				+ (material[0] * value_of(best, make_a) + material[1] * value_of(best, make_b))
				+ " of " + material_stock;
		} else {
			write "No solution";
		}
	}

}

experiment linear type: gui title: "Production planning, linear engine" {
	parameter "Engine" var: engine;
	parameter "Machine capacity" var: machine_capacity min: 10 max: 500;
	parameter "Material stock" var: material_stock min: 10 max: 500;
}

/** The same model on the constraint engine, to compare what each of them does with it. */
experiment constraints type: gui title: "Production planning, constraint engine" {
	parameter "Engine" var: engine init: "choco";
}

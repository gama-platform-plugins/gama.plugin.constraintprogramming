/**
* Name: Livestock Feeding
* Author: Baptiste Lesquoy
* Description: Allocating feed resources to livestock at the lowest cost (ECONOM model).
*   Resources come either from the local farming systems, in limited quantity, or from the market,
*   at a higher price. Each herd has an intake capacity, a nutritional requirement, and a maximum
*   share of grain in its ration.
*
*   The three-dimensional arrays of decision variables of the original Choco model are kept as
*   nested lists, so that x_feed[r][p][l] reads exactly as it does in Java.
* Tags: constraint, optimization
*/

model livestock_feeding

global {

	// -----------------------------------------------------------------------------------------
	// Sets and dimensions
	// -----------------------------------------------------------------------------------------
	int nb_resources <- 4; // 0 = grain, 1 = forage, 2 = stover, 3 = coproduct
	int nb_systems <- 2;   // 0 = organic, 1 = conventional
	int nb_livestock <- 2; // 0 = dairy cows, 1 = goats

	// -----------------------------------------------------------------------------------------
	// Parameters
	// -----------------------------------------------------------------------------------------

	// c_loc[r][p]: unit cost of the local resource r coming from system p
	list<list<int>> c_loc <- [[12, 15], [8, 10], [5, 6], [10, 12]];

	// c_ext[r][p]: unit cost of the resource r bought on the market for system p
	list<list<int>> c_ext <- [[22, 25], [16, 18], [10, 12], [18, 20]];

	// q_loc[r]: quantity of resource r available locally
	list<int> q_loc <- [200, 800, 300, 150];

	// slh[l][p]: number of heads of livestock l in system p
	list<list<int>> slh <- [[15, 10], [20, 10]];

	// ic[l]: intake capacity of one head of livestock l
	list<int> ic <- [60, 40];

	// zeta_rate[r][l]: nutrient content of resource r for livestock l
	list<list<int>> zeta_rate <- [[90, 85], [50, 45], [20, 25], [70, 65]];

	// zeta_req[l]: nutritional requirement of one head of livestock l
	list<int> zeta_req <- [3500, 2200];

	// max_grain[l]: maximum share of grain in the ration of livestock l, as a percentage
	list<int> max_grain <- [40, 50];

	// indices of the resources counted as grain
	list<int> grain_resources <- [0];

	// upper bound of the domain of every allocation variable
	int max_allocation <- 5000;

	init {
		problem pb <- problem("ECO_MODEL_ECONOM");

		// -------------------------------------------------------------------------------------
		// Decision variables
		//   x_feed[r][p][l]: quantity of local resource r from system p given to livestock l
		//   y_feed[r][l][p]: quantity of resource r bought on the market for livestock l in p
		// -------------------------------------------------------------------------------------
		list<list<list<pb_variable>>> x_feed <- [];
		list<list<list<pb_variable>>> y_feed <- [];

		loop r from: 0 to: nb_resources - 1 {
			list<list<pb_variable>> x_per_system <- [];
			list<list<pb_variable>> y_per_livestock <- [];
			loop p from: 0 to: nb_systems - 1 {
				list<pb_variable> x_per_livestock <- [];
				loop l from: 0 to: nb_livestock - 1 {
					x_per_livestock <- x_per_livestock
						+ int_var(pb, "X_r" + r + "_p" + p + "_l" + l, 0, max_allocation);
				}
				x_per_system <- x_per_system + [x_per_livestock];
			}
			loop l from: 0 to: nb_livestock - 1 {
				list<pb_variable> y_per_system <- [];
				loop p from: 0 to: nb_systems - 1 {
					y_per_system <- y_per_system
						+ int_var(pb, "Y_r" + r + "_l" + l + "_p" + p, 0, max_allocation);
				}
				y_per_livestock <- y_per_livestock + [y_per_system];
			}
			x_feed <- x_feed + [x_per_system];
			y_feed <- y_feed + [y_per_livestock];
		}

		// -------------------------------------------------------------------------------------
		// 1. Local availability
		//    for all r: sum over p, l of x_feed <= q_loc[r]
		// -------------------------------------------------------------------------------------
		loop r from: 0 to: nb_resources - 1 {
			list<pb_variable> used_locally <- [];
			loop p from: 0 to: nb_systems - 1 {
				loop l from: 0 to: nb_livestock - 1 {
					used_locally <- used_locally + x_feed[r][p][l];
				}
			}
			do post(sum_var(used_locally) <= q_loc[r]);
		}

		// -------------------------------------------------------------------------------------
		// 2. Intake capacity
		//    for all l: sum over r, p of (x_feed + y_feed) <= sum over p of slh[l][p] * ic[l]
		// -------------------------------------------------------------------------------------
		loop l from: 0 to: nb_livestock - 1 {
			list<pb_variable> consumed <- [];
			loop r from: 0 to: nb_resources - 1 {
				loop p from: 0 to: nb_systems - 1 {
					consumed <- consumed + x_feed[r][p][l];
					consumed <- consumed + y_feed[r][l][p];
				}
			}
			int total_intake <- 0;
			loop p from: 0 to: nb_systems - 1 { total_intake <- total_intake + slh[l][p] * ic[l]; }
			do post(sum_var(consumed) <= total_intake);
		}

		// -------------------------------------------------------------------------------------
		// 3. Nutritional requirements
		//    for all l: sum over r, p of (x_feed + y_feed) * zeta_rate >= sum over p of
		//    slh[l][p] * zeta_req[l]
		// -------------------------------------------------------------------------------------
		loop l from: 0 to: nb_livestock - 1 {
			list<pb_variable> terms <- [];
			list<int> coeffs <- [];
			loop r from: 0 to: nb_resources - 1 {
				loop p from: 0 to: nb_systems - 1 {
					terms <- terms + x_feed[r][p][l];
					coeffs <- coeffs + zeta_rate[r][l];
					terms <- terms + y_feed[r][l][p];
					coeffs <- coeffs + zeta_rate[r][l];
				}
			}
			int total_requirement <- 0;
			loop p from: 0 to: nb_systems - 1 {
				total_requirement <- total_requirement + slh[l][p] * zeta_req[l];
			}
			do post(scalar(terms, coeffs, ">=", total_requirement));
		}

		// -------------------------------------------------------------------------------------
		// 4. Composition of the ration: at most max_grain[l] percent of grain
		//    The natural formulation, sum(grain) / sum(total) <= max, is not linear. Multiplying
		//    both sides by the total turns it into sum(grain * (100 - max)) - sum(other * max) <= 0
		// -------------------------------------------------------------------------------------
		loop l from: 0 to: nb_livestock - 1 {
			list<pb_variable> terms <- [];
			list<int> coeffs <- [];
			loop r from: 0 to: nb_resources - 1 {
				int weight <- (r in grain_resources) ? (100 - max_grain[l]) : (0 - max_grain[l]);
				loop p from: 0 to: nb_systems - 1 {
					terms <- terms + x_feed[r][p][l];
					coeffs <- coeffs + weight;
					terms <- terms + y_feed[r][l][p];
					coeffs <- coeffs + weight;
				}
			}
			do post(scalar(terms, coeffs, "<=", 0));
		}

		// -------------------------------------------------------------------------------------
		// Objective: minimise the total cost of the feed, local and bought
		// -------------------------------------------------------------------------------------
		list<pb_variable> cost_terms <- [];
		list<int> cost_coeffs <- [];
		loop r from: 0 to: nb_resources - 1 {
			loop p from: 0 to: nb_systems - 1 {
				loop l from: 0 to: nb_livestock - 1 {
					cost_terms <- cost_terms + x_feed[r][p][l];
					cost_coeffs <- cost_coeffs + c_loc[r][p];
					cost_terms <- cost_terms + y_feed[r][l][p];
					cost_coeffs <- cost_coeffs + c_ext[r][p];
				}
			}
		}

		// Above 65536 values the plugin represents a domain by its bounds only, which is what
		// happens here: this variable is a total, never a decision to branch on.
		pb_variable total_cost <- int_var(pb, "TotalCost", 0, 500000);
		do post(scalar(cost_terms, cost_coeffs, "=", total_cost));

		solution best <- minimize(pb, total_cost, 20 #s);

		// -------------------------------------------------------------------------------------
		// Results
		// -------------------------------------------------------------------------------------
		if (best.exists) {
			write "=========================================";
			write "  OPTIMAL SOLUTION FOUND (ECONOM)";
			write "=========================================";
			write "Minimal feeding cost: " + value_of(best, total_cost) + " EUR";
			write "-----------------------------------------";
			write "Local allocations (x_feed):";
			loop r from: 0 to: nb_resources - 1 {
				loop p from: 0 to: nb_systems - 1 {
					loop l from: 0 to: nb_livestock - 1 {
						int quantity <- value_of(best, x_feed[r][p][l]);
						if (quantity > 0) {
							write "  resource " + r + ", system " + p + ", livestock " + l + ": " + quantity;
						}
					}
				}
			}
			write "Purchases (y_feed):";
			loop r from: 0 to: nb_resources - 1 {
				loop l from: 0 to: nb_livestock - 1 {
					loop p from: 0 to: nb_systems - 1 {
						int quantity <- value_of(best, y_feed[r][l][p]);
						if (quantity > 0) {
							write "  resource " + r + ", livestock " + l + ", system " + p + ": " + quantity;
						}
					}
				}
			}
			write "-----------------------------------------";
			write "" + pb.nodes + " nodes, " + pb.fails + " failures, " + pb.search_time + "s";
		} else {
			write "No solution found: the constraints are mutually contradictory";
		}
	}

}

experiment feeding_optimisation type: gui title: "Livestock feeding" { }

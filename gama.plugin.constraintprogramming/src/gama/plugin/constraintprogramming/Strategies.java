package gama.plugin.constraintprogramming;

import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.variables.IntVar;

import gama.annotations.doc;
import gama.annotations.example;
import gama.annotations.no_test;
import gama.annotations.operator;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.IList;

/**
 * The operators that configure how the solver explores the search space.
 *
 * <p>
 * None of them changes the set of solutions of a problem: whatever the strategy, the solver returns the same solutions,
 * only faster or slower. They can therefore be tried freely, and left out entirely when the default behaviour is good
 * enough.
 * </p>
 */
@SuppressWarnings ({ "rawtypes", "unchecked" })
public class Strategies {

	/**
	 * Creates a problem with lazy clause generation.
	 */
	@operator (
			value = "problem",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Creates a problem solved by the engine named as second operand. Accepted names: 'choco', the constraint engine, which handles everything the plugin exposes; 'choco_lcg', the same with lazy clause generation, where the solver derives a clause from each conflict and keeps it; and 'lp', the linear engine, which only accepts linear constraints but settles a linear model in one go where a constraint engine would search.",
			comment = "The engine is decided at creation, since variables and constraints are built differently. Nothing else in a model changes: the same declarations, the same expressions and the same way of reading a solution work on every engine, and an engine that cannot represent a constraint says so when it is posted.",
			examples = { @example (
					value = "problem p <- problem(\"my_problem\", \"lp\");",
					isExecutable = false) },
			see = { "use_strategy", "use_restarts" })
	@no_test
	public static GamaProblem problemWithBackend(final IScope scope, final String name, final String backend)
			throws GamaRuntimeException {
		final GamaProblem.Backend b = GamaProblem.Backend.named(backend);
		if (b == null) throw GamaRuntimeException.error("Unknown engine '" + backend
				+ "'. Expected one of: choco, choco_lcg, lp", scope);
		return new GamaProblem(name, b);
	}

	/**
	 * Builds the branching strategy designated by its name.
	 *
	 * @param scope
	 *            the current scope
	 * @param name
	 *            the name of the strategy
	 * @param vars
	 *            the variables to branch on
	 * @return the strategy, or null when the name designates the default search
	 */
	private static AbstractStrategy strategyNamed(final IScope scope, final String name, final IntVar[] vars)
			throws GamaRuntimeException {
		return switch (name) {
			case "default" -> null;
			case "input_order_lb" -> Search.inputOrderLBSearch(vars);
			case "input_order_ub" -> Search.inputOrderUBSearch(vars);
			case "min_dom_lb" -> Search.minDomLBSearch(vars);
			case "min_dom_ub" -> Search.minDomUBSearch(vars);
			case "random" -> Search.randomSearch(vars, scope.getRandom().between(0, Integer.MAX_VALUE));
			case "dom_over_w_deg" -> Search.domOverWDegSearch(vars);
			case "dom_over_w_deg_ref" -> Search.domOverWDegRefSearch(vars);
			case "activity_based" -> Search.activityBasedSearch(vars);
			case "conflict_history" -> Search.conflictHistorySearch(vars);
			case "failure_rate" -> Search.failureRateBasedSearch(vars);
			case "failure_length" -> Search.failureLengthBasedSearch(vars);
			case "pick_on_dom" -> Search.pickOnDom(vars);
			case "round_robin" -> Search.roundRobinSearch(vars);
			case "adaptive_round_robin" -> Search.adaptiveRoundRobinSearch(vars);
			default -> throw GamaRuntimeException.error("Unknown search strategy '" + name
					+ "'. Expected one of: default, input_order_lb, input_order_ub, min_dom_lb, min_dom_ub, random, "
					+ "dom_over_w_deg, dom_over_w_deg_ref, activity_based, conflict_history, failure_rate, "
					+ "failure_length, pick_on_dom, round_robin, adaptive_round_robin", scope);
		};
	}

	/**
	 * Sets the branching strategy over every integer variable of the problem.
	 */
	@operator (
			value = "use_strategy",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Chooses how the solver branches, over every integer variable of the problem, and returns the problem. Accepted names: 'default', 'input_order_lb', 'input_order_ub', 'min_dom_lb', 'min_dom_ub', 'random', 'dom_over_w_deg', 'dom_over_w_deg_ref', 'activity_based', 'conflict_history', 'failure_rate', 'failure_length', 'pick_on_dom', 'round_robin', 'adaptive_round_robin'.",
			comment = "A strategy never changes which solutions exist, only the order and the speed at which they are found. The adaptive ones ('dom_over_w_deg_ref', 'conflict_history', 'activity_based') learn from failures during the search and are the usual first thing to try on a problem that does not converge.",
			examples = { @example (
					value = "do use_strategy(p, \"dom_over_w_deg_ref\");",
					isExecutable = false) },
			see = { "with_last_conflict", "use_restarts" })
	@no_test
	public static GamaProblem useStrategy(final IScope scope, final GamaProblem problem, final String name)
			throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to configure a nil problem", scope);
		final IntVar[] all = problem.getModel().retrieveIntVars(true);
		final AbstractStrategy strategy = strategyNamed(scope, name, all);
		if (strategy == null) {
			Search.defaultSearch(problem.getModel());
		} else {
			problem.getSolver().setSearch(strategy);
		}
		return problem;
	}

	/**
	 * Sets the branching strategy over a chosen subset of variables.
	 */
	@operator (
			value = "use_strategy",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Chooses how the solver branches, over the variables given as third operand only, and returns the problem. The remaining variables are handled by a default strategy appended behind, so the search stays complete.",
			comment = "Restricting the branching to the real decisions is often the single most profitable setting. By default the solver also branches on the variables produced by sum_var, min_var and the like, and on the objective, although those are determined by the others and branching on them mostly wastes decisions.",
			examples = { @example (
					value = "do use_strategy(p, \"dom_over_w_deg_ref\", decisions);",
					isExecutable = false) },
			see = { "use_strategy" })
	@no_test
	public static GamaProblem useStrategy(final IScope scope, final GamaProblem problem, final String name,
			final IList<GamaVariable> vars) throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to configure a nil problem", scope);
		if (vars == null || vars.isEmpty()) return useStrategy(scope, problem, name);
		final AbstractStrategy strategy = strategyNamed(scope, name, CPUtils.intVars(scope, vars));
		if (strategy == null) {
			Search.defaultSearch(problem.getModel());
		} else {
			problem.getSolver().setSearch(strategy);
			// The declared strategy only covers part of the variables: without this, the search could stop on a node
			// where the others are still undecided.
			problem.getSolver().makeCompleteStrategy(true);
		}
		return problem;
	}

	/**
	 * Returns the strategy currently in use, installing the default one if none has been set.
	 *
	 * @param problem
	 *            the problem
	 * @return the current strategy
	 */
	private static AbstractStrategy currentStrategy(final GamaProblem problem) {
		final Solver solver = problem.getSolver();
		AbstractStrategy current = solver.getSearch();
		if (current == null) {
			Search.defaultSearch(problem.getModel());
			current = solver.getSearch();
		}
		return current;
	}

	/**
	 * Wraps the current strategy with last conflict reasoning.
	 */
	@operator (
			value = "with_last_conflict",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Adds last conflict reasoning on top of the strategy currently in use, and returns the problem. After a failure, the solver retries the variable involved in it before following its usual order.",
			comment = "Very cheap and rarely harmful: it is the first thing to add to any strategy.",
			examples = { @example (
					value = "do with_last_conflict(p);",
					isExecutable = false) },
			see = { "use_strategy", "with_conflict_ordering" })
	@no_test
	public static GamaProblem withLastConflict(final IScope scope, final GamaProblem problem)
			throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to configure a nil problem", scope);
		problem.getSolver().setSearch(Search.lastConflict(currentStrategy(problem)));
		return problem;
	}

	/**
	 * Wraps the current strategy with conflict ordering.
	 */
	@operator (
			value = "with_conflict_ordering",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Adds conflict ordering on top of the strategy currently in use, and returns the problem. A generalisation of last conflict, which keeps the whole sequence of variables involved in recent failures.",
			see = { "with_last_conflict" })
	@no_test
	public static GamaProblem withConflictOrdering(final IScope scope, final GamaProblem problem)
			throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to configure a nil problem", scope);
		problem.getSolver().setSearch(Search.conflictOrderingSearch(currentStrategy(problem)));
		return problem;
	}

	/**
	 * Wraps the current strategy with best bound value selection.
	 */
	@operator (
			value = "with_best_bound",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Makes the strategy try, for each variable, the bound that looks the most promising for the objective, and returns the problem. Only meaningful on an optimisation problem, and only if the strategy branches on integer variables.",
			see = { "use_strategy", "minimize" })
	@no_test
	public static GamaProblem withBestBound(final IScope scope, final GamaProblem problem)
			throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to configure a nil problem", scope);
		try {
			problem.getSolver().setSearch(Search.bestBound(currentStrategy(problem)));
		} catch (final ClassCastException e) {
			throw GamaRuntimeException
					.error("with_best_bound only applies to a strategy branching on integer variables", scope);
		}
		return problem;
	}

	/**
	 * Sets a restart policy.
	 */
	@operator (
			value = "use_restarts",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Makes the solver restart its search periodically, and returns the problem. Accepted policies: 'luby', 'geometric', 'linear', 'constant' and 'on_solution'. The third operand is the number of failures before the first restart; the policy decides how that number grows afterwards.",
			comment = "Restarting escapes a bad early decision that the search would otherwise pay for during a very long time. It only pays off with a strategy that learns, such as 'dom_over_w_deg_ref' or 'conflict_history', and with record_nogoods, which is what keeps the effort of a run from being lost at the next one. 'on_solution' restarts after each solution instead, which is useful for optimisation.",
			examples = { @example (
					value = "do use_restarts(p, \"luby\", 500);",
					isExecutable = false) },
			see = { "record_nogoods", "use_strategy" })
	@no_test
	public static GamaProblem useRestarts(final IScope scope, final GamaProblem problem, final String policy,
			final int cutoff) throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to configure a nil problem", scope);
		if (cutoff <= 0 && !"on_solution".equals(policy))
			throw GamaRuntimeException.error("The cutoff of a restart policy must be strictly positive", scope);
		final Solver solver = problem.getSolver();
		final FailCounter counter = new FailCounter(problem.getModel(), cutoff);
		switch (policy) {
			case "luby" -> solver.setLubyRestart(cutoff, counter, Integer.MAX_VALUE);
			case "geometric" -> solver.setGeometricalRestart(cutoff, 1.2, counter, Integer.MAX_VALUE);
			case "linear" -> solver.setLinearRestart(cutoff, counter, Integer.MAX_VALUE);
			case "constant" -> solver.setConstantRestart(cutoff, counter, Integer.MAX_VALUE);
			case "on_solution" -> solver.setRestartOnSolutions();
			default -> throw GamaRuntimeException.error("Unknown restart policy '" + policy
					+ "'. Expected one of: luby, geometric, linear, constant, on_solution", scope);
		}
		return problem;
	}

	/**
	 * Records nogoods from restarts.
	 */
	@operator (
			value = "record_nogoods",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Makes the solver remember, at each restart, the assignments it has already proven impossible, and returns the problem. Without it a restart throws away everything the previous run had established.",
			comment = "Only useful together with use_restarts.",
			examples = { @example (
					value = "do record_nogoods(p);",
					isExecutable = false) },
			see = { "use_restarts" })
	@no_test
	public static GamaProblem recordNogoods(final IScope scope, final GamaProblem problem)
			throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to configure a nil problem", scope);
		problem.getSolver().setNoGoodRecordingFromRestarts();
		return problem;
	}

}

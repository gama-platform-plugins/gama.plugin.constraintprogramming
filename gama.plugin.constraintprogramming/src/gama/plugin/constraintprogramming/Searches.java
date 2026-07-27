package gama.plugin.constraintprogramming;

import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.util.criteria.Criterion;

import gama.annotations.doc;
import gama.annotations.example;
import gama.annotations.no_test;
import gama.annotations.operator;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;

/**
 * The operators that run the solver on a problem and return what it found.
 *
 * <p>
 * Every search is guarded by a stop criterion bound to the interruption of the simulation, so that stopping or closing
 * an experiment during a long search actually stops it.
 * </p>
 *
 * <p>
 * A solver keeps its state between two calls: searching the same problem twice resumes where the previous search
 * stopped, which is what makes an anytime search spread over several simulation cycles possible. When independent
 * searches are wanted, a fresh problem has to be built.
 * </p>
 */
public class Searches {

	/**
	 * Runs the solver, with the interruption of the simulation and an optional time budget as stop criteria.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem to search
	 * @param objective
	 *            the variable to optimise, or null to look for any solution
	 * @param maximise
	 *            whether the objective has to be maximised
	 * @param within
	 *            the time budget in seconds, or null for no limit
	 * @return the solution found, possibly holding none
	 */
	private static GamaSolution run(final IScope scope, final GamaProblem problem, final GamaVariable objective,
			final boolean maximise, final Double within) throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to search a nil problem", scope);
		final Solver solver = problem.getSolver();
		final Criterion interrupted = scope::interrupted;
		solver.addStopCriterion(interrupted);
		if (within != null && within > 0) { solver.limitTime((long) (within * 1000)); }
		try {
			if (objective != null)
				return new GamaSolution(problem, solver.findOptimalSolution(objective.asIntVar(scope), maximise));
			return new GamaSolution(problem, solver.findSolution());
		} finally {
			solver.removeStopCriterion(interrupted);
		}
	}

	/**
	 * Looks for one solution.
	 */
	@operator (
			value = "search",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Runs the solver on a problem and returns the first solution it finds. The returned solution always exists as an object: its 'exists' attribute tells whether one was actually found.",
			comment = "The search stops as soon as the simulation is interrupted. Without a time budget, an unsatisfiable problem can keep the solver busy for a long time: prefer the two-operand form when the problem is not known to be easy.",
			examples = { @example (
					value = "solution sol <- search(p);",
					isExecutable = false) },
			see = { "minimize", "maximize", "all_solutions" })
	@no_test
	public static GamaSolution search(final IScope scope, final GamaProblem problem) throws GamaRuntimeException {
		return run(scope, problem, null, false, null);
	}

	/**
	 * Looks for one solution, within a time budget.
	 */
	@operator (
			value = "search",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Runs the solver on a problem for at most the duration given as second operand, and returns the first solution it finds.",
			examples = { @example (
					value = "solution sol <- search(p, 5 #s);",
					isExecutable = false) },
			see = { "search", "minimize", "maximize" })
	@no_test
	public static GamaSolution search(final IScope scope, final GamaProblem problem, final double within)
			throws GamaRuntimeException {
		return run(scope, problem, null, false, within);
	}

	/**
	 * Looks for the solution minimising a variable.
	 */
	@operator (
			value = "minimize",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Runs the solver on a problem and returns the solution giving the smallest value to the variable given as second operand. The solution is a proven optimum, unless the search was interrupted.",
			examples = { @example (
					value = "solution best <- minimize(p, total_cost);",
					isExecutable = false) },
			see = { "maximize", "search" })
	@no_test
	public static GamaSolution minimize(final IScope scope, final GamaProblem problem, final GamaVariable objective)
			throws GamaRuntimeException {
		return run(scope, problem, objective, false, null);
	}

	/**
	 * Looks for the solution minimising a variable, within a time budget.
	 */
	@operator (
			value = "minimize",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Runs the solver on a problem for at most the duration given as third operand, and returns the best solution found for the variable to minimise. Interrupting the search on a time budget returns the best solution found so far, which is not necessarily an optimum.",
			examples = { @example (
					value = "solution best <- minimize(p, total_cost, 20 #s);",
					isExecutable = false) },
			see = { "minimize", "maximize" })
	@no_test
	public static GamaSolution minimize(final IScope scope, final GamaProblem problem, final GamaVariable objective,
			final double within) throws GamaRuntimeException {
		return run(scope, problem, objective, false, within);
	}

	/**
	 * Looks for the solution maximising a variable.
	 */
	@operator (
			value = "maximize",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Runs the solver on a problem and returns the solution giving the largest value to the variable given as second operand.",
			examples = { @example (
					value = "solution best <- maximize(p, total_value);",
					isExecutable = false) },
			see = { "minimize", "search" })
	@no_test
	public static GamaSolution maximize(final IScope scope, final GamaProblem problem, final GamaVariable objective)
			throws GamaRuntimeException {
		return run(scope, problem, objective, true, null);
	}

	/**
	 * Looks for the solution maximising a variable, within a time budget.
	 */
	@operator (
			value = "maximize",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Runs the solver on a problem for at most the duration given as third operand, and returns the best solution found for the variable to maximise.",
			examples = { @example (
					value = "solution best <- maximize(p, total_value, 20 #s);",
					isExecutable = false) },
			see = { "maximize", "minimize" })
	@no_test
	public static GamaSolution maximize(final IScope scope, final GamaProblem problem, final GamaVariable objective,
			final double within) throws GamaRuntimeException {
		return run(scope, problem, objective, true, within);
	}

	/**
	 * Enumerates all the solutions.
	 */
	@operator (
			value = "all_solutions",
			content_type = GamaSolutionType.id,
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Runs the solver on a problem and returns every solution it finds. Beware that the number of solutions of a constraint problem is very often enormous: use the two-operand form unless the problem is known to be small.",
			examples = { @example (
					value = "list<solution> all <- all_solutions(p);",
					isExecutable = false) },
			see = { "search" })
	@no_test
	public static IList<GamaSolution> allSolutions(final IScope scope, final GamaProblem problem)
			throws GamaRuntimeException {
		return enumerate(scope, problem, 0);
	}

	/**
	 * Enumerates a bounded number of solutions.
	 */
	@operator (
			value = "all_solutions",
			content_type = GamaSolutionType.id,
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Runs the solver on a problem and returns at most as many solutions as the second operand indicates.",
			examples = { @example (
					value = "list<solution> five <- all_solutions(p, 5);",
					isExecutable = false) },
			see = { "all_solutions", "search" })
	@no_test
	public static IList<GamaSolution> allSolutions(final IScope scope, final GamaProblem problem, final int limit)
			throws GamaRuntimeException {
		return enumerate(scope, problem, limit);
	}

	/**
	 * Enumerates the solutions of a problem, recording each of them.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 * @param limit
	 *            the maximum number of solutions, 0 for all of them
	 * @return the solutions found
	 */
	private static IList<GamaSolution> enumerate(final IScope scope, final GamaProblem problem, final int limit)
			throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to search a nil problem", scope);
		final IList<GamaSolution> result = GamaListFactory.create(Types.get(GamaSolutionType.id));
		final Solver solver = problem.getSolver();
		final Criterion interrupted = scope::interrupted;
		solver.addStopCriterion(interrupted);
		try {
			while (solver.solve()) {
				result.add(new GamaSolution(problem, new Solution(problem.getModel()).record()));
				if (limit > 0 && result.size() >= limit) { break; }
				if (scope.interrupted()) { break; }
			}
		} finally {
			solver.removeStopCriterion(interrupted);
		}
		return result;
	}

}

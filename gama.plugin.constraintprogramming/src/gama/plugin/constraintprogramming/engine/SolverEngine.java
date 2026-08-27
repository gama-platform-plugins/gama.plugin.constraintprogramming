package gama.plugin.constraintprogramming.engine;

import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.IList;
import gama.plugin.constraintprogramming.GamaProblem;
import gama.plugin.constraintprogramming.GamaSolution;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * What the plugin asks of a solver, and all it asks of one.
 *
 * <p>
 * The GAML side of the plugin talks to this interface and never to a particular solver. A problem describes variables
 * and relations; an engine turns that description into whatever its own library wants, searches, and reports what the
 * search cost. Choco is one implementation among others rather than the shape everything else has to fit.
 * </p>
 *
 * <p>
 * What an engine cannot do it says so, rather than being asked at all: enumerating every solution, for instance, only
 * means something for a solver that searches, so the default implementation refuses it by naming the engine that does.
 * </p>
 */
public interface SolverEngine {

	/**
	 * Searches the problem.
	 *
	 * @param scope
	 *            the current scope, used to report errors and to interrupt a search the simulation abandoned
	 * @param problem
	 *            the problem to search
	 * @param objective
	 *            the term to optimise, or null to look for any solution
	 * @param maximise
	 *            whether the objective has to be maximised
	 * @param within
	 *            the time budget in seconds, or null for no limit
	 * @return the solution found, possibly holding none
	 */
	GamaSolution solve(IScope scope, GamaProblem problem, Term objective, boolean maximise, Double within)
			throws GamaRuntimeException;

	/**
	 * Enumerates the solutions of the problem.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 * @param limit
	 *            how many solutions to stop at, or 0 for all of them
	 * @return the solutions
	 */
	default IList<GamaSolution> enumerate(final IScope scope, final GamaProblem problem, final int limit)
			throws GamaRuntimeException {
		throw GamaRuntimeException.error("Enumerating solutions is only available with the 'choco' engine, and this "
				+ "problem uses '" + getLabel() + "'.", scope);
	}

	/**
	 * The branch and bound nodes the last search explored.
	 *
	 * <p>
	 * Zero is a valid answer rather than a missing one: a problem with no integer variable is settled without
	 * branching, and one small enough is decided before branching begins.
	 * </p>
	 *
	 * @return the node count
	 */
	long getNodes();

	/**
	 * The dead ends the last search reached by propagation.
	 *
	 * @return the failure count, zero for an engine that does not propagate
	 */
	default long getFails() { return 0; }

	/**
	 * How many solutions the last search found.
	 *
	 * @return the count, which an engine that does not enumerate reports as 1 or 0
	 */
	int getSolutions();

	/**
	 * How long the last search took.
	 *
	 * @return the duration in seconds
	 */
	double getSearchTime();

	/**
	 * The name this engine is known by in GAML.
	 *
	 * @return the label
	 */
	String getLabel();
}

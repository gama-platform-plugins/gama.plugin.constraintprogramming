package gama.plugin.constraintprogramming.engine;

import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.plugin.constraintprogramming.GamaProblem;
import gama.plugin.constraintprogramming.GamaSolution;
import gama.plugin.constraintprogramming.LinearCompiler;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * The linear engine bundled with Choco.
 *
 * <p>
 * It reports no counter of its own, only whether it reached a feasible answer, so the search time is measured around
 * the call and the node count stays at zero. It also cannot be interrupted: its branch and bound runs to the end
 * whatever budget it is given, which is why the budget is accepted and ignored rather than refused.
 * </p>
 */
public class LinearEngine implements SolverEngine {

	/** How long the last search took, in seconds. */
	private double searchTime;

	/** Whether the last search came back with an assignment. */
	private int solutions;

	@Override
	public GamaSolution solve(final IScope scope, final GamaProblem problem, final Term objective,
			final boolean maximise, final Double within) throws GamaRuntimeException {
		final long started = System.nanoTime();
		final GamaSolution result = LinearCompiler.solve(scope, problem, objective, maximise);
		searchTime = (System.nanoTime() - started) / 1e9;
		solutions = result.exists() ? 1 : 0;
		return result;
	}

	@Override
	public long getNodes() { return 0; }

	@Override
	public int getSolutions() { return solutions; }

	@Override
	public double getSearchTime() { return searchTime; }

	@Override
	public String getLabel() { return "lp"; }
}

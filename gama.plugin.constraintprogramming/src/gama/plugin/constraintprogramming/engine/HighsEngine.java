package gama.plugin.constraintprogramming.engine;

import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.plugin.constraintprogramming.GamaProblem;
import gama.plugin.constraintprogramming.GamaSolution;
import gama.plugin.constraintprogramming.highs.HighsCompiler;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * The HiGHS engine, a native linear and mixed integer solver loaded from a binary shipped with the plugin.
 *
 * <p>
 * It keeps its own figures, which the compiler reads back through its C API once a run is over. They describe what
 * HiGHS did, not what a Choco search would have done, so the node count is the size of its branch and bound tree and
 * is genuinely zero for a problem it settles without branching.
 * </p>
 */
public class HighsEngine implements SolverEngine {

	/** The branch and bound nodes the last run explored. */
	private long nodes;

	/** Whether the last run came back with an assignment. */
	private int solutions;

	/** How long the last run took, in seconds, as HiGHS reports it. */
	private double searchTime;

	@Override
	public GamaSolution solve(final IScope scope, final GamaProblem problem, final Term objective,
			final boolean maximise, final Double within) throws GamaRuntimeException {
		return HighsCompiler.solve(scope, problem, objective, maximise, within);
	}

	/**
	 * Records what the run just finished cost, as read from the solver.
	 *
	 * @param nodes
	 *            the branch and bound nodes explored
	 * @param solutions
	 *            1 when an assignment came back, 0 otherwise
	 * @param seconds
	 *            how long the run took
	 */
	public void record(final long nodes, final int solutions, final double seconds) {
		this.nodes = nodes;
		this.solutions = solutions;
		this.searchTime = seconds;
	}

	@Override
	public long getNodes() { return nodes; }

	@Override
	public int getSolutions() { return solutions; }

	@Override
	public double getSearchTime() { return searchTime; }

	@Override
	public String getLabel() { return "highs"; }
}

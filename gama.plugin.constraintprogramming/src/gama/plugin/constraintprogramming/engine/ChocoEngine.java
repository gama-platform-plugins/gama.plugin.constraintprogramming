package gama.plugin.constraintprogramming.engine;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.SettingsBuilder;
import org.chocosolver.util.criteria.Criterion;

import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;
import gama.plugin.constraintprogramming.ChocoCompiler;
import gama.plugin.constraintprogramming.GamaProblem;
import gama.plugin.constraintprogramming.GamaSolution;
import gama.plugin.constraintprogramming.GamaSolutionType;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * The constraint engine, backed by Choco.
 *
 * <p>
 * The only place in the plugin that holds a Choco model and a Choco solver. Everything Choco-specific the plugin
 * offers, from branching strategies to solution enumeration, is reached through this class rather than through the
 * problem, so that a problem solved by another engine has no Choco solver to hand out by accident.
 * </p>
 */
public class ChocoEngine implements SolverEngine {

	/** The model the variables and constraints are built into. */
	private final Model model;

	/** Whether lazy clause generation is on. */
	private final boolean lcg;

	/**
	 * Instantiates the engine.
	 *
	 * @param name
	 *            the name of the problem
	 * @param lcg
	 *            whether to enable lazy clause generation
	 */
	public ChocoEngine(final String name, final boolean lcg) {
		this.lcg = lcg;
		this.model = lcg ? new Model(name, new SettingsBuilder().setLCG(true).build()) : new Model(name);
	}

	/**
	 * The Choco model. Only the parts of the plugin that build Choco objects have any use for it.
	 *
	 * @return the model
	 */
	public Model getModel() { return model; }

	/**
	 * The Choco solver, for the operators that tune a search only a constraint engine performs.
	 *
	 * @return the solver
	 */
	public Solver getSolver() { return model.getSolver(); }

	@Override
	public GamaSolution solve(final IScope scope, final GamaProblem problem, final Term objective,
			final boolean maximise, final Double within) throws GamaRuntimeException {
		final Solver solver = getSolver();
		// The criterion lets a simulation that is being stopped interrupt a search already under way
		final Criterion interrupted = scope::interrupted;
		solver.addStopCriterion(interrupted);
		if (within != null && within > 0) { solver.limitTime((long) (within * 1000)); }
		try {
			if (objective != null) return new GamaSolution(problem,
					solver.findOptimalSolution(ChocoCompiler.compile(scope, problem, objective).intVar(), maximise));
			return new GamaSolution(problem, solver.findSolution());
		} finally {
			solver.removeStopCriterion(interrupted);
		}
	}

	@Override
	public IList<GamaSolution> enumerate(final IScope scope, final GamaProblem problem, final int limit)
			throws GamaRuntimeException {
		final IList<GamaSolution> result = GamaListFactory.create(Types.get(GamaSolutionType.id));
		final Solver solver = getSolver();
		final Criterion interrupted = scope::interrupted;
		solver.addStopCriterion(interrupted);
		try {
			while (solver.solve()) {
				result.add(new GamaSolution(problem, new Solution(model).record()));
				if (limit > 0 && result.size() >= limit) { break; }
				if (scope.interrupted()) { break; }
			}
		} finally {
			solver.removeStopCriterion(interrupted);
		}
		return result;
	}

	@Override
	public long getNodes() { return getSolver().getNodeCount(); }

	@Override
	public long getFails() { return getSolver().getFailCount(); }

	@Override
	public int getSolutions() { return (int) getSolver().getSolutionCount(); }

	@Override
	public double getSearchTime() { return getSolver().getTimeCount(); }

	@Override
	public String getLabel() { return lcg ? "choco_lcg" : "choco"; }
}

package gama.plugin.constraintprogramming;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.chocosolver.lp.LinearProgram;
import org.chocosolver.lp.MILP;

import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.plugin.constraintprogramming.terms.LinearForm;
import gama.plugin.constraintprogramming.terms.NonLinearException;
import gama.plugin.constraintprogramming.terms.Relation;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * Compiles a problem into a mixed integer linear program and solves it.
 *
 * <p>
 * Only the linear part of the plugin is representable here. A constraint that is not a relation between two linear
 * terms is refused when it is posted, or when it is flattened, with a message naming the sub-expression responsible.
 * </p>
 *
 * <p>
 * The solver bundled with Choco works in standard form: its variables are implicitly non-negative and unbounded above.
 * Every variable is therefore shifted, a variable of domain [lb, ub] being represented by a non-negative variable of
 * domain [0, ub - lb], with lb added back when the solution is read. Upper bounds become explicit rows.
 * </p>
 */
public class LinearCompiler {

	/** The index of each variable in the linear program. */
	private final Map<GamaVariable, Integer> index = new LinkedHashMap<>();

	/** The shift applied to each variable, that is, its lower bound. */
	private final Map<GamaVariable, Double> shift = new LinkedHashMap<>();

	/** The program being built. */
	private final MILP program = new MILP();

	/**
	 * Solves a problem with the linear engine.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 * @param objective
	 *            the variable to optimise, or null to look for any feasible assignment
	 * @param maximise
	 *            whether the objective has to be maximised
	 * @return the solution found, possibly holding none
	 */
	public static GamaSolution solve(final IScope scope, final GamaProblem problem, final GamaVariable objective,
			final boolean maximise) throws GamaRuntimeException {
		return solve(scope, problem, objective == null ? null : new Term.Var(objective), maximise);
	}

	/**
	 * Solves a problem with the linear engine, optimising a linear form rather than a single variable, which is what a
	 * file read in the MPS format gives.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 * @param objective
	 *            the linear form to optimise, or null to look for any feasible assignment
	 * @param maximise
	 *            whether it has to be maximised
	 * @return the solution found, possibly holding none
	 */
	public static GamaSolution solve(final IScope scope, final GamaProblem problem, final Term objective,
			final boolean maximise) throws GamaRuntimeException {
		final LinearCompiler compiler = new LinearCompiler();
		compiler.declare(scope, problem);
		compiler.constrain(scope, problem);
		compiler.objective(scope, objective, maximise);
		final LinearProgram.Status status = compiler.program.branchAndBound();
		if (status != LinearProgram.Status.FEASIBLE) return new GamaSolution(problem, (Map<String, Double>) null);
		return new GamaSolution(problem, compiler.read());
	}

	/**
	 * Declares one linear variable per decision variable of the problem, shifted to be non-negative.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 */
	private void declare(final IScope scope, final GamaProblem problem) throws GamaRuntimeException {
		// Choco requires every variable of a linear program to exist before the first row is added, so the upper
		// bounds are held back and written once the declarations are over.
		final Map<Integer, Double> spans = new LinkedHashMap<>();
		for (final GamaVariable v : problem.declaredVariables()) {
			if (v.isExpression()) { continue; }
			final GamaVariable.Kind kind = v.getVariableKind();
			if (kind == null || kind == GamaVariable.Kind.SET)
				throw GamaRuntimeException.error("The linear engine does not handle " + v.getVariableName()
						+ ", which is a " + v.getKind() + " variable", scope);
			final double lb = v.getLowerBound();
			final double ub = v.getUpperBound();
			final int i = switch (kind) {
				case BOOL -> program.makeBoolean();
				case INT -> program.makeInteger();
				case REAL -> program.makeVariable();
				default -> throw GamaRuntimeException.error("The linear engine does not handle "
						+ v.getVariableName() + ", which is a " + v.getKind() + " variable", scope);
			};
			if (Double.isInfinite(lb)) throw GamaRuntimeException.error("The variable " + v.getVariableName()
					+ " has no lower bound. The linear engine works in standard form, where every variable is shifted "
					+ "to be non-negative, which needs a finite lower bound.", scope);
			index.put(v, i);
			shift.put(v, lb);
			if (kind != GamaVariable.Kind.BOOL && !Double.isInfinite(ub) && ub - lb < Double.MAX_VALUE / 8) {
				spans.put(i, ub - lb);
			}
		}
		spans.forEach((i, span) -> {
			final HashMap<Integer, Double> row = new HashMap<>();
			row.put(i, 1.0);
			program.addLeq(row, span);
		});
	}

	/**
	 * Turns every posted relation into a row of the linear program.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 */
	private void constrain(final IScope scope, final GamaProblem problem) throws GamaRuntimeException {
		for (final GamaConstraint c : problem.getPosted()) {
			final Relation r = c.getRelation();
			if (r == null) throw GamaRuntimeException.error("The constraint " + c.getConstraintName()
					+ " cannot be handled by the linear engine", scope);
			final LinearForm form;
			try {
				form = LinearForm.of(r.left()).subtract(LinearForm.of(r.right()));
			} catch (final NonLinearException e) {
				throw GamaRuntimeException.error("The constraint " + r.describe()
						+ " cannot be handled by the linear engine: " + e.getMessage()
						+ ". Use the 'choco' engine for this problem.", scope);
			}
			final HashMap<Integer, Double> row = new HashMap<>();
			double bound = -form.getConstant();
			for (final Map.Entry<GamaVariable, Double> e : form.getCoefficients().entrySet()) {
				final Integer i = index.get(e.getKey());
				if (i == null) throw GamaRuntimeException.error(
						"The variable " + e.getKey().getVariableName() + " is not declared in this problem", scope);
				row.merge(i, e.getValue(), Double::sum);
				// x = y + lb, so the shift moves to the right hand side
				bound -= e.getValue() * shift.get(e.getKey());
			}
			if (row.isEmpty()) { continue; }
			switch (r.op()) {
				case LE -> program.addLeq(row, bound);
				case GE -> program.addGeq(row, bound);
				case EQ -> program.addEq(row, bound);
				// Strict comparisons over integers are the non-strict ones shifted by one
				case LT -> program.addLeq(row, bound - 1);
				case GT -> program.addGeq(row, bound + 1);
				case NE -> throw GamaRuntimeException.error("The constraint " + r.describe()
						+ " uses '!=', which a linear engine cannot express directly. Use the 'choco' engine, or "
						+ "model the disequality with a boolean variable.", scope);
			}
		}
	}

	/**
	 * Sets the objective of the linear program.
	 *
	 * @param scope
	 *            the current scope
	 * @param objective
	 *            the variable to optimise, or null
	 * @param maximise
	 *            whether it has to be maximised
	 */
	private void objective(final IScope scope, final Term objective, final boolean maximise)
			throws GamaRuntimeException {
		if (objective == null) return;
		final LinearForm form;
		try {
			form = LinearForm.of(objective);
		} catch (final NonLinearException e) {
			throw GamaRuntimeException
					.error("The objective " + objective.describe() + " is not linear: " + e.getMessage(), scope);
		}
		final HashMap<Integer, Double> row = new HashMap<>();
		for (final Map.Entry<GamaVariable, Double> e : form.getCoefficients().entrySet()) {
			final Integer i = index.get(e.getKey());
			if (i == null) throw GamaRuntimeException.error("The objective mentions " + e.getKey().getVariableName()
					+ ", which is not a declared variable of this problem", scope);
			row.merge(i, e.getValue(), Double::sum);
		}
		if (row.isEmpty()) return;
		program.setObjective(maximise, row);
	}

	/**
	 * Reads the solution back, undoing the shift.
	 *
	 * @return the value of each variable
	 */
	private Map<String, Double> read() {
		final Map<String, Double> values = new LinkedHashMap<>();
		index.forEach((v, i) -> values.put(v.getVariableName(), program.value(i) + shift.get(v)));
		return values;
	}

}

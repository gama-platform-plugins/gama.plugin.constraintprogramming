package gama.plugin.constraintprogramming.highs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.RealVar;

import com.sun.jna.Pointer;

import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.plugin.constraintprogramming.GamaConstraint;
import gama.plugin.constraintprogramming.GamaProblem;
import gama.plugin.constraintprogramming.GamaSolution;
import gama.plugin.constraintprogramming.GamaVariable;
import gama.plugin.constraintprogramming.terms.LinearForm;
import gama.plugin.constraintprogramming.terms.NonLinearException;
import gama.plugin.constraintprogramming.terms.Relation;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * Compiles a problem into the sparse form HiGHS expects, and solves it.
 *
 * <p>
 * Unlike the engine bundled with Choco, HiGHS takes the bounds of each column as they are, negative and infinite ones
 * included, so nothing has to be shifted. Rows carry a lower and an upper bound, which expresses every comparison
 * directly and a two-sided constraint in a single row.
 * </p>
 */
public class HighsCompiler {

	/** The value HiGHS reads as no bound. */
	private static final double INFINITY = 1.0e30;

	/** The index of each variable among the columns, in declaration order. */
	private final Map<GamaVariable, Integer> index = new LinkedHashMap<>();

	/** The variables, in the same order, so that the solution can be read back. */
	private final List<GamaVariable> columns = new ArrayList<>();

	/**
	 * Solves a problem with HiGHS.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 * @param objective
	 *            the linear form to optimise, or null to look for any feasible assignment
	 * @param maximise
	 *            whether it has to be maximised
	 * @param within
	 *            the time budget in seconds, or null for no limit
	 * @return the solution found, possibly holding none
	 */
	public static GamaSolution solve(final IScope scope, final GamaProblem problem, final Term objective,
			final boolean maximise, final Double within) throws GamaRuntimeException {
		final HighsLibrary highs = HighsLoader.library();
		if (highs == null) throw GamaRuntimeException.error("The HiGHS engine is not available on this machine: "
				+ HighsLoader.getFailure() + ". Use the 'choco' engine, or add the binary to the plugin.", scope);

		final HighsCompiler compiler = new HighsCompiler();
		compiler.declare(scope, problem);
		final int numCol = compiler.columns.size();
		if (numCol == 0) throw GamaRuntimeException.error("The problem declares no variable", scope);

		final List<double[]> rowBounds = new ArrayList<>();
		final List<int[]> rowIndices = new ArrayList<>();
		final List<double[]> rowValues = new ArrayList<>();
		compiler.rows(scope, problem, rowBounds, rowIndices, rowValues);

		final double[] colCost = new double[numCol];
		double offset = 0;
		if (objective != null) { offset = compiler.cost(scope, objective, colCost); }

		final Pointer handle = highs.Highs_create();
		if (handle == null) throw GamaRuntimeException.error("HiGHS refused to create a solver", scope);
		try {
			// HiGHS writes a log to the standard output by default, which has no place in the console of a simulation
			highs.Highs_setBoolOptionValue(handle, "output_flag", 0);
			if (within != null && within > 0) {
				highs.Highs_setDoubleOptionValue(handle, "time_limit", within);
			}
			return compiler.run(scope, problem, highs, handle, colCost, offset, maximise, rowBounds, rowIndices,
					rowValues);
		} finally {
			highs.Highs_destroy(handle);
		}
	}

	/**
	 * Gives each declared variable a column, with its bounds and its kind.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 */
	private void declare(final IScope scope, final GamaProblem problem) throws GamaRuntimeException {
		for (final GamaVariable v : problem.declaredVariables()) {
			if (v.isExpression()) { continue; }
			index.put(v, columns.size());
			columns.add(v);
		}
	}

	/**
	 * Reads the bounds and the kind of a column from the variable it stands for.
	 *
	 * @param scope
	 *            the current scope
	 * @param v
	 *            the variable
	 * @param lower
	 *            filled with the lower bound
	 * @param upper
	 *            filled with the upper bound
	 * @param integrality
	 *            filled with the kind
	 * @param i
	 *            the index of the column
	 */
	private static void describe(final IScope scope, final GamaVariable v, final double[] lower, final double[] upper,
			final int[] integrality, final int i) throws GamaRuntimeException {
		switch (v.getVariable()) {
			case BoolVar b -> {
				lower[i] = 0;
				upper[i] = 1;
				integrality[i] = HighsLibrary.VAR_TYPE_INTEGER;
			}
			case IntVar iv -> {
				lower[i] = iv.getLB();
				upper[i] = iv.getUB();
				integrality[i] = HighsLibrary.VAR_TYPE_INTEGER;
			}
			case RealVar rv -> {
				lower[i] = bound(rv.getLB());
				upper[i] = bound(rv.getUB());
				integrality[i] = HighsLibrary.VAR_TYPE_CONTINUOUS;
			}
			default -> throw GamaRuntimeException.error("The HiGHS engine does not handle " + v.getVariableName()
					+ ", which is a " + v.getKind() + " variable", scope);
		}
	}

	/**
	 * Brings a bound into the range HiGHS reads, where anything past a threshold means no bound at all.
	 *
	 * @param value
	 *            the bound
	 * @return the bound as HiGHS reads it
	 */
	private static double bound(final double value) {
		if (Double.isNaN(value)) return 0;
		if (value >= INFINITY) return INFINITY;
		if (value <= -INFINITY) return -INFINITY;
		return value;
	}

	/**
	 * Turns every posted relation into a row.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 * @param bounds
	 *            filled with the pair of bounds of each row
	 * @param indices
	 *            filled with the columns each row mentions
	 * @param values
	 *            filled with the coefficient of each of them
	 */
	private void rows(final IScope scope, final GamaProblem problem, final List<double[]> bounds,
			final List<int[]> indices, final List<double[]> values) throws GamaRuntimeException {
		for (final GamaConstraint c : problem.getPosted()) {
			final Relation r = c.getRelation();
			if (r == null) throw GamaRuntimeException.error("The constraint " + c.getConstraintName()
					+ " is a global constraint, which the HiGHS engine does not handle. Use the 'choco' engine.",
					scope);
			final LinearForm form;
			try {
				form = LinearForm.of(r.left()).subtract(LinearForm.of(r.right()));
			} catch (final NonLinearException e) {
				throw GamaRuntimeException.error("The constraint " + r.describe()
						+ " cannot be handled by the HiGHS engine: " + e.getMessage()
						+ ". Use the 'choco' engine for this problem.", scope);
			}
			final double rhs = -form.getConstant();
			final int size = form.getCoefficients().size();
			if (size == 0) { continue; }
			final int[] rowIndex = new int[size];
			final double[] rowValue = new double[size];
			int k = 0;
			for (final Map.Entry<GamaVariable, Double> e : form.getCoefficients().entrySet()) {
				final Integer i = index.get(e.getKey());
				if (i == null) throw GamaRuntimeException.error(
						"The variable " + e.getKey().getVariableName() + " is not declared in this problem", scope);
				rowIndex[k] = i;
				rowValue[k] = e.getValue();
				k++;
			}
			final double[] pair = switch (r.op()) {
				case LE -> new double[] { -INFINITY, rhs };
				case GE -> new double[] { rhs, INFINITY };
				case EQ -> new double[] { rhs, rhs };
				// Over integers a strict comparison is the non-strict one shifted by one
				case LT -> new double[] { -INFINITY, rhs - 1 };
				case GT -> new double[] { rhs + 1, INFINITY };
				case NE -> throw GamaRuntimeException.error("The constraint " + r.describe()
						+ " uses '!=', which a linear engine cannot express in a single row. Use the 'choco' engine.",
						scope);
			};
			bounds.add(pair);
			indices.add(rowIndex);
			values.add(rowValue);
		}
	}

	/**
	 * Fills the objective coefficients from a linear form.
	 *
	 * @param scope
	 *            the current scope
	 * @param objective
	 *            the form to optimise
	 * @param colCost
	 *            filled with the coefficient of each column
	 * @return the constant part of the objective
	 */
	private double cost(final IScope scope, final Term objective, final double[] colCost)
			throws GamaRuntimeException {
		final LinearForm form;
		try {
			form = LinearForm.of(objective);
		} catch (final NonLinearException e) {
			throw GamaRuntimeException
					.error("The objective " + objective.describe() + " is not linear: " + e.getMessage(), scope);
		}
		for (final Map.Entry<GamaVariable, Double> e : form.getCoefficients().entrySet()) {
			final Integer i = index.get(e.getKey());
			if (i == null) throw GamaRuntimeException.error("The objective mentions " + e.getKey().getVariableName()
					+ ", which is not a declared variable of this problem", scope);
			colCost[i] += e.getValue();
		}
		return form.getConstant();
	}

	/**
	 * Hands the model over, runs the solver and reads the answer.
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem
	 * @param highs
	 *            the library
	 * @param handle
	 *            the solver instance
	 * @param colCost
	 *            the objective coefficients
	 * @param offset
	 *            the constant part of the objective
	 * @param maximise
	 *            whether the objective has to be maximised
	 * @param bounds
	 *            the bounds of each row
	 * @param indices
	 *            the columns each row mentions
	 * @param values
	 *            the coefficients
	 * @return the solution
	 */
	private GamaSolution run(final IScope scope, final GamaProblem problem, final HighsLibrary highs,
			final Pointer handle, final double[] colCost, final double offset, final boolean maximise,
			final List<double[]> bounds, final List<int[]> indices, final List<double[]> values)
			throws GamaRuntimeException {
		final int numCol = columns.size();
		final int numRow = bounds.size();

		final double[] colLower = new double[numCol];
		final double[] colUpper = new double[numCol];
		final int[] integrality = new int[numCol];
		boolean anyInteger = false;
		for (int i = 0; i < numCol; i++) {
			describe(scope, columns.get(i), colLower, colUpper, integrality, i);
			anyInteger |= integrality[i] == HighsLibrary.VAR_TYPE_INTEGER;
		}

		int nnz = 0;
		for (final int[] row : indices) { nnz += row.length; }
		final int[] aStart = new int[numRow + 1];
		final int[] aIndex = new int[nnz];
		final double[] aValue = new double[nnz];
		final double[] rowLower = new double[numRow];
		final double[] rowUpper = new double[numRow];
		int k = 0;
		for (int r = 0; r < numRow; r++) {
			aStart[r] = k;
			rowLower[r] = bounds.get(r)[0];
			rowUpper[r] = bounds.get(r)[1];
			final int[] rowIndex = indices.get(r);
			final double[] rowValue = values.get(r);
			for (int j = 0; j < rowIndex.length; j++) {
				aIndex[k] = rowIndex[j];
				aValue[k] = rowValue[j];
				k++;
			}
		}
		aStart[numRow] = k;

		final int sense = maximise ? HighsLibrary.OBJ_SENSE_MAXIMIZE : HighsLibrary.OBJ_SENSE_MINIMIZE;
		final int status = anyInteger
				? highs.Highs_passMip(handle, numCol, numRow, nnz, HighsLibrary.MATRIX_FORMAT_ROWWISE, sense, offset,
						colCost, colLower, colUpper, rowLower, rowUpper, aStart, aIndex, aValue, integrality)
				: highs.Highs_passLp(handle, numCol, numRow, nnz, HighsLibrary.MATRIX_FORMAT_ROWWISE, sense, offset,
						colCost, colLower, colUpper, rowLower, rowUpper, aStart, aIndex, aValue);
		if (status != 0) throw GamaRuntimeException
				.error("HiGHS refused the model, with status " + status, scope);

		highs.Highs_run(handle);
		final int model = highs.Highs_getModelStatus(handle);
		if (model != HighsLibrary.MODEL_STATUS_OPTIMAL && model != HighsLibrary.MODEL_STATUS_TIME_LIMIT
				&& model != HighsLibrary.MODEL_STATUS_ITERATION_LIMIT
				&& model != HighsLibrary.MODEL_STATUS_INTERRUPT)
			return new GamaSolution(problem, (Map<String, Double>) null);

		final double[] colValue = new double[numCol];
		if (highs.Highs_getSolution(handle, colValue, null, null, null) != 0)
			return new GamaSolution(problem, (Map<String, Double>) null);

		final Map<String, Double> solution = new LinkedHashMap<>();
		for (int i = 0; i < numCol; i++) { solution.put(columns.get(i).getVariableName(), colValue[i]); }
		return new GamaSolution(problem, solution);
	}

}

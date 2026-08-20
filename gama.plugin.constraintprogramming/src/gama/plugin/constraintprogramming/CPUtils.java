package gama.plugin.constraintprogramming;

import java.util.List;

import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.SetVar;

import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.Cast;
import gama.api.runtime.scope.IScope;

/**
 * Conversion helpers between GAML containers and the Java arrays expected by the Choco factories. Choco works almost
 * exclusively with arrays, which the GAML operator processor forbids in signatures, so every operator of this plugin
 * takes lists and converts them here.
 */
public class CPUtils {

	/** The category under which all the operators of the plugin are documented. */
	public static final String CATEGORY = "Constraint programming";

	/**
	 * Refuses an operator that only the constraint engine can honour.
	 *
	 * <p>
	 * The derived variables and the reification link a new variable to its operands through a constraint posted in
	 * Choco. A linear engine reads the relations recorded by the plugin and never sees that link, so the variable
	 * would be left free and the answer would be quietly wrong. Refusing is the only safe behaviour.
	 * </p>
	 *
	 * @param scope
	 *            the current scope
	 * @param problem
	 *            the problem the operator is applied to
	 * @param operator
	 *            the name of the operator, for the message
	 * @param alternative
	 *            what to write instead, or null when there is no direct equivalent
	 */
	public static void requireConstraintEngine(final IScope scope, final GamaProblem problem, final String operator,
			final String alternative) throws GamaRuntimeException {
		if (problem == null || !problem.isLinear()) return;
		throw GamaRuntimeException.error(operator + " is only available with the 'choco' engine, and this problem uses '"
				+ problem.getBackend().getLabel() + "'."
				+ (alternative == null ? "" : " " + alternative), scope);
	}

	/**
	 * Converts a list of GAML variables into an array of Choco integer variables.
	 *
	 * @param scope
	 *            the current scope
	 * @param vars
	 *            the variables
	 * @return the array of IntVar
	 * @throws GamaRuntimeException
	 *             if the list is empty, contains nil, or contains a variable that is not an int variable
	 */
	public static IntVar[] intVars(final IScope scope, final List<GamaVariable> vars) throws GamaRuntimeException {
		checkNotEmpty(scope, vars);
		final IntVar[] result = new IntVar[vars.size()];
		for (int i = 0; i < result.length; i++) { result[i] = variableAt(scope, vars, i).asIntVar(scope); }
		return result;
	}

	/**
	 * Converts a list of GAML variables into an array of Choco boolean variables.
	 *
	 * @param scope
	 *            the current scope
	 * @param vars
	 *            the variables
	 * @return the array of BoolVar
	 * @throws GamaRuntimeException
	 *             if one of the variables is not a boolean variable
	 */
	public static BoolVar[] boolVars(final IScope scope, final List<GamaVariable> vars) throws GamaRuntimeException {
		checkNotEmpty(scope, vars);
		final BoolVar[] result = new BoolVar[vars.size()];
		for (int i = 0; i < result.length; i++) { result[i] = variableAt(scope, vars, i).asBoolVar(scope); }
		return result;
	}

	/**
	 * Converts a list of GAML variables into an array of Choco set variables.
	 *
	 * @param scope
	 *            the current scope
	 * @param vars
	 *            the variables
	 * @return the array of SetVar
	 * @throws GamaRuntimeException
	 *             if one of the variables is not a set variable
	 */
	public static SetVar[] setVars(final IScope scope, final List<GamaVariable> vars) throws GamaRuntimeException {
		checkNotEmpty(scope, vars);
		final SetVar[] result = new SetVar[vars.size()];
		for (int i = 0; i < result.length; i++) { result[i] = variableAt(scope, vars, i).asSetVar(scope); }
		return result;
	}

	/**
	 * Converts a GAML list into an array of ints, casting each element.
	 *
	 * @param scope
	 *            the current scope
	 * @param values
	 *            the values
	 * @return the array of ints, empty if the list is nil
	 */
	public static int[] ints(final IScope scope, final List<Integer> values) {
		if (values == null) return new int[0];
		final int[] result = new int[values.size()];
		for (int i = 0; i < result.length; i++) {
			final Integer v = Cast.asInt(scope, values.get(i));
			if (v == null) throw GamaRuntimeException.error("nil found at index " + i + " of a list of int expected by a constraint", scope);
			result[i] = v;
		}
		return result;
	}

	/**
	 * Returns the problem to which a list of variables belongs, checking that they all belong to the same one. Building
	 * a constraint over variables coming from two different problems is a modelling error that Choco would only report
	 * much later, in a far less readable way.
	 *
	 * @param scope
	 *            the current scope
	 * @param vars
	 *            the variables
	 * @return the problem they all belong to
	 * @throws GamaRuntimeException
	 *             if the list is empty or if the variables belong to different problems
	 */
	public static GamaProblem problemOf(final IScope scope, final List<GamaVariable> vars) throws GamaRuntimeException {
		checkNotEmpty(scope, vars);
		final GamaProblem p = variableAt(scope, vars, 0).getProblem();
		for (int i = 1; i < vars.size(); i++) {
			if (variableAt(scope, vars, i).getProblem() != p) throw GamaRuntimeException.error(
					"All the variables of a constraint must belong to the same problem", scope);
		}
		return p;
	}

	/**
	 * Returns the problem of a single variable.
	 *
	 * @param scope
	 *            the current scope
	 * @param v
	 *            the variable
	 * @return its problem
	 * @throws GamaRuntimeException
	 *             if the variable is nil
	 */
	public static GamaProblem problemOf(final IScope scope, final GamaVariable v) throws GamaRuntimeException {
		if (v == null) throw GamaRuntimeException.error("A nil variable was passed to a constraint", scope);
		return v.getProblem();
	}

	/**
	 * Checks that a list of variables is usable.
	 *
	 * @param scope
	 *            the current scope
	 * @param vars
	 *            the variables
	 */
	private static void checkNotEmpty(final IScope scope, final List<GamaVariable> vars) throws GamaRuntimeException {
		if (vars == null || vars.isEmpty())
			throw GamaRuntimeException.error("An empty list of variables was passed to a constraint", scope);
	}

	/**
	 * Returns the variable at a given index, checking that it is not nil.
	 *
	 * @param scope
	 *            the current scope
	 * @param vars
	 *            the variables
	 * @param i
	 *            the index
	 * @return the variable
	 */
	private static GamaVariable variableAt(final IScope scope, final List<GamaVariable> vars, final int i)
			throws GamaRuntimeException {
		final GamaVariable v = vars.get(i);
		if (v == null)
			throw GamaRuntimeException.error("nil found at index " + i + " of a list of variables", scope);
		return v;
	}

}

package gama.plugin.constraintprogramming;

import org.chocosolver.solver.variables.IntVar;

import gama.annotations.doc;
import gama.annotations.example;
import gama.annotations.no_test;
import gama.annotations.operator;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;
import gama.api.types.matrix.IMatrix;

/**
 * The operators that declare variables in a problem, and those that derive a new variable from existing ones.
 *
 * <p>
 * Declaring a variable modifies the problem it is declared in: these operators are not pure, which is why none of them
 * is marked as {@code can_be_const}.
 * </p>
 */
public class Variables {

	/**
	 * Above this domain size, an integer variable is represented by its bounds only rather than by the enumeration of
	 * its values. An enumerated domain allocates one bit per value, so a variable declared over the whole int range
	 * would cost several megabytes on its own.
	 */
	private static final int ENUMERATION_THRESHOLD = 65536;

	/**
	 * Converts a bound expressed as a float in GAML into an int usable by Choco. Choco caps the domains of its integer
	 * variables to Integer.MAX_VALUE / 100 to keep a margin against overflows inside the propagators, so #infinity, or
	 * any value beyond that cap, is clamped rather than passed through.
	 *
	 * @param bound
	 *            the bound
	 * @param lower
	 *            whether it is a lower bound (used when the value is not a number)
	 * @return an int within the bounds accepted by Choco
	 */
	private static int clamp(final double bound, final boolean lower) {
		if (Double.isNaN(bound)) return lower ? IntVar.MIN_INT_BOUND : IntVar.MAX_INT_BOUND;
		if (bound <= IntVar.MIN_INT_BOUND) return IntVar.MIN_INT_BOUND;
		if (bound >= IntVar.MAX_INT_BOUND) return IntVar.MAX_INT_BOUND;
		return (int) Math.round(bound);
	}

	/**
	 * Declares an integer variable in a problem.
	 */
	@operator (
			value = "int_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Declares an integer variable in a problem, with a domain going from the third to the fourth operand, and returns it.",
			comment = "Bounds are clamped to the range supported by the solver, so #infinity (or -#infinity) can be used to declare an unbounded variable. Beyond 65536 values, the domain is represented by its bounds only, which uses far less memory but propagates less precisely.",
			examples = { @example (
					value = "pb_variable x <- int_var(p, \"x\", 1, 9);",
					isExecutable = false) },
			see = { "int_vars", "bool_var", "real_var", "set_var" })
	@no_test
	public static GamaVariable intVar(final IScope scope, final GamaProblem problem, final String name, final double lb,
			final double ub) throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Cannot declare the variable " + name + " in a nil problem", scope);
		final int low = clamp(lb, true);
		final int high = clamp(ub, false);
		if (low > high) throw GamaRuntimeException
				.error("The domain of " + name + " is empty: its lower bound " + low + " is greater than its upper bound " + high, scope);
		final boolean bounded = (long) high - low > ENUMERATION_THRESHOLD;
		return problem.register(GamaVariable.ofInt(problem, name, low, high, bounded));
	}

	/**
	 * Declares an integer variable over an explicit set of values.
	 */
	@operator (
			value = "int_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Declares an integer variable in a problem, whose domain is the explicit list of values given as third operand, and returns it.",
			examples = { @example (
					value = "pb_variable x <- int_var(p, \"x\", [1, 3, 7]);",
					isExecutable = false) },
			see = { "int_var", "int_vars" })
	@no_test
	public static GamaVariable intVarIn(final IScope scope, final GamaProblem problem, final String name,
			final IList<Integer> values) throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Cannot declare the variable " + name + " in a nil problem", scope);
		final int[] domain = CPUtils.ints(scope, values);
		if (domain.length == 0)
			throw GamaRuntimeException.error("The domain given to the variable " + name + " is empty", scope);
		return problem.register(GamaVariable.ofInt(problem, name, domain));
	}

	/**
	 * Declares an array of integer variables.
	 */
	@operator (
			value = "int_vars",
			content_type = GamaVariableType.id,
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Declares a list of integer variables sharing the same domain, named after the second operand followed by their index, and returns them.",
			examples = { @example (
					value = "list<pb_variable> queens <- int_vars(p, \"Q\", 8, 1, 8);",
					isExecutable = false) },
			see = { "int_var" })
	@no_test
	public static IList<GamaVariable> intVars(final IScope scope, final GamaProblem problem, final String name,
			final int number, final double lb, final double ub) throws GamaRuntimeException {
		final IList<GamaVariable> result = GamaListFactory.create(Types.get(GamaVariableType.id));
		for (int i = 0; i < number; i++) { result.add(intVar(scope, problem, name + "_" + i, lb, ub)); }
		return result;
	}

	/**
	 * Declares a boolean variable.
	 */
	@operator (
			value = "bool_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Declares a boolean variable in a problem and returns it. A boolean variable is an integer variable whose domain is [0, 1], and can be used wherever an integer variable is expected.",
			examples = { @example (
					value = "pb_variable b <- bool_var(p, \"b\");",
					isExecutable = false) },
			see = { "bool_vars", "int_var", "reify" })
	@no_test
	public static GamaVariable boolVar(final IScope scope, final GamaProblem problem, final String name)
			throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Cannot declare the variable " + name + " in a nil problem", scope);
		return problem.register(GamaVariable.ofBool(problem, name));
	}

	/**
	 * Declares an array of boolean variables.
	 */
	@operator (
			value = "bool_vars",
			content_type = GamaVariableType.id,
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Declares a list of boolean variables, named after the second operand followed by their index, and returns them.",
			see = { "bool_var" })
	@no_test
	public static IList<GamaVariable> boolVars(final IScope scope, final GamaProblem problem, final String name,
			final int number) throws GamaRuntimeException {
		final IList<GamaVariable> result = GamaListFactory.create(Types.get(GamaVariableType.id));
		for (int i = 0; i < number; i++) { result.add(boolVar(scope, problem, name + "_" + i)); }
		return result;
	}

	/**
	 * Declares a real variable.
	 */
	@operator (
			value = "real_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Declares a real (continuous) variable in a problem and returns it.",
			comment = "Real variables are only usable with the few constraints that support them; without the Ibex library, the solver reasons on their bounds only.",
			see = { "int_var" })
	@no_test
	public static GamaVariable realVar(final IScope scope, final GamaProblem problem, final String name, final double lb,
			final double ub) throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Cannot declare the variable " + name + " in a nil problem", scope);
		if (lb > ub) throw GamaRuntimeException.error("The domain of " + name + " is empty", scope);
		return problem.register(GamaVariable.ofReal(problem, name, lb, ub));
	}

	/**
	 * Declares a set variable.
	 */
	@operator (
			value = "set_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Declares a set variable in a problem and returns it. The third operand lists the values that the set necessarily contains, the fourth the values it may contain. Only available with the 'choco' engine.",
			examples = { @example (
					value = "pb_variable s <- set_var(p, \"s\", [], [1, 2, 3, 4]);",
					isExecutable = false) },
			see = { "int_var" })
	@no_test
	public static GamaVariable setVar(final IScope scope, final GamaProblem problem, final String name,
			final IList<Integer> mandatory, final IList<Integer> possible) throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Cannot declare the variable " + name + " in a nil problem", scope);
		CPUtils.requireConstraintEngine(scope, problem, "set_var", "A linear engine has no notion of a set variable.");
		return problem
				.register(problem.getModel().setVar(name, CPUtils.ints(scope, mandatory), CPUtils.ints(scope, possible)));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Derived variables. Each of them declares a new variable in the problem and links it to its operands, either
	// through a view (no propagator, no cost) or through a channelling constraint posted immediately.
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * The sum of a list of variables.
	 */
	@operator (
			value = "sum_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the sum of the variables given as operand. Only available with the 'choco' engine.",
			comment = "Named sum_var rather than sum to avoid any ambiguity with the sum operator of the core library.",
			examples = { @example (
					value = "pb_variable total <- sum_var(loads);",
					isExecutable = false) },
			see = { "min_var", "max_var", "scalar" })
	@no_test
	public static GamaVariable sumVar(final IScope scope, final IList<GamaVariable> vars) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		CPUtils.requireConstraintEngine(scope, p, "sum_var", "Write the sum as an expression instead, as in a + b + c.");
		return p.register(p.getModel().sum(p.newName("sum"), CPUtils.intVars(scope, vars)));
	}

	/**
	 * The minimum of a list of variables.
	 */
	@operator (
			value = "min_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the smallest value taken by the variables given as operand. Only available with the 'choco' engine.",
			see = { "max_var", "sum_var", "arg_min_var" })
	@no_test
	public static GamaVariable minVar(final IScope scope, final IList<GamaVariable> vars) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		CPUtils.requireConstraintEngine(scope, p, "min_var", null);
		return p.register(p.getModel().min(p.newName("min"), CPUtils.intVars(scope, vars)));
	}

	/**
	 * The maximum of a list of variables.
	 */
	@operator (
			value = "max_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the largest value taken by the variables given as operand. Only available with the 'choco' engine.",
			see = { "min_var", "sum_var", "arg_max_var" })
	@no_test
	public static GamaVariable maxVar(final IScope scope, final IList<GamaVariable> vars) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		CPUtils.requireConstraintEngine(scope, p, "max_var", null);
		return p.register(p.getModel().max(p.newName("max"), CPUtils.intVars(scope, vars)));
	}

	/**
	 * The number of variables taking a given value.
	 */
	@operator (
			value = "count_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the number of variables of the first operand taking the value given as second operand. Only available with the 'choco' engine.",
			examples = { @example (
					value = "pb_variable nb_idle <- count_var(slots, 0);",
					isExecutable = false) },
			see = { "n_values", "global_cardinality" })
	@no_test
	public static GamaVariable countVar(final IScope scope, final IList<GamaVariable> vars, final int value)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		CPUtils.requireConstraintEngine(scope, p, "count_var", null);
		return p.register(p.getModel().count(p.newName("count"), value, CPUtils.intVars(scope, vars)));
	}

	/**
	 * The index of the smallest variable.
	 */
	@operator (
			value = "arg_min_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the index, starting at 0, of the smallest variable of the operand. Only available with the 'choco' engine.",
			see = { "arg_max_var", "min_var" })
	@no_test
	public static GamaVariable argMinVar(final IScope scope, final IList<GamaVariable> vars) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		CPUtils.requireConstraintEngine(scope, p, "arg_min_var", null);
		return p.register(p.getModel().argmin(p.newName("argmin"), CPUtils.intVars(scope, vars)));
	}

	/**
	 * The index of the largest variable.
	 */
	@operator (
			value = "arg_max_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the index, starting at 0, of the largest variable of the operand. Only available with the 'choco' engine.",
			see = { "arg_min_var", "max_var" })
	@no_test
	public static GamaVariable argMaxVar(final IScope scope, final IList<GamaVariable> vars) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		CPUtils.requireConstraintEngine(scope, p, "arg_max_var", null);
		return p.register(p.getModel().argmax(p.newName("argmax"), CPUtils.intVars(scope, vars)));
	}

	/**
	 * The value read in a table at a variable index.
	 */
	@operator (
			value = "element_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the value read in the table given as first operand, at the index given by the variable of the second operand. Indices start at 0. Only available with the 'choco' engine.",
			examples = { @example (
					value = "pb_variable cost <- element_var([10, 4, 7], choice);",
					isExecutable = false) },
			see = { "element" })
	@no_test
	public static GamaVariable elementVar(final IScope scope, final IList<Integer> table, final GamaVariable index)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, index);
		CPUtils.requireConstraintEngine(scope, p, "element_var", null);
		return p.register(
				p.getModel().element(p.newName("element"), CPUtils.ints(scope, table), index.asIntVar(scope), 0));
	}

	/**
	 * The value read in one row of a matrix at a variable index.
	 */
	@operator (
			value = "element_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the value read in the matrix given as first operand, on the row given as second, at the column given by the variable of the third. Indices start at 0. Only available with the 'choco' engine.",
			examples = { @example (
					value = "pb_variable leg <- element_var(distances, i, next[i]);",
					isExecutable = false) },
			see = { "element_var", "element" })
	@no_test
	public static GamaVariable elementVar(final IScope scope, final IMatrix table, final int row,
			final GamaVariable index) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, index);
		CPUtils.requireConstraintEngine(scope, p, "element_var", null);
		if (table == null) throw GamaRuntimeException.error("A nil matrix was given to element_var", scope);
		final int cols = table.getCols(scope);
		final int[] line = new int[cols];
		for (int c = 0; c < cols; c++) {
			final Integer v = gama.api.gaml.types.Cast.asInt(scope, table.get(scope, c, row));
			if (v == null) throw GamaRuntimeException
					.error("nil found at row " + row + ", column " + c + " of a matrix", scope);
			line[c] = v;
		}
		return p.register(p.getModel().element(p.newName("element"), line, index.asIntVar(scope), 0));
	}

	/**
	 * The remainder of a variable.
	 */
	@operator (
			value = "mod_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the remainder of the euclidean division of the first operand by the second. Only available with the 'choco' engine.",
			see = { "abs_var" })
	@no_test
	public static GamaVariable modVar(final IScope scope, final GamaVariable var, final int divisor)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		CPUtils.requireConstraintEngine(scope, p, "mod_var", "Write it as an expression instead, as in x mod k.");
		if (divisor == 0) throw GamaRuntimeException.error("Division by zero in mod_var", scope);
		return p.register(p.getModel().mod(p.newName("mod"), var.asIntVar(scope), divisor));
	}

	/**
	 * The absolute value of a variable.
	 */
	@operator (
			value = "abs_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the absolute value of the operand. Implemented as a view: it costs neither a propagator nor a search decision. Only available with the 'choco' engine.",
			see = { "neg_var", "offset_var", "scale_var" })
	@no_test
	public static GamaVariable absVar(final IScope scope, final GamaVariable var) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		CPUtils.requireConstraintEngine(scope, p, "abs_var", null);
		return p.register(p.getModel().abs(var.asIntVar(scope)));
	}

	/**
	 * The opposite of a variable.
	 */
	@operator (
			value = "neg_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the opposite of the operand. Implemented as a view. Only available with the 'choco' engine.",
			see = { "abs_var", "offset_var", "scale_var" })
	@no_test
	public static GamaVariable negVar(final IScope scope, final GamaVariable var) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		CPUtils.requireConstraintEngine(scope, p, "neg_var", "Write it as an expression instead, as in -x.");
		return p.register(p.getModel().neg(var.asIntVar(scope)));
	}

	/**
	 * A variable shifted by a constant.
	 */
	@operator (
			value = "offset_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the first operand plus the constant given as second operand. Implemented as a view. Only available with the 'choco' engine.",
			see = { "scale_var", "neg_var" })
	@no_test
	public static GamaVariable offsetVar(final IScope scope, final GamaVariable var, final int offset)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		CPUtils.requireConstraintEngine(scope, p, "offset_var", "Write it as an expression instead, as in x + k.");
		return p.register(p.getModel().offset(var.asIntVar(scope), offset));
	}

	/**
	 * A variable multiplied by a constant.
	 */
	@operator (
			value = "scale_var",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a new variable constrained to be equal to the first operand multiplied by the constant given as second operand. Implemented as a view. Only available with the 'choco' engine.",
			see = { "offset_var", "neg_var" })
	@no_test
	public static GamaVariable scaleVar(final IScope scope, final GamaVariable var, final int factor)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		CPUtils.requireConstraintEngine(scope, p, "scale_var", "Write it as an expression instead, as in k * x.");
		return p.register(p.getModel().mul(var.asIntVar(scope), factor));
	}

	/**
	 * Retrieves a variable by its name.
	 */
	@operator (
			value = "variable_named",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns the variable declared under this name in the problem, or nil if there is none.",
			examples = { @example (
					value = "pb_variable x <- variable_named(p, \"Q_0\");",
					isExecutable = false) })
	@no_test
	public static GamaVariable variableNamed(final IScope scope, final GamaProblem problem, final String name) {
		if (problem == null) return null;
		return problem.getVariable(name);
	}

	/**
	 * Reads the value of a variable in a solution.
	 */
	@operator (
			value = "value_of",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns the value taken by the variable given as second operand in the solution given as first operand, or nil if no solution was found.",
			examples = { @example (
					value = "int first_queen <- value_of(sol, queens[0]);",
					isExecutable = false) },
			see = { "values_of" })
	@no_test
	public static Integer valueOf(final IScope scope, final GamaSolution solution, final GamaVariable variable)
			throws GamaRuntimeException {
		if (solution == null) return null;
		return solution.valueOf(scope, variable);
	}

	/**
	 * Reads the values of a list of variables in a solution.
	 */
	@operator (
			value = "values_of",
			content_type = IType.INT,
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns the values taken by the variables given as second operand in the solution given as first operand, in the same order.",
			examples = { @example (
					value = "list<int> positions <- values_of(sol, queens);",
					isExecutable = false) },
			see = { "value_of" })
	@no_test
	public static IList<Integer> valuesOf(final IScope scope, final GamaSolution solution,
			final IList<GamaVariable> variables) throws GamaRuntimeException {
		final IList<Integer> result = GamaListFactory.create(Types.INT);
		if (solution == null || variables == null) return result;
		for (final GamaVariable v : variables) { result.add(solution.valueOf(scope, v)); }
		return result;
	}

	/**
	 * Reads the elements of a set variable in a solution.
	 */
	@operator (
			value = "set_value_of",
			content_type = IType.INT,
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns the elements of the set variable given as second operand in the solution given as first operand.",
			see = { "value_of" })
	@no_test
	public static IList<Integer> setValueOf(final IScope scope, final GamaSolution solution, final GamaVariable variable)
			throws GamaRuntimeException {
		if (solution == null) return GamaListFactory.create(Types.INT);
		return solution.setValueOf(scope, variable);
	}

}

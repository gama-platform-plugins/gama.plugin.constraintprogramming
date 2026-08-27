package gama.plugin.constraintprogramming;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.extension.Tuples;

import gama.annotations.doc;
import gama.annotations.example;
import gama.annotations.no_test;
import gama.annotations.operator;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.api.gaml.types.Cast;
import gama.api.types.list.IList;
import gama.api.types.matrix.IMatrix;
import gama.plugin.constraintprogramming.terms.Relation;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * The operators that build constraints over the variables of a problem.
 *
 * <p>
 * Building a constraint adds nothing to the problem: it has to be given to {@code post} to become effective. This
 * mirrors the distinction Choco makes between building a constraint and posting it, and is what allows constraints to
 * be reified or combined before being posted.
 * </p>
 */
public class Constraints {

	// ---------------------------------------------------------------------------------------------------------------
	// Posting
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Adds a constraint to its problem.
	 */
	@operator (
			value = "post",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Adds a constraint to the problem over whose variables it has been built, and returns it. A constraint that is never posted is simply ignored by the solver.",
			comment = "Posting the same constraint twice has no effect the second time, so a constraint held in a variable can safely be posted inside a loop.",
			examples = { @example (
					value = "do post(all_different(queens));",
					isExecutable = false) },
			see = { "post_all", "reify", "search" })
	@no_test
	public static GamaConstraint post(final IScope scope, final GamaConstraint constraint)
			throws GamaRuntimeException {
		if (constraint == null) throw GamaRuntimeException.error("Trying to post a nil constraint", scope);
		constraint.post(scope);
		return constraint;
	}

	/**
	 * Adds a constraint to an explicitly named problem.
	 */
	@operator (
			value = "post",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Adds a constraint to the problem given as first operand, checking that it was indeed built over the variables of that problem.",
			examples = { @example (
					value = "do post(p, all_different(queens));",
					isExecutable = false) },
			see = { "post" })
	@no_test
	public static GamaConstraint post(final IScope scope, final GamaProblem problem, final GamaConstraint constraint)
			throws GamaRuntimeException {
		if (constraint == null) throw GamaRuntimeException.error("Trying to post a nil constraint", scope);
		if (problem == null) throw GamaRuntimeException.error("Trying to post a constraint in a nil problem", scope);
		if (constraint.getProblem() != problem) throw GamaRuntimeException.error("The constraint "
				+ constraint.getConstraintName() + " was built over the variables of the problem "
				+ constraint.getProblem().getProblemName() + ", it cannot be posted in " + problem.getProblemName(),
				scope);
		constraint.post(scope);
		return constraint;
	}

	/**
	 * Adds several constraints at once.
	 */
	@operator (
			value = "post_all",
			content_type = GamaConstraintType.id,
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Adds every constraint of the list to its problem, and returns the list. Convenient when the constraints have been accumulated in a loop.",
			examples = { @example (
					value = "do post_all(precedence_constraints);",
					isExecutable = false) },
			see = { "post" })
	@no_test
	public static IList<GamaConstraint> postAll(final IScope scope, final IList<GamaConstraint> constraints)
			throws GamaRuntimeException {
		if (constraints == null) return null;
		for (final GamaConstraint c : constraints) {
			if (c == null) throw GamaRuntimeException.error("nil found in a list of constraints to post", scope);
			c.post(scope);
		}
		return constraints;
	}

	/**
	 * Wraps a Choco constraint built over a list of variables.
	 *
	 * @param scope
	 *            the current scope
	 * @param vars
	 *            the variables the constraint was built from, used to retrieve the problem
	 * @param c
	 *            builds the Choco constraint when one is needed
	 * @return the GAML constraint
	 */
	private static GamaConstraint of(final IScope scope, final IList<GamaVariable> vars,
			final Supplier<Constraint> c) {
		return new GamaConstraint(CPUtils.problemOf(scope, vars), c);
	}

	/**
	 * Builds the GAML constraint, keeping the relations it amounts to so that every engine can take it.
	 *
	 * @param scope
	 *            the current scope
	 * @param vars
	 *            the variables it is expressed over
	 * @param c
	 *            builds the Choco constraint when one is needed
	 * @param relations
	 *            the relations it asserts
	 * @return the GAML constraint
	 */
	private static GamaConstraint of(final IScope scope, final IList<GamaVariable> vars, final Supplier<Constraint> c,
			final List<Relation> relations) {
		return new GamaConstraint(CPUtils.problemOf(scope, vars), c, relations);
	}

	/**
	 * The relations that spell out a comparison between each variable and the next.
	 *
	 * <p>
	 * The offset follows the convention of the Choco constraints these restate, verified by enumeration:
	 * {@code increasing(v, d)} holds when {@code v[i] + d <= v[i+1]}, and {@code decreasing(v, d)} when
	 * {@code v[i] >= v[i+1] + d}.
	 * </p>
	 *
	 * @param vars
	 *            the variables, in order
	 * @param op
	 *            the comparison between one and the next
	 * @param delta
	 *            the offset added to the right hand side
	 * @return one relation per consecutive pair
	 */
	private static List<Relation> chain(final IList<GamaVariable> vars, final Relation.Rel op, final int delta) {
		final List<Relation> out = new ArrayList<>(Math.max(0, vars.size() - 1));
		for (int i = 0; i + 1 < vars.size(); i++) {
			final Term right = delta == 0 ? new Term.Var(vars.get(i + 1))
					: new Term.Binary(Term.Bin.ADD, new Term.Var(vars.get(i + 1)), new Term.Const(delta));
			out.add(new Relation(op, new Term.Var(vars.get(i)), right));
		}
		return out;
	}

	/**
	 * Translates the comparison written as a string by arithm and scalar into a neutral one.
	 *
	 * @param scope
	 *            the current scope
	 * @param op
	 *            the operator, as written in the model
	 * @return the comparison
	 */
	static Relation.Rel relationOf(final IScope scope, final String op) throws GamaRuntimeException {
		for (final Relation.Rel r : Relation.Rel.values()) { if (r.getSymbol().equals(op)) return r; }
		throw GamaRuntimeException.error("Unknown comparison '" + op + "'. Expected one of: = != < <= > >=", scope);
	}

	/**
	 * Builds the term for a weighted sum of variables.
	 *
	 * @param vars
	 *            the variables
	 * @param coeffs
	 *            their coefficients
	 * @return the term
	 */
	private static Term weightedSum(final IList<GamaVariable> vars, final int[] coeffs) {
		final java.util.List<Term> products = new java.util.ArrayList<>(coeffs.length);
		for (int i = 0; i < coeffs.length; i++) {
			products.add(new Term.Binary(Term.Bin.MUL, new Term.Const(coeffs[i]), new Term.Var(vars.get(i))));
		}
		return Term.sum(products);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Arithmetic and simple relations
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * A relation between a variable and a constant.
	 */
	@operator (
			value = "arithm",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint 'variable operator value', where the operator is given as a string among '=', '!=', '<', '<=', '>' and '>='.",
			examples = { @example (
					value = "do post(arithm(x, \"<=\", 5));",
					isExecutable = false) },
			see = { "scalar", "member" })
	@no_test
	public static GamaConstraint arithm(final IScope scope, final GamaVariable var, final String op, final int value)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		return new GamaConstraint(p, () -> p.getModel().arithm(var.asIntVar(scope), op, value),
				new Relation(relationOf(scope, op), new Term.Var(var), new Term.Const(value)));
	}

	/**
	 * A relation between two variables.
	 */
	@operator (
			value = "arithm",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint 'first operator second', where the operator is given as a string among '=', '!=', '<', '<=', '>' and '>='.",
			examples = { @example (
					value = "do post(arithm(x, \"<\", y));",
					isExecutable = false) },
			see = { "arithm" })
	@no_test
	public static GamaConstraint arithm(final IScope scope, final GamaVariable var, final String op,
			final GamaVariable other) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		return new GamaConstraint(p, () -> p.getModel().arithm(var.asIntVar(scope), op, other.asIntVar(scope)),
				new Relation(relationOf(scope, op), new Term.Var(var), new Term.Var(other)));
	}

	/**
	 * A relation between a combination of two variables and a constant.
	 */
	@operator (
			value = "arithm",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint 'first op1 second op2 value', where op1 is an arithmetic operator ('+', '-', '*', '/') and op2 a relational one ('=', '!=', '<', '<=', '>', '>=').",
			examples = { @example (
					value = "do post(arithm(x, \"-\", y, \"!=\", 3));",
					isExecutable = false) },
			see = { "arithm" })
	@no_test
	public static GamaConstraint arithm(final IScope scope, final GamaVariable var, final String op1,
			final GamaVariable other, final String op2, final int value) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		final Term.Bin arithmetic = switch (op1) {
			case "+" -> Term.Bin.ADD;
			case "-" -> Term.Bin.SUB;
			case "*" -> Term.Bin.MUL;
			case "/" -> Term.Bin.DIV;
			default -> throw GamaRuntimeException
					.error("Unknown arithmetic operator '" + op1 + "'. Expected one of: + - * /", scope);
		};
		return new GamaConstraint(p, () -> p.getModel().arithm(var.asIntVar(scope), op1, other.asIntVar(scope), op2, value),
				new Relation(relationOf(scope, op2),
						new Term.Binary(arithmetic, new Term.Var(var), new Term.Var(other)),
						new Term.Const(value)));
	}

	/**
	 * A weighted sum compared to a constant.
	 */
	@operator (
			value = "scalar",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint 'sum of the variables weighted by the coefficients, operator value'. The two first operands must have the same size.",
			examples = { @example (
					value = "do post(scalar(items, [3, 5, 2], \"<=\", 10));",
					isExecutable = false) },
			see = { "sum_var", "arithm" })
	@no_test
	public static GamaConstraint scalar(final IScope scope, final IList<GamaVariable> vars, final IList<Integer> coeffs,
			final String op, final int value) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		final int[] c = CPUtils.ints(scope, coeffs);
		if (c.length != vars.size()) throw GamaRuntimeException
				.error("scalar expects as many coefficients (" + c.length + ") as variables (" + vars.size() + ")", scope);
		return new GamaConstraint(p, () -> p.getModel().scalar(CPUtils.intVars(scope, vars), c, op, value),
				new Relation(relationOf(scope, op), weightedSum(vars, c), new Term.Const(value)));
	}

	/**
	 * A weighted sum compared to a variable.
	 */
	@operator (
			value = "scalar",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint 'sum of the variables weighted by the coefficients, operator variable'.",
			see = { "scalar" })
	@no_test
	public static GamaConstraint scalar(final IScope scope, final IList<GamaVariable> vars, final IList<Integer> coeffs,
			final String op, final GamaVariable value) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		final int[] c = CPUtils.ints(scope, coeffs);
		if (c.length != vars.size()) throw GamaRuntimeException
				.error("scalar expects as many coefficients (" + c.length + ") as variables (" + vars.size() + ")", scope);
		return new GamaConstraint(p, () -> p.getModel().scalar(CPUtils.intVars(scope, vars), c, op, value.asIntVar(scope)),
				new Relation(relationOf(scope, op), weightedSum(vars, c), new Term.Var(value)));
	}

	/**
	 * Membership in an explicit set of values.
	 */
	@operator (
			value = "member",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the variable takes one of the values of the list. Only available with the 'choco' engine.",
			examples = { @example (
					value = "do post(member(x, [1, 3, 7]));",
					isExecutable = false) },
			see = { "not_member" })
	@no_test
	public static GamaConstraint member(final IScope scope, final GamaVariable var, final IList<Integer> values)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		return new GamaConstraint(p, () -> p.getModel().member(var.asIntVar(scope), CPUtils.ints(scope, values)));
	}

	/**
	 * Non-membership in an explicit set of values.
	 */
	@operator (
			value = "not_member",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the variable takes none of the values of the list. Only available with the 'choco' engine.",
			see = { "member" })
	@no_test
	public static GamaConstraint notMember(final IScope scope, final GamaVariable var, final IList<Integer> values)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, var);
		return new GamaConstraint(p, () -> p.getModel().notMember(var.asIntVar(scope), CPUtils.ints(scope, values)));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Global constraints over a list of variables
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * All the variables take distinct values.
	 */
	@operator (
			value = "all_different",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that all the variables of the list take distinct values. Only available with the 'choco' engine.",
			examples = { @example (
					value = "do post(all_different(queens));",
					isExecutable = false) },
			see = { "all_different_except_0", "all_equal", "n_values" })
	@no_test
	public static GamaConstraint allDifferent(final IScope scope, final IList<GamaVariable> vars)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().allDifferent(CPUtils.intVars(scope, vars)));
	}

	/**
	 * All the non-zero variables take distinct values.
	 */
	@operator (
			value = "all_different_except_0",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that all the variables of the list take distinct values, except those taking the value 0, which is used to denote an absence of assignment. Only available with the 'choco' engine.",
			see = { "all_different" })
	@no_test
	public static GamaConstraint allDifferentExcept0(final IScope scope, final IList<GamaVariable> vars)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().allDifferentExcept0(CPUtils.intVars(scope, vars)));
	}

	/**
	 * All the variables take the same value.
	 */
	@operator (
			value = "all_equal",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that all the variables of the list take the same value. Available on every engine: it amounts to a chain of equalities.",
			see = { "not_all_equal", "all_different" })
	@no_test
	public static GamaConstraint allEqual(final IScope scope, final IList<GamaVariable> vars) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().allEqual(CPUtils.intVars(scope, vars)),
				chain(vars, Relation.Rel.EQ, 0));
	}

	/**
	 * At least two variables differ.
	 */
	@operator (
			value = "not_all_equal",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that at least two variables of the list take different values. Only available with the 'choco' engine.",
			see = { "all_equal" })
	@no_test
	public static GamaConstraint notAllEqual(final IScope scope, final IList<GamaVariable> vars)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().notAllEqual(CPUtils.intVars(scope, vars)));
	}

	/**
	 * The value read in a table at a variable index.
	 */
	@operator (
			value = "element",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the first variable is equal to the value read in the table at the index given by the third operand. Indices start at 0. Only available with the 'choco' engine.",
			examples = { @example (
					value = "do post(element(cost, [10, 4, 7], choice));",
					isExecutable = false) },
			see = { "element_var" })
	@no_test
	public static GamaConstraint element(final IScope scope, final GamaVariable value, final IList<Integer> table,
			final GamaVariable index) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, value);
		return new GamaConstraint(p, () -> p.getModel().element(value.asIntVar(scope), CPUtils.ints(scope, table),
				index.asIntVar(scope), 0));
	}

	/**
	 * The number of variables taking one of a set of values.
	 */
	@operator (
			value = "among_values",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the first variable is equal to the number of variables of the second operand taking one of the values of the third. Only available with the 'choco' engine.",
			comment = "Named among_values rather than among because among is already an operator of the core library.",
			see = { "count_var", "global_cardinality" })
	@no_test
	public static GamaConstraint amongValues(final IScope scope, final GamaVariable nb, final IList<GamaVariable> vars,
			final IList<Integer> values) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars,
				() -> p.getModel().among(nb.asIntVar(scope), CPUtils.intVars(scope, vars), CPUtils.ints(scope, values)));
	}

	/**
	 * The number of distinct values taken.
	 */
	@operator (
			value = "n_values",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the second operand is equal to the number of distinct values taken by the variables of the first. Only available with the 'choco' engine.",
			see = { "at_least_n_values", "at_most_n_values", "all_different" })
	@no_test
	public static GamaConstraint nValues(final IScope scope, final IList<GamaVariable> vars, final GamaVariable nb)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().nValues(CPUtils.intVars(scope, vars), nb.asIntVar(scope)));
	}

	/**
	 * A lower bound on the number of distinct values taken.
	 */
	@operator (
			value = "at_least_n_values",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the variables of the first operand take at least as many distinct values as the second operand. Only available with the 'choco' engine.",
			see = { "n_values", "at_most_n_values" })
	@no_test
	public static GamaConstraint atLeastNValues(final IScope scope, final IList<GamaVariable> vars, final GamaVariable nb)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().atLeastNValues(CPUtils.intVars(scope, vars), nb.asIntVar(scope), true));
	}

	/**
	 * An upper bound on the number of distinct values taken.
	 */
	@operator (
			value = "at_most_n_values",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the variables of the first operand take at most as many distinct values as the second operand. Only available with the 'choco' engine.",
			see = { "n_values", "at_least_n_values" })
	@no_test
	public static GamaConstraint atMostNValues(final IScope scope, final IList<GamaVariable> vars, final GamaVariable nb)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().atMostNValues(CPUtils.intVars(scope, vars), nb.asIntVar(scope), true));
	}

	/**
	 * How many times each value is taken.
	 */
	@operator (
			value = "global_cardinality",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that, for each index i, the number of variables of the first operand taking the value values[i] is equal to occurrences[i]. If the last operand is true, the variables can only take values listed in the second operand. Only available with the 'choco' engine.",
			examples = { @example (
					value = "do post(global_cardinality(slots, [1, 2, 3], loads, true));",
					isExecutable = false) },
			see = { "count_var", "among_values" })
	@no_test
	public static GamaConstraint globalCardinality(final IScope scope, final IList<GamaVariable> vars,
			final IList<Integer> values, final IList<GamaVariable> occurrences, final boolean closed)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		final int[] v = CPUtils.ints(scope, values);
		if (v.length != occurrences.size()) throw GamaRuntimeException.error(
				"global_cardinality expects as many occurrence variables (" + occurrences.size() + ") as values ("
						+ v.length + ")",
				scope);
		return of(scope, vars, () -> p.getModel().globalCardinality(CPUtils.intVars(scope, vars), v,
				CPUtils.intVars(scope, occurrences), closed));
	}

	/**
	 * Values are sorted in increasing order.
	 */
	@operator (
			value = "increasing",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that each variable of the list is greater than or equal to the previous one, plus the delta given as second operand. A delta of 1 makes the sequence strictly increasing. Available on every engine: it amounts to a chain of inequalities.",
			see = { "decreasing", "sorted" })
	@no_test
	public static GamaConstraint increasing(final IScope scope, final IList<GamaVariable> vars, final int delta)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().increasing(CPUtils.intVars(scope, vars), delta),
				chain(vars, Relation.Rel.LE, -delta));
	}

	/**
	 * Values are sorted in decreasing order.
	 */
	@operator (
			value = "decreasing",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that each variable of the list is smaller than or equal to the previous one, minus the delta given as second operand. Available on every engine: it amounts to a chain of inequalities.",
			see = { "increasing" })
	@no_test
	public static GamaConstraint decreasing(final IScope scope, final IList<GamaVariable> vars, final int delta)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().decreasing(CPUtils.intVars(scope, vars), delta),
				chain(vars, Relation.Rel.GE, delta));
	}

	/**
	 * The second list is the sorted version of the first.
	 */
	@operator (
			value = "sorted",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the second list contains the same values as the first, in increasing order. Only available with the 'choco' engine.",
			comment = "Named sorted rather than sort because sort is already an operator of the core library.",
			see = { "increasing" })
	@no_test
	public static GamaConstraint sorted(final IScope scope, final IList<GamaVariable> vars,
			final IList<GamaVariable> sortedVars) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().sort(CPUtils.intVars(scope, vars), CPUtils.intVars(scope, sortedVars)));
	}

	/**
	 * Lexicographic ordering, strict.
	 */
	@operator (
			value = "lex_less",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the first list is strictly smaller than the second in the lexicographic order. Useful to break symmetries between interchangeable groups of variables. Only available with the 'choco' engine.",
			see = { "lex_less_eq" })
	@no_test
	public static GamaConstraint lexLess(final IScope scope, final IList<GamaVariable> first,
			final IList<GamaVariable> second) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, first);
		return of(scope, first, () -> p.getModel().lexLess(CPUtils.intVars(scope, first), CPUtils.intVars(scope, second)));
	}

	/**
	 * Lexicographic ordering, non-strict.
	 */
	@operator (
			value = "lex_less_eq",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the first list is smaller than or equal to the second in the lexicographic order. Only available with the 'choco' engine.",
			see = { "lex_less" })
	@no_test
	public static GamaConstraint lexLessEq(final IScope scope, final IList<GamaVariable> first,
			final IList<GamaVariable> second) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, first);
		return of(scope, first, () -> p.getModel().lexLessEq(CPUtils.intVars(scope, first), CPUtils.intVars(scope, second)));
	}

	/**
	 * Two lists are inverse permutations of each other.
	 */
	@operator (
			value = "inverse_channeling",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that first[j] = i if and only if second[i] = j. Used to keep two complementary views of the same assignment consistent. Only available with the 'choco' engine.",
			comment = "Named inverse_channeling rather than inverse because inverse is already an operator of the core library.",
			see = { "element" })
	@no_test
	public static GamaConstraint inverseChanneling(final IScope scope, final IList<GamaVariable> first,
			final IList<GamaVariable> second) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, first);
		return of(scope, first,
				() -> p.getModel().inverseChanneling(CPUtils.intVars(scope, first), CPUtils.intVars(scope, second)));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Routing and packing
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * The successor variables form a single circuit.
	 */
	@operator (
			value = "circuit",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the variables, read as successors (vars[i] = j meaning that j follows i), form a single hamiltonian circuit. Indices start at 0. Only available with the 'choco' engine.",
			see = { "sub_circuit", "path", "tree" })
	@no_test
	public static GamaConstraint circuit(final IScope scope, final IList<GamaVariable> vars) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().circuit(CPUtils.intVars(scope, vars)));
	}

	/**
	 * The successor variables form a circuit over a subset of the nodes.
	 */
	@operator (
			value = "sub_circuit",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the variables, read as successors, form a single circuit visiting exactly as many nodes as the second operand indicates, the others pointing to themselves. Only available with the 'choco' engine.",
			see = { "circuit" })
	@no_test
	public static GamaConstraint subCircuit(final IScope scope, final IList<GamaVariable> vars, final GamaVariable size)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().subCircuit(CPUtils.intVars(scope, vars), 0, size.asIntVar(scope)));
	}

	/**
	 * The successor variables form a path.
	 */
	@operator (
			value = "path",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the variables, read as successors, form a single hamiltonian path going from the node given as second operand to the one given as third. Only available with the 'choco' engine.",
			see = { "circuit" })
	@no_test
	public static GamaConstraint path(final IScope scope, final IList<GamaVariable> vars, final GamaVariable start,
			final GamaVariable end) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars,
				() -> p.getModel().path(CPUtils.intVars(scope, vars), start.asIntVar(scope), end.asIntVar(scope)));
	}

	/**
	 * The predecessor variables form a forest.
	 */
	@operator (
			value = "tree",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the variables, read as predecessors, form an anti-arborescence made of as many trees as the second operand indicates. Only available with the 'choco' engine.",
			see = { "circuit", "path" })
	@no_test
	public static GamaConstraint tree(final IScope scope, final IList<GamaVariable> vars, final GamaVariable nbRoots)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		return of(scope, vars, () -> p.getModel().tree(CPUtils.intVars(scope, vars), nbRoots.asIntVar(scope)));
	}

	/**
	 * Items are packed into bins whose load is bounded.
	 */
	@operator (
			value = "bin_packing",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that, item i being placed in the bin given by itemBin[i] and weighing itemSize[i], each bin b carries the load binLoad[b]. The last operand is the index of the first bin. Only available with the 'choco' engine.",
			examples = { @example (
					value = "do post(bin_packing(assignment, [4, 2, 3], loads, 0));",
					isExecutable = false) },
			see = { "knapsack" })
	@no_test
	public static GamaConstraint binPacking(final IScope scope, final IList<GamaVariable> itemBin,
			final IList<Integer> itemSize, final IList<GamaVariable> binLoad, final int offset)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, itemBin);
		final int[] sizes = CPUtils.ints(scope, itemSize);
		if (sizes.length != itemBin.size()) throw GamaRuntimeException.error(
				"bin_packing expects as many sizes (" + sizes.length + ") as items (" + itemBin.size() + ")", scope);
		return of(scope, itemBin, () -> p.getModel().binPacking(CPUtils.intVars(scope, itemBin), sizes,
				CPUtils.intVars(scope, binLoad), offset));
	}

	/**
	 * The knapsack constraint.
	 */
	@operator (
			value = "knapsack",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint linking the number of occurrences of each item, the total weight and the total energy, given the weight and the energy of each item. Available on every engine: it amounts to two linear equalities. A constraint engine also gets the dedicated propagator, which prunes more than the two equalities alone.",
			see = { "bin_packing", "scalar" })
	@no_test
	public static GamaConstraint knapsack(final IScope scope, final IList<GamaVariable> occurrences,
			final GamaVariable weightSum, final GamaVariable energySum, final IList<Integer> weights,
			final IList<Integer> energies) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, occurrences);
		final int[] w = CPUtils.ints(scope, weights);
		final int[] e = CPUtils.ints(scope, energies);
		if (w.length != occurrences.size() || e.length != occurrences.size())
			throw GamaRuntimeException.error("knapsack expects as many weights (" + w.length + ") and energies ("
					+ e.length + ") as variables (" + occurrences.size() + ")", scope);
		return of(scope, occurrences,
				() -> p.getModel().knapsack(CPUtils.intVars(scope, occurrences), weightSum.asIntVar(scope),
						energySum.asIntVar(scope), w, e),
				List.of(new Relation(Relation.Rel.EQ, weightedSum(occurrences, w), new Term.Var(weightSum)),
						new Relation(Relation.Rel.EQ, weightedSum(occurrences, e), new Term.Var(energySum))));
	}

	/**
	 * An explicit list of the allowed combinations.
	 */
	@operator (
			value = "table",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the values taken by the variables form one of the rows of the matrix. Each row is one allowed combination, and the matrix must have as many columns as there are variables. Only available with the 'choco' engine.",
			comment = "This is the way to express a relation that has no analytical form: a tabulated response curve, an empirical compatibility table, a rule set given by extension. A table propagates strongly, since it reasons over the whole relation at once, but its size grows as the product of the domains.",
			examples = { @example (
					value = "do post(table(vars, matrix([[1, 2], [2, 4], [3, 8]])));",
					isExecutable = false) },
			see = { "as_table", "element" })
	@no_test
	public static GamaConstraint table(final IScope scope, final IList<GamaVariable> vars, final IMatrix rows)
			throws GamaRuntimeException {
		return table(scope, vars, rows, true);
	}

	/**
	 * An explicit list of the allowed, or forbidden, combinations.
	 */
	@operator (
			value = "table",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the values taken by the variables form one of the rows of the matrix when the last operand is true, and none of them when it is false. Only available with the 'choco' engine.",
			see = { "table" })
	@no_test
	public static GamaConstraint table(final IScope scope, final IList<GamaVariable> vars, final IMatrix rows,
			final boolean allowed) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		if (rows == null) throw GamaRuntimeException.error("A nil matrix was given to table", scope);
		final int cols = rows.getCols(scope);
		final int lines = rows.getRows(scope);
		if (cols != vars.size()) throw GamaRuntimeException.error("table expects a matrix with as many columns ("
				+ cols + ") as there are variables (" + vars.size() + ")", scope);
		final Tuples tuples = new Tuples(allowed);
		for (int r = 0; r < lines; r++) {
			final int[] tuple = new int[cols];
			for (int c = 0; c < cols; c++) {
				final Integer v = Cast.asInt(scope, rows.get(scope, c, r));
				if (v == null) throw GamaRuntimeException
						.error("nil found at row " + r + ", column " + c + " of a table", scope);
				tuple[c] = v;
			}
			tuples.add(tuple);
		}
		return of(scope, vars, () -> p.getModel().table(CPUtils.intVars(scope, vars), tuples));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Combining constraints
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * The conjunction of several constraints.
	 */
	@operator (
			value = "and_all",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that all the constraints of the list hold. Available on every engine when every constraint of the list is itself, since the conjunction is then simply their relations put together; otherwise it needs the 'choco' engine.",
			see = { "or_all", "opposite", "if_then" })
	@no_test
	public static GamaConstraint andAll(final IScope scope, final IList<GamaConstraint> constraints)
			throws GamaRuntimeException {
		final GamaProblem p = problemOfConstraints(scope, constraints);
		final List<Relation> all = new ArrayList<>();
		for (final GamaConstraint c : constraints) {
			if (c == null || c.getRelations().isEmpty()) {
				all.clear();
				break;
			}
			all.addAll(c.getRelations());
		}
		return new GamaConstraint(p, () -> p.getModel().and(chocoConstraints(scope, constraints)), all);
	}

	/**
	 * The disjunction of several constraints.
	 */
	@operator (
			value = "or_all",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that at least one of the constraints of the list holds. Only available with the 'choco' engine.",
			see = { "and_all", "opposite", "if_then" })
	@no_test
	public static GamaConstraint orAll(final IScope scope, final IList<GamaConstraint> constraints)
			throws GamaRuntimeException {
		final GamaProblem p = problemOfConstraints(scope, constraints);
		return new GamaConstraint(p, () -> p.getModel().or(chocoConstraints(scope, constraints)));
	}

	/**
	 * The negation of a constraint.
	 */
	@operator (
			value = "opposite",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the constraint given as operand does not hold. Only available with the 'choco' engine.",
			comment = "Named opposite rather than not because not is already an operator of the core library.",
			see = { "and_all", "or_all" })
	@no_test
	public static GamaConstraint opposite(final IScope scope, final GamaConstraint constraint)
			throws GamaRuntimeException {
		if (constraint == null) throw GamaRuntimeException.error("Cannot negate a nil constraint", scope);
		final GamaProblem p = constraint.getProblem();
		return new GamaConstraint(p, () -> p.getModel().not(constraint.getChocoConstraint(scope)));
	}

	/**
	 * An implication between two constraints.
	 */
	@operator (
			value = "if_then",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that if the first constraint holds, then the second one holds too. Only available with the 'choco' engine.",
			examples = { @example (
					value = "do post(if_then(arithm(x, \"=\", 1), arithm(y, \">\", 5)));",
					isExecutable = false) },
			see = { "or_all", "opposite" })
	@no_test
	public static GamaConstraint ifThen(final IScope scope, final GamaConstraint condition, final GamaConstraint consequence)
			throws GamaRuntimeException {
		if (condition == null || consequence == null)
			throw GamaRuntimeException.error("if_then does not accept a nil constraint", scope);
		final GamaProblem p = condition.getProblem();
		final Model m = p.getModel();
		return new GamaConstraint(p,
				() -> m.or(m.not(condition.getChocoConstraint(scope)), consequence.getChocoConstraint(scope)));
	}

	/**
	 * The boolean variable that reflects whether a constraint holds.
	 */
	@operator (
			value = "reify",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a boolean variable that is true if and only if the constraint given as operand holds. Reifying a constraint adds the link between the constraint and the variable to the problem immediately; the constraint itself is not posted, and does not have to hold. Only available with the 'choco' engine.",
			examples = { @example (
					value = "pb_variable is_late <- reify(arithm(end, \">\", deadline));",
					isExecutable = false) },
			see = { "bool_var", "if_then" })
	@no_test
	public static GamaVariable reify(final IScope scope, final GamaConstraint constraint) throws GamaRuntimeException {
		if (constraint == null) throw GamaRuntimeException.error("Cannot reify a nil constraint", scope);
		final GamaProblem p = constraint.getProblem();
		CPUtils.requireConstraintEngine(scope, p, "reify", null);
		return p.register(constraint.getChocoConstraint(scope).reify());
	}

	/**
	 * Returns the problem shared by a list of constraints.
	 *
	 * @param scope
	 *            the current scope
	 * @param constraints
	 *            the constraints
	 * @return the problem they all refer to
	 */
	private static GamaProblem problemOfConstraints(final IScope scope, final IList<GamaConstraint> constraints)
			throws GamaRuntimeException {
		if (constraints == null || constraints.isEmpty())
			throw GamaRuntimeException.error("An empty list of constraints was given to a combinator", scope);
		final GamaConstraint first = constraints.get(0);
		if (first == null) throw GamaRuntimeException.error("nil found in a list of constraints", scope);
		return first.getProblem();
	}

	/**
	 * Unwraps a list of constraints.
	 *
	 * @param scope
	 *            the current scope
	 * @param constraints
	 *            the constraints
	 * @return the array of Choco constraints
	 */
	private static Constraint[] chocoConstraints(final IScope scope, final IList<GamaConstraint> constraints)
			throws GamaRuntimeException {
		final Constraint[] result = new Constraint[constraints.size()];
		for (int i = 0; i < result.length; i++) {
			final GamaConstraint c = constraints.get(i);
			if (c == null) throw GamaRuntimeException.error("nil found at index " + i + " of a list of constraints", scope);
			// getChocoConstraint, not getConstraint: the Choco form is built on demand and the field is empty until then
			result[i] = c.getChocoConstraint(scope);
		}
		return result;
	}

}

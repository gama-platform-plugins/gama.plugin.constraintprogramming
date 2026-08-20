package gama.plugin.constraintprogramming;

import gama.annotations.doc;
import gama.annotations.example;
import gama.annotations.no_test;
import gama.annotations.operator;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.plugin.constraintprogramming.terms.Relation;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * The arithmetic and relational operators of GAML, overloaded over the variables of a problem.
 *
 * <p>
 * Writing {@code x + 2 * y} builds an expression tree rather than a value: nothing is added to the problem until the
 * expression is compared and the resulting constraint is posted. Keeping the tree unevaluated lets Choco compile it as a
 * whole, which is both cheaper than materialising one intermediate variable per operator and stronger, since a complete
 * expression can be turned into a table by {@code as_table}.
 * </p>
 *
 * <p>
 * The relational operators return a constraint rather than a boolean, which is what allows {@code x + y = 10} to be
 * posted. To test whether two GAML values designate the same variable, use {@code same} instead of {@code =}.
 * </p>
 */
public class Expressions {

	/**
	 * Returns an operand as a term: the term it already stands for, or a reference to the declared variable.
	 *
	 * @param scope
	 *            the current scope
	 * @param v
	 *            the operand
	 * @return the term
	 */
	private static Term term(final IScope scope, final GamaVariable v) throws GamaRuntimeException {
		if (v == null) throw GamaRuntimeException.error("nil used in a constraint expression", scope);
		return v.isExpression() ? v.getTerm() : new Term.Var(v);
	}

	/**
	 * Wraps a built term.
	 *
	 * @param v
	 *            the variable giving the problem
	 * @param t
	 *            the term
	 * @return the GAML wrapper
	 */
	private static GamaVariable of(final GamaVariable v, final Term t) {
		return new GamaVariable(v.getProblem(), t);
	}

	/**
	 * Builds a binary term over two operands.
	 */
	private static GamaVariable bin(final IScope scope, final Term.Bin op, final GamaVariable a,
			final GamaVariable b) throws GamaRuntimeException {
		return of(a, new Term.Binary(op, term(scope, a), term(scope, b)));
	}

	/**
	 * Builds a binary term over an operand and a constant on the right.
	 */
	private static GamaVariable binK(final IScope scope, final Term.Bin op, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return of(a, new Term.Binary(op, term(scope, a), new Term.Const(k)));
	}

	/**
	 * Builds a binary term over a constant on the left and an operand.
	 */
	private static GamaVariable kBin(final IScope scope, final Term.Bin op, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return of(b, new Term.Binary(op, new Term.Const(k), term(scope, b)));
	}

	/**
	 * Builds a relation between two operands, and compiles it for the engine of the problem.
	 */
	private static GamaConstraint rel(final IScope scope, final Relation.Rel op, final GamaVariable a,
			final GamaVariable b) throws GamaRuntimeException {
		return compile(scope, a, new Relation(op, term(scope, a), term(scope, b)));
	}

	/**
	 * Builds a relation between an operand and a constant on the right.
	 */
	private static GamaConstraint relK(final IScope scope, final Relation.Rel op, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return compile(scope, a, new Relation(op, term(scope, a), new Term.Const(k)));
	}

	/**
	 * Builds a relation between a constant on the left and an operand.
	 */
	private static GamaConstraint kRel(final IScope scope, final Relation.Rel op, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return compile(scope, b, new Relation(op, new Term.Const(k), term(scope, b)));
	}

	/**
	 * Compiles a relation into a constraint, keeping the relation so that it can be recompiled differently later.
	 */
	private static GamaConstraint compile(final IScope scope, final GamaVariable v, final Relation r)
			throws GamaRuntimeException {
		final GamaProblem p = v.getProblem();
		return new GamaConstraint(p, ChocoCompiler.compile(scope, p, r).decompose(), r);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Arithmetic
	// ---------------------------------------------------------------------------------------------------------------

	/** Sum of two variables or expressions. */
	@operator (value = "+", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'first plus second'. Nothing is added to the problem until the expression takes part in a posted constraint.",
			examples = { @example (value = "do post(x + y = 10);", isExecutable = false) },
			see = { "-", "*", "as_table" })
	@no_test
	public static GamaVariable plus(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return bin(scope, Term.Bin.ADD, a, b);
	}

	/** Sum of a variable and a constant. */
	@operator (value = "+", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'variable plus constant'.")
	@no_test
	public static GamaVariable plus(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return binK(scope, Term.Bin.ADD, a, k);
	}

	/** Sum of a constant and a variable. */
	@operator (value = "+", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'constant plus variable'.")
	@no_test
	public static GamaVariable plus(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kBin(scope, Term.Bin.ADD, k, b);
	}

	/** Difference of two variables or expressions. */
	@operator (value = "-", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'first minus second'.")
	@no_test
	public static GamaVariable minus(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return bin(scope, Term.Bin.SUB, a, b);
	}

	/** Difference of a variable and a constant. */
	@operator (value = "-", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'variable minus constant'.")
	@no_test
	public static GamaVariable minus(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return binK(scope, Term.Bin.SUB, a, k);
	}

	/** Difference of a constant and a variable. */
	@operator (value = "-", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'constant minus variable'.")
	@no_test
	public static GamaVariable minus(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kBin(scope, Term.Bin.SUB, k, b);
	}

	/** Opposite of a variable or expression. */
	@operator (value = "-", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'minus the operand'.")
	@no_test
	public static GamaVariable negate(final IScope scope, final GamaVariable a) throws GamaRuntimeException {
		return of(a, new Term.Unary(Term.Un.NEG, term(scope, a)));
	}

	/** Product of two variables or expressions. */
	@operator (value = "*", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'first times second'. A product of two variables is a non-linear constraint: it is propagated on the bounds only, so it deduces little until the domains have shrunk.",
			examples = { @example (value = "do post(quantity * unit_price = cost);", isExecutable = false) })
	@no_test
	public static GamaVariable times(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return bin(scope, Term.Bin.MUL, a, b);
	}

	/** Product of a variable and a constant. */
	@operator (value = "*", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'variable times constant'.")
	@no_test
	public static GamaVariable times(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return binK(scope, Term.Bin.MUL, a, k);
	}

	/** Product of a constant and a variable. */
	@operator (value = "*", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'constant times variable'.")
	@no_test
	public static GamaVariable times(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kBin(scope, Term.Bin.MUL, k, b);
	}

	/** Quotient of two variables or expressions. */
	@operator (value = "/", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'first divided by second'. The division is euclidean, and the solver forbids a null divisor.")
	@no_test
	public static GamaVariable divide(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return bin(scope, Term.Bin.DIV, a, b);
	}

	/** Quotient of a variable by a constant. */
	@operator (value = "/", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'variable divided by constant', as an euclidean division.")
	@no_test
	public static GamaVariable divide(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		if (k == 0) throw GamaRuntimeException.error("Division by zero in a constraint expression", scope);
		return binK(scope, Term.Bin.DIV, a, k);
	}

	/** Quotient of a constant by a variable. */
	@operator (value = "/", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'constant divided by variable', as an euclidean division.")
	@no_test
	public static GamaVariable divide(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kBin(scope, Term.Bin.DIV, k, b);
	}

	/** Remainder of two variables or expressions. */
	@operator (value = "mod", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'first modulo second'.")
	@no_test
	public static GamaVariable modulo(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return bin(scope, Term.Bin.MOD, a, b);
	}

	/** Remainder of a variable by a constant. */
	@operator (value = "mod", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'variable modulo constant'.")
	@no_test
	public static GamaVariable modulo(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		if (k == 0) throw GamaRuntimeException.error("Modulo by zero in a constraint expression", scope);
		return binK(scope, Term.Bin.MOD, a, k);
	}

	/** Remainder of a constant by a variable. */
	@operator (value = "mod", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'constant modulo variable'.")
	@no_test
	public static GamaVariable modulo(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kBin(scope, Term.Bin.MOD, k, b);
	}

	/** Power of a variable by a constant exponent. */
	@operator (value = "^", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'variable to the power constant'.")
	@no_test
	public static GamaVariable power(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return binK(scope, Term.Bin.POW, a, k);
	}

	/** Power of a variable by a variable exponent. */
	@operator (value = "^", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the expression 'first to the power second'.")
	@no_test
	public static GamaVariable power(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return bin(scope, Term.Bin.POW, a, b);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Relations
	// ---------------------------------------------------------------------------------------------------------------

	/** Equality of two variables or expressions. */
	@operator (value = "=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the two operands are equal. On the variables of a problem, '=' expresses a constraint rather than a test: to check whether two GAML values designate the same variable, use 'same'.",
			examples = { @example (value = "do post(x + y = 10);", isExecutable = false) },
			see = { "same", "!=" })
	@no_test
	public static GamaConstraint eq(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		if (a == null || b == null) throw GamaRuntimeException.error("nil used in a constraint expression", scope);
		// A real and an integer variable cannot meet in an arithmetic expression, but they can be channelled
		if (a.isReal() != b.isReal()) {
			final GamaVariable real = a.isReal() ? a : b;
			final GamaVariable integer = a.isReal() ? b : a;
			return new GamaConstraint(real.getProblem(),
					real.getProblem().getModel().eq(real.asRealVar(scope), integer.asIntVar(scope)));
		}
		return rel(scope, Relation.Rel.EQ, a, b);
	}

	/** Equality with a constant. */
	@operator (value = "=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the expression equals the constant.", see = { "same" })
	@no_test
	public static GamaConstraint eq(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return relK(scope, Relation.Rel.EQ, a, k);
	}

	/** Equality of a constant with an expression. */
	@operator (value = "=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the constant equals the expression.", see = { "same" })
	@no_test
	public static GamaConstraint eq(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kRel(scope, Relation.Rel.EQ, k, b);
	}

	/** Difference of two variables or expressions. */
	@operator (value = "!=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the two operands differ.", see = { "=" })
	@no_test
	public static GamaConstraint neq(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return rel(scope, Relation.Rel.NE, a, b);
	}

	/** Difference from a constant. */
	@operator (value = "!=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the expression differs from the constant.")
	@no_test
	public static GamaConstraint neq(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return relK(scope, Relation.Rel.NE, a, k);
	}

	/** Difference of a constant from an expression. */
	@operator (value = "!=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the constant differs from the expression.")
	@no_test
	public static GamaConstraint neq(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kRel(scope, Relation.Rel.NE, k, b);
	}

	/** Strict order between two variables or expressions. */
	@operator (value = "<", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the first operand is strictly smaller than the second.")
	@no_test
	public static GamaConstraint lt(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return rel(scope, Relation.Rel.LT, a, b);
	}

	/** Strict upper bound. */
	@operator (value = "<", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the expression is strictly smaller than the constant.")
	@no_test
	public static GamaConstraint lt(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return relK(scope, Relation.Rel.LT, a, k);
	}

	/** Strict lower bound. */
	@operator (value = "<", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the constant is strictly smaller than the expression.")
	@no_test
	public static GamaConstraint lt(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kRel(scope, Relation.Rel.LT, k, b);
	}

	/** Order between two variables or expressions. */
	@operator (value = "<=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the first operand is smaller than or equal to the second.")
	@no_test
	public static GamaConstraint le(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return rel(scope, Relation.Rel.LE, a, b);
	}

	/** Upper bound. */
	@operator (value = "<=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the expression is smaller than or equal to the constant.")
	@no_test
	public static GamaConstraint le(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return relK(scope, Relation.Rel.LE, a, k);
	}

	/** Lower bound. */
	@operator (value = "<=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the constant is smaller than or equal to the expression.")
	@no_test
	public static GamaConstraint le(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kRel(scope, Relation.Rel.LE, k, b);
	}

	/** Strict order, reversed. */
	@operator (value = ">", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the first operand is strictly greater than the second.")
	@no_test
	public static GamaConstraint gt(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return rel(scope, Relation.Rel.GT, a, b);
	}

	/** Strict lower bound, reversed. */
	@operator (value = ">", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the expression is strictly greater than the constant.")
	@no_test
	public static GamaConstraint gt(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return relK(scope, Relation.Rel.GT, a, k);
	}

	/** Strict upper bound, reversed. */
	@operator (value = ">", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the constant is strictly greater than the expression.")
	@no_test
	public static GamaConstraint gt(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kRel(scope, Relation.Rel.GT, k, b);
	}

	/** Order, reversed. */
	@operator (value = ">=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the first operand is greater than or equal to the second.")
	@no_test
	public static GamaConstraint ge(final IScope scope, final GamaVariable a, final GamaVariable b)
			throws GamaRuntimeException {
		return rel(scope, Relation.Rel.GE, a, b);
	}

	/** Lower bound, reversed. */
	@operator (value = ">=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the expression is greater than or equal to the constant.")
	@no_test
	public static GamaConstraint ge(final IScope scope, final GamaVariable a, final int k)
			throws GamaRuntimeException {
		return relK(scope, Relation.Rel.GE, a, k);
	}

	/** Upper bound, reversed. */
	@operator (value = ">=", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Builds the constraint stating that the constant is greater than or equal to the expression.")
	@no_test
	public static GamaConstraint ge(final IScope scope, final int k, final GamaVariable b)
			throws GamaRuntimeException {
		return kRel(scope, Relation.Rel.GE, k, b);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Identity and compilation
	// ---------------------------------------------------------------------------------------------------------------

	/** Identity test between two variables. */
	@operator (value = "same", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Returns whether the two operands designate the same variable of the same problem. This is the test that '=' would perform on any other type, and that it no longer performs here since '=' expresses a constraint.",
			examples = { @example (value = "if (same(queens[0], first_queen)) { ... }", isExecutable = false) },
			see = { "=" })
	@no_test
	public static boolean same(final IScope scope, final GamaVariable a, final GamaVariable b) {
		return a == b;
	}

	/** Compiles a constraint into a table. */
	@operator (value = "as_table", category = { CPUtils.CATEGORY }, concept = { IConcept.OPTIMIZATION })
	@doc (value = "Recompiles a constraint built from an expression into a single table constraint, listing the combinations of values that satisfy it, and returns it.",
			comment = "A table propagates far more strongly than the chain of propagators the expression is decomposed into, because it reasons over the whole relation at once. It is only applicable when the variables of the expression have small domains, since the number of combinations grows as their product. Only works on a constraint that came from an expression, not on a global constraint.",
			examples = { @example (value = "do post(as_table(x * y + z = 12));", isExecutable = false) },
			see = { "=" })
	@no_test
	public static GamaConstraint asTable(final IScope scope, final GamaConstraint constraint)
			throws GamaRuntimeException {
		if (constraint == null) throw GamaRuntimeException.error("Trying to tabulate a nil constraint", scope);
		final Relation source = constraint.getRelation();
		if (source == null) throw GamaRuntimeException.error("as_table only applies to a constraint built from an "
				+ "arithmetic expression, and " + constraint.getConstraintName() + " is not one", scope);
		try {
			return new GamaConstraint(constraint.getProblem(),
					ChocoCompiler.compile(scope, constraint.getProblem(), source).extension(), source);
		} catch (final Exception e) {
			throw GamaRuntimeException.error("Impossible to tabulate this constraint, most likely because the domains "
					+ "of its variables are too large: " + e.getMessage(), scope);
		}
	}

}

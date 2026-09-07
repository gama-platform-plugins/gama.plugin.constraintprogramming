package gama.plugin.constraintprogramming;

import gama.plugin.constraintprogramming.terms.NonLinearException;
import gama.plugin.constraintprogramming.terms.LinearForm;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.Model;
import java.util.Map;
import org.chocosolver.solver.expression.discrete.arithmetic.ArExpression;
import org.chocosolver.solver.expression.discrete.relational.ReExpression;

import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.plugin.constraintprogramming.terms.Relation;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * Compiles the backend-neutral terms of the plugin into the expression objects of Choco.
 *
 * <p>
 * This is the only place where an arithmetic expression written in GAML meets Choco. A second engine means a second
 * compiler reading the same terms, not a second set of operators.
 * </p>
 */
public class ChocoCompiler {

	/**
	 * Compiles a term into a Choco arithmetic expression.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @param problem
	 *            the problem the expression belongs to, needed to build constants
	 * @param term
	 *            the term
	 * @return the Choco expression
	 * @throws GamaRuntimeException
	 *             if the term refers to a variable Choco cannot use in an expression
	 */
	public static ArExpression compile(final IScope scope, final GamaProblem problem, final Term term)
			throws GamaRuntimeException {
		return switch (term) {
			case Term.Var v -> v.variable().asChocoExpression(scope);
			case Term.Const c -> {
				if (!c.isIntegral()) throw GamaRuntimeException.error("The constant " + c.value()
						+ " is not a whole number, and the constraint engine only reasons over integers. Use the 'lp' "
						+ "engine for this problem, or scale the data to integers.", scope);
				yield problem.getModel().intVar((int) c.value());
			}
			case Term.Unary u -> switch (u.op()) {
				case NEG -> compile(scope, problem, u.operand()).neg();
				case ABS -> compile(scope, problem, u.operand()).abs();
			};
			case Term.Binary b -> {
				final ArExpression left = compile(scope, problem, b.left());
				final ArExpression right = compile(scope, problem, b.right());
				yield switch (b.op()) {
					case ADD -> left.add(right);
					case SUB -> left.sub(right);
					case MUL -> left.mul(right);
					case DIV -> left.div(right);
					case MOD -> left.mod(right);
					case POW -> left.pow(right);
				};
			}
		};
	}

	/**
	 * Turns a relation into the Choco constraint that expresses it best.
	 *
	 * <p>
	 * This is where the plugin decides how Choco should encode what a relation states, and the only place that
	 * decision is made. An operator says what its constraint asserts and nothing more; a weighted sum over forty
	 * variables compiled as an expression tree costs a hundred and nineteen auxiliary variables and eighty
	 * propagators, where the dedicated constraint costs one of each, so a relation that turns out to be linear is
	 * handed to {@code scalar} or, over a single variable, to {@code arithm}.
	 * </p>
	 *
	 * <p>
	 * A relation that is not linear, a product of two decisions for instance, falls back on the expression tree,
	 * which is what Choco needs for it anyway.
	 * </p>
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @param problem
	 *            the problem the relation is expressed over
	 * @param relation
	 *            the relation
	 * @return the constraint
	 */
	public static Constraint constraintOf(final IScope scope, final GamaProblem problem, final Relation relation)
			throws GamaRuntimeException {
		final LinearForm form;
		try {
			form = LinearForm.of(relation.left()).subtract(LinearForm.of(relation.right()));
		} catch (final NonLinearException e) {
			// Not linear, so there is nothing better than the expression tree Choco propagates natively
			return compile(scope, problem, relation).decompose();
		}
		final Model model = problem.requireChoco(scope, "This constraint").getModel();
		final String op = relation.op().getSymbol();
		final double rhs = -form.getConstant();
		final Map<GamaVariable, Double> terms = form.getCoefficients();

		if (terms.isEmpty()) return holds(0, op, rhs) ? model.trueConstraint() : model.falseConstraint();

		final GamaVariable[] vars = terms.keySet().toArray(new GamaVariable[0]);
		final double[] coeffs = new double[vars.length];
		boolean integral = isWhole(rhs);
		boolean allInt = true;
		for (int i = 0; i < vars.length; i++) {
			coeffs[i] = terms.get(vars[i]);
			integral &= isWhole(coeffs[i]);
			allInt &= vars[i].getVariableKind() != GamaVariable.Kind.REAL;
		}

		if (!integral || !allInt) {
			// A real variable or a fractional coefficient: the comparison has to be made over reals
			final Variable[] operands = new Variable[vars.length];
			for (int i = 0; i < vars.length; i++) { operands[i] = vars[i].getVariable(); }
			return model.scalar(operands, coeffs, op, rhs);
		}

		final IntVar[] operands = new IntVar[vars.length];
		final int[] weights = new int[vars.length];
		for (int i = 0; i < vars.length; i++) {
			operands[i] = vars[i].asIntVar(scope);
			weights[i] = (int) Math.round(coeffs[i]);
		}
		if (operands.length == 1 && Math.abs(weights[0]) == 1) {
			// x op k, or -x op k which is x with the comparison turned around
			final int k = (int) Math.round(rhs) * weights[0];
			return model.arithm(operands[0], weights[0] > 0 ? op : mirrored(op), k);
		}
		return model.scalar(operands, weights, op, (int) Math.round(rhs));
	}

	/**
	 * Whether a value is a whole number, within the tolerance a double comparison needs.
	 *
	 * @param d
	 *            the value
	 * @return true if it is whole
	 */
	private static boolean isWhole(final double d) { return Math.abs(d - Math.rint(d)) < 1e-9; }

	/**
	 * Whether a comparison between two constants holds.
	 *
	 * @param left
	 *            the left hand side
	 * @param op
	 *            the comparison
	 * @param right
	 *            the right hand side
	 * @return whether it holds
	 */
	private static boolean holds(final double left, final String op, final double right) {
		return switch (op) {
			case "=" -> Math.abs(left - right) < 1e-9;
			case "!=" -> Math.abs(left - right) >= 1e-9;
			case "<" -> left < right - 1e-9;
			case "<=" -> left <= right + 1e-9;
			case ">" -> left > right + 1e-9;
			default -> left >= right - 1e-9;
		};
	}

	/**
	 * The comparison read from the other side, for when both sides are multiplied by a negative number.
	 *
	 * @param op
	 *            the comparison
	 * @return the mirrored comparison
	 */
	private static String mirrored(final String op) {
		return switch (op) {
			case "<" -> ">";
			case "<=" -> ">=";
			case ">" -> "<";
			case ">=" -> "<=";
			default -> op;
		};
	}

	/**
	 * Compiles a relation into a Choco relational expression.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @param problem
	 *            the problem the relation belongs to
	 * @param relation
	 *            the relation
	 * @return the Choco relational expression
	 */
	public static ReExpression compile(final IScope scope, final GamaProblem problem, final Relation relation)
			throws GamaRuntimeException {
		final ArExpression left = compile(scope, problem, relation.left());
		final ArExpression right = compile(scope, problem, relation.right());
		return switch (relation.op()) {
			case EQ -> left.eq(right);
			case NE -> left.ne(right);
			case LT -> left.lt(right);
			case LE -> left.le(right);
			case GT -> left.gt(right);
			case GE -> left.ge(right);
		};
	}

}

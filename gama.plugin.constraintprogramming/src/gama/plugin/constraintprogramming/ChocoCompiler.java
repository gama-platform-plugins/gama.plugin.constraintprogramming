package gama.plugin.constraintprogramming;

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

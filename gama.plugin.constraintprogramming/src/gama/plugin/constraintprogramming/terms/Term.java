package gama.plugin.constraintprogramming.terms;

import gama.plugin.constraintprogramming.GamaVariable;

/**
 * A backend-neutral arithmetic term.
 *
 * <p>
 * The arithmetic operators of the plugin build these trees rather than the expressions of a particular solver, so that
 * the same GAML model can be compiled for several engines. A constraint engine turns a term into its own expression
 * objects; a linear engine flattens it into a linear form and rejects the nodes it cannot represent.
 * </p>
 *
 * <p>
 * Nothing here refers to a solver. The only foreign type is {@link GamaVariable}, and only as the identity of a declared
 * decision variable.
 * </p>
 */
public sealed interface Term {

	/** The unary operators. */
	enum Un {
		/** Opposite. */
		NEG,
		/** Absolute value. */
		ABS
	}

	/** The binary operators. */
	enum Bin {
		/** Sum. */
		ADD,
		/** Difference. */
		SUB,
		/** Product. */
		MUL,
		/** Euclidean quotient. */
		DIV,
		/** Remainder. */
		MOD,
		/** Power. */
		POW
	}

	/**
	 * A reference to a declared decision variable.
	 *
	 * @param variable
	 *            the variable
	 */
	record Var(GamaVariable variable) implements Term {}

	/**
	 * An integer constant.
	 *
	 * @param value
	 *            the value
	 */
	record Const(int value) implements Term {}

	/**
	 * The application of a unary operator.
	 *
	 * @param op
	 *            the operator
	 * @param operand
	 *            the operand
	 */
	record Unary(Un op, Term operand) implements Term {}

	/**
	 * The application of a binary operator.
	 *
	 * @param op
	 *            the operator
	 * @param left
	 *            the left operand
	 * @param right
	 *            the right operand
	 */
	record Binary(Bin op, Term left, Term right) implements Term {}

	/**
	 * Whether this term is linear, that is, whether it only combines variables and constants through additions,
	 * subtractions, negations and multiplications by a constant. A linear engine can compile exactly these.
	 *
	 * @return true if the term is linear
	 */
	default boolean isLinear() {
		return switch (this) {
			case Var v -> true;
			case Const c -> true;
			case Unary u -> u.op() == Un.NEG && u.operand().isLinear();
			case Binary b -> switch (b.op()) {
				case ADD, SUB -> b.left().isLinear() && b.right().isLinear();
				case MUL -> b.left().isLinear() && b.right().isLinear()
						&& (b.left() instanceof Const || b.right() instanceof Const);
				case DIV -> b.right() instanceof Const && b.left().isLinear();
				default -> false;
			};
		};
	}

	/**
	 * A human readable form of the term, used in error messages so that a modeller can recognise which expression a
	 * backend refused.
	 *
	 * @return the term, written out
	 */
	default String describe() {
		return switch (this) {
			case Var v -> v.variable().getVariableName();
			case Const c -> String.valueOf(c.value());
			case Unary u -> switch (u.op()) {
				case NEG -> "-(" + u.operand().describe() + ")";
				case ABS -> "abs(" + u.operand().describe() + ")";
			};
			case Binary b -> "(" + b.left().describe() + " " + switch (b.op()) {
				case ADD -> "+";
				case SUB -> "-";
				case MUL -> "*";
				case DIV -> "/";
				case MOD -> "mod";
				case POW -> "^";
			} + " " + b.right().describe() + ")";
		};
	}

}

package gama.plugin.constraintprogramming.terms;

import java.util.ArrayList;
import java.util.List;

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
	 * A numeric constant. Held as a double because the data of a linear model, and of a file read in the MPS format in
	 * particular, is not integral in general. An engine that only accepts integers checks it when compiling.
	 *
	 * @param value
	 *            the value
	 */
	record Const(double value) implements Term {

		/**
		 * Whether this constant is a whole number, which is what an integer engine requires.
		 *
		 * @return true if the value is integral
		 */
		public boolean isIntegral() { return value == Math.rint(value) && !Double.isInfinite(value); }
	}

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
	 * Builds the sum of a list of terms as a balanced tree rather than a chain leaning to one side.
	 *
	 * <p>
	 * The shape matters: everything that walks a term does so recursively, so a sum of ten thousand terms built as a
	 * chain would be ten thousand frames deep, where a balanced tree is fourteen. Sums of that size are ordinary in a
	 * model read from a file.
	 * </p>
	 *
	 * @param terms
	 *            the terms to add
	 * @return their sum, or the constant zero if the list is empty
	 */
	static Term sum(final List<Term> terms) {
		if (terms.isEmpty()) return new Const(0);
		List<Term> level = terms;
		while (level.size() > 1) {
			final List<Term> next = new ArrayList<>((level.size() + 1) / 2);
			for (int i = 0; i < level.size(); i += 2) {
				next.add(i + 1 < level.size() ? new Binary(Bin.ADD, level.get(i), level.get(i + 1)) : level.get(i));
			}
			level = next;
		}
		return level.get(0);
	}

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
		final StringBuilder sb = new StringBuilder();
		describe(sb, 0);
		return sb.toString();
	}

	/** The number of characters beyond which a term is abbreviated, so that a message stays readable. */
	int DESCRIPTION_BUDGET = 400;

	/**
	 * Writes the term out, giving up once the budget is spent. Bounded rather than complete, because a term read from
	 * a file can mention thousands of variables and no message needs to carry them all.
	 *
	 * @param sb
	 *            the buffer to write to
	 * @param depth
	 *            the current depth, used to stop on a term that is deeper than any message needs
	 */
	private void describe(final StringBuilder sb, final int depth) {
		if (sb.length() > DESCRIPTION_BUDGET) {
			if (!sb.toString().endsWith("...")) { sb.append("..."); }
			return;
		}
		switch (this) {
			case Var v -> sb.append(v.variable().getVariableName());
			case Const c -> sb.append(c.isIntegral() ? String.valueOf((long) c.value()) : String.valueOf(c.value()));
			case Unary u -> {
				sb.append(u.op() == Un.NEG ? "-(" : "abs(");
				u.operand().describe(sb, depth + 1);
				sb.append(')');
			}
			case Binary b -> {
				sb.append('(');
				b.left().describe(sb, depth + 1);
				sb.append(' ').append(switch (b.op()) {
					case ADD -> "+";
					case SUB -> "-";
					case MUL -> "*";
					case DIV -> "/";
					case MOD -> "mod";
					case POW -> "^";
				}).append(' ');
				b.right().describe(sb, depth + 1);
				sb.append(')');
			}
		}
	}

}

package gama.plugin.constraintprogramming.terms;

/**
 * A backend-neutral comparison between two terms. This is what the relational operators of the plugin build, and what a
 * backend turns into a constraint of its own.
 *
 * @param op
 *            the comparison
 * @param left
 *            the left hand side
 * @param right
 *            the right hand side
 */
public record Relation(Relation.Rel op, Term left, Term right) {

	/** The comparisons. */
	public enum Rel {
		/** Equal. */
		EQ("="),
		/** Different. */
		NE("!="),
		/** Strictly smaller. */
		LT("<"),
		/** Smaller or equal. */
		LE("<="),
		/** Strictly greater. */
		GT(">"),
		/** Greater or equal. */
		GE(">=");

		/** The symbol used in GAML and in error messages. */
		private final String symbol;

		Rel(final String symbol) {
			this.symbol = symbol;
		}

		/**
		 * Gets the symbol.
		 *
		 * @return the symbol
		 */
		public String getSymbol() { return symbol; }
	}

	/**
	 * Whether both sides are linear, which is what a linear engine requires.
	 *
	 * @return true if the relation is linear
	 */
	public boolean isLinear() { return left.isLinear() && right.isLinear(); }

	/**
	 * A human readable form of the relation, used in error messages.
	 *
	 * @return the relation, written out
	 */
	public String describe() {
		return left.describe() + " " + op.getSymbol() + " " + right.describe();
	}

}

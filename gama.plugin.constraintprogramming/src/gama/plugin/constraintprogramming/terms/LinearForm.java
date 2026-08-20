package gama.plugin.constraintprogramming.terms;

import java.util.LinkedHashMap;
import java.util.Map;

import gama.plugin.constraintprogramming.GamaVariable;

/**
 * A term flattened into a linear form: a coefficient for each variable it mentions, plus a constant.
 *
 * <p>
 * This is what a linear engine consumes. Flattening is total on the terms for which {@link Term#isLinear()} holds, and
 * raises {@link NonLinearException} on the others, naming the sub-term responsible.
 * </p>
 */
public class LinearForm {

	/** The coefficient of each variable, in the order the variables were met. */
	private final Map<GamaVariable, Double> coefficients = new LinkedHashMap<>();

	/** The constant part. */
	private double constant;

	/**
	 * Flattens a term.
	 *
	 * @param term
	 *            the term
	 * @return its linear form
	 * @throws NonLinearException
	 *             if the term contains a node no linear engine can represent
	 */
	public static LinearForm of(final Term term) {
		final LinearForm result = new LinearForm();
		result.accumulate(term, 1.0);
		return result;
	}

	/**
	 * Adds a term to this form, multiplied by a factor.
	 *
	 * @param term
	 *            the term
	 * @param factor
	 *            the factor it is multiplied by
	 */
	private void accumulate(final Term term, final double factor) {
		switch (term) {
			case Term.Const c -> constant += factor * c.value();
			case Term.Var v -> coefficients.merge(v.variable(), factor, Double::sum);
			case Term.Unary u -> {
				if (u.op() != Term.Un.NEG) throw new NonLinearException(term);
				accumulate(u.operand(), -factor);
			}
			case Term.Binary b -> {
				switch (b.op()) {
					case ADD -> {
						accumulate(b.left(), factor);
						accumulate(b.right(), factor);
					}
					case SUB -> {
						accumulate(b.left(), factor);
						accumulate(b.right(), -factor);
					}
					case MUL -> {
						// One side has to be constant, otherwise the product of two unknowns is not linear
						final Double left = constantValueOf(b.left());
						final Double right = constantValueOf(b.right());
						if (right != null) {
							accumulate(b.left(), factor * right);
						} else if (left != null) {
							accumulate(b.right(), factor * left);
						} else throw new NonLinearException(term);
					}
					case DIV -> {
						final Double divisor = constantValueOf(b.right());
						if (divisor == null || divisor == 0) throw new NonLinearException(term);
						accumulate(b.left(), factor / divisor);
					}
					default -> throw new NonLinearException(term);
				}
			}
		}
	}

	/**
	 * Returns the value of a term if it is a constant expression, null otherwise. A constant sub-expression is folded
	 * rather than rejected, so that a coefficient written as an arithmetic expression is accepted.
	 *
	 * @param term
	 *            the term
	 * @return its value, or null if it mentions a variable
	 */
	private static Double constantValueOf(final Term term) {
		return switch (term) {
			case Term.Const c -> (double) c.value();
			case Term.Var v -> null;
			case Term.Unary u -> {
				final Double inner = constantValueOf(u.operand());
				if (inner == null) yield null;
				yield u.op() == Term.Un.NEG ? -inner : Math.abs(inner);
			}
			case Term.Binary b -> {
				final Double l = constantValueOf(b.left());
				final Double r = constantValueOf(b.right());
				if (l == null || r == null) yield null;
				yield switch (b.op()) {
					case ADD -> l + r;
					case SUB -> l - r;
					case MUL -> l * r;
					case DIV -> r == 0 ? null : (double) (long) (l / r);
					case MOD -> r == 0 ? null : (double) (long) (l % r);
					case POW -> Math.pow(l, r);
				};
			}
		};
	}

	/**
	 * Gets the coefficients, by variable.
	 *
	 * @return the coefficients
	 */
	public Map<GamaVariable, Double> getCoefficients() { return coefficients; }

	/**
	 * Gets the constant part.
	 *
	 * @return the constant
	 */
	public double getConstant() { return constant; }

	/**
	 * Subtracts another form from this one, which is how a relation between two sides is brought to the form
	 * 'linear expression compared to a constant'.
	 *
	 * @param other
	 *            the form to subtract
	 * @return this form, modified
	 */
	public LinearForm subtract(final LinearForm other) {
		other.coefficients.forEach((v, c) -> coefficients.merge(v, -c, Double::sum));
		constant -= other.constant;
		coefficients.values().removeIf(c -> c == 0.0);
		return this;
	}

}

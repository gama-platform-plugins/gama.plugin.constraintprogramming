package gama.plugin.constraintprogramming.terms;

/**
 * Raised when a term that a linear engine cannot represent is met while flattening. Carries the offending sub-term so
 * that the message names the expression the modeller actually wrote rather than the whole constraint.
 */
public class NonLinearException extends RuntimeException {

	/** Serial version UID. */
	private static final long serialVersionUID = 1L;

	/** The sub-term that could not be flattened. */
	private final transient Term culprit;

	/**
	 * Instantiates a new exception.
	 *
	 * @param culprit
	 *            the offending sub-term
	 */
	public NonLinearException(final Term culprit) {
		super("the sub-expression " + culprit.describe() + " is not linear");
		this.culprit = culprit;
	}

	/**
	 * Gets the offending sub-term.
	 *
	 * @return the sub-term
	 */
	public Term getCulprit() { return culprit; }

}

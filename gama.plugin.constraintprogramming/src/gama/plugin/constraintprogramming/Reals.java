package gama.plugin.constraintprogramming;

import gama.plugin.constraintprogramming.terms.Term;
import gama.plugin.constraintprogramming.terms.Relation;
import java.util.function.Supplier;
import org.chocosolver.solver.variables.Variable;

import gama.annotations.doc;
import gama.annotations.example;
import gama.annotations.no_test;
import gama.annotations.operator;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.Cast;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.IList;

/**
 * The operators that work on real, continuous variables.
 *
 * <p>
 * Only the constraints implemented in Java are exposed. Everything non-linear over reals in Choco is delegated to Ibex,
 * a native library that is not shipped with the plugin, so it is deliberately left out. What remains covers linear
 * combinations, the link between a real and an integer variable, and the lookup of a real value at an integer index.
 * </p>
 */
public class Reals {

	/**
	 * Converts a GAML list into an array of doubles.
	 *
	 * @param scope
	 *            the current scope
	 * @param values
	 *            the values
	 * @return the array of doubles
	 */
	private static double[] doubles(final IScope scope, final IList<Double> values) throws GamaRuntimeException {
		if (values == null) return new double[0];
		final double[] result = new double[values.size()];
		for (int i = 0; i < result.length; i++) {
			final Double v = Cast.asFloat(scope, values.get(i));
			if (v == null) throw GamaRuntimeException
					.error("nil found at index " + i + " of a list of float expected by a constraint", scope);
			result[i] = v;
		}
		return result;
	}

	/**
	 * A weighted sum over variables that may be integer or real.
	 */
	@operator (
			value = "real_scalar",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint 'sum of the variables weighted by the real coefficients, operator value'. The variables may be integer or real, in any mix.",
			comment = "Named real_scalar rather than scalar because the coefficients are floats and the two would be ambiguous in GAML, where an int is accepted wherever a float is expected.",
			examples = { @example (
					value = "do post(real_scalar(quantities, [1.5, 0.75, 2.0], \"<=\", 100.0));",
					isExecutable = false) },
			see = { "scalar" })
	@no_test
	public static GamaConstraint realScalar(final IScope scope, final IList<GamaVariable> vars,
			final IList<Double> coeffs, final String op, final double value) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, vars);
		final double[] c = doubles(scope, coeffs);
		if (c.length != vars.size()) throw GamaRuntimeException.error(
				"real_scalar expects as many coefficients (" + c.length + ") as variables (" + vars.size() + ")",
				scope);
		final java.util.List<Term> products = new java.util.ArrayList<>(c.length);
		for (int i = 0; i < c.length; i++) {
			products.add(new Term.Binary(Term.Bin.MUL, new Term.Const(c[i]), new Term.Var(vars.get(i))));
		}
		return new GamaConstraint(p, () -> {
			// materialised here rather than above, so that a linear engine never builds a Choco variable
			final Variable[] operands = new Variable[vars.size()];
			for (int i = 0; i < operands.length; i++) { operands[i] = vars.get(i).getVariable(); }
			return p.getModel().scalar(operands, c, op, value);
		}, new Relation(Constraints.relationOf(scope, op), Term.sum(products), new Term.Const(value)));
	}

	/**
	 * The value read in a table of reals at a variable index.
	 */
	@operator (
			value = "real_element",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Builds the constraint stating that the real variable given as first operand is equal to the value read in the table of floats at the index given by the third operand. Indices start at 0.",
			comment = "This is the practical way to bring a non-linear relation into a model over reals: tabulate it against an integer index.",
			see = { "element" })
	@no_test
	public static GamaConstraint realElement(final IScope scope, final GamaVariable value, final IList<Double> table,
			final GamaVariable index) throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, value);
		return new GamaConstraint(p,
				() -> p.getModel().element(value.asRealVar(scope), doubles(scope, table), index.asIntVar(scope)));
	}

	/**
	 * A real view of an integer variable.
	 */
	@operator (
			value = "real_view",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns a real variable that follows the integer variable given as first operand, with the precision given as second. Implemented as a view, so it costs neither a propagator nor a search decision.",
			examples = { @example (
					value = "pb_variable r <- real_view(x, 0.01);",
					isExecutable = false) },
			see = { "real_var" })
	@no_test
	public static GamaVariable realView(final IScope scope, final GamaVariable variable, final double precision)
			throws GamaRuntimeException {
		final GamaProblem p = CPUtils.problemOf(scope, variable);
		return p.register(p.getModel().realIntView(variable.asIntVar(scope), precision));
	}

	/**
	 * Sets the precision used for real variables.
	 */
	@operator (
			value = "set_precision",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Sets the precision below which the solver considers a real domain instantiated, and returns the problem. Applies to the real variables declared afterwards.",
			examples = { @example (
					value = "do set_precision(p, 0.001);",
					isExecutable = false) },
			see = { "real_var" })
	@no_test
	public static GamaProblem setPrecision(final IScope scope, final GamaProblem problem, final double precision)
			throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to configure a nil problem", scope);
		problem.getModel().setPrecision(precision);
		return problem;
	}

}

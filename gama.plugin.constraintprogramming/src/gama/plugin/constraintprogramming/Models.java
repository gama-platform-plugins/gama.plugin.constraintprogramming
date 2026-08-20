package gama.plugin.constraintprogramming;

import gama.annotations.doc;
import gama.annotations.example;
import gama.annotations.no_test;
import gama.annotations.operator;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import java.util.Map;

import gama.api.runtime.scope.IScope;
import gama.plugin.constraintprogramming.terms.LinearForm;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * The operators that read a problem from a file, and those that work on the objective such a file declares.
 */
public class Models {

	/**
	 * Reads a problem written in the MPS format.
	 */
	@operator (
			value = "read_mps",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Reads a linear or mixed integer problem written in the MPS format and returns it, ready to be solved by the linear engine. Variables, constraints and objective come from the file; nothing has to be declared in the model.",
			comment = "The path is resolved relative to the model, as everywhere else in GAML. Compressed files are not read: uncompress them first, whether they use gzip or the packed form Netlib distributes for its own test set.",
			examples = { @example (
					value = "problem p <- read_mps(\"../includes/afiro.mps\");",
					isExecutable = false) },
			see = { "optimize", "objective_of" })
	@no_test
	public static GamaProblem readMps(final IScope scope, final String path) throws GamaRuntimeException {
		return MpsReader.read(scope, path, GamaProblem.Backend.LP);
	}

	/**
	 * Reads a problem written in the MPS format, to be solved by a named engine.
	 */
	@operator (
			value = "read_mps",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Reads a problem written in the MPS format and returns it, to be solved by the engine named as second operand.",
			comment = "The constraint engine only reasons over integers, so it refuses a file carrying fractional data or continuous columns. Reading the same file with both engines is the way to compare them on a model neither of us wrote.",
			examples = { @example (
					value = "problem p <- read_mps(\"../includes/model.mps\", \"choco\");",
					isExecutable = false) },
			see = { "read_mps" })
	@no_test
	public static GamaProblem readMps(final IScope scope, final String path, final String engine)
			throws GamaRuntimeException {
		final GamaProblem.Backend b = GamaProblem.Backend.named(engine);
		if (b == null) throw GamaRuntimeException
				.error("Unknown engine '" + engine + "'. Expected one of: choco, choco_lcg, lp, highs", scope);
		return MpsReader.read(scope, path, b);
	}

	/**
	 * The objective declared by the file a problem was read from.
	 */
	@operator (
			value = "objective_of",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns the objective of a problem read from a file, written out as a string, or nil if the file declares none. Such an objective is a linear form over the variables rather than a variable of its own, which is why it is not returned as one.",
			see = { "optimize", "objective_value" })
	@no_test
	public static String objectiveOf(final IScope scope, final GamaProblem problem) throws GamaRuntimeException {
		if (problem == null || problem.getObjective() == null) return null;
		return problem.getObjective().describe();
	}

	/**
	 * Whether the objective declared by the file has to be maximised.
	 */
	@operator (
			value = "maximises",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns whether the objective of a problem read from a file has to be maximised. Minimising is the default of the MPS format, and only an OBJSENSE section changes it.",
			see = { "optimize" })
	@no_test
	public static boolean maximises(final IScope scope, final GamaProblem problem) {
		return problem != null && problem.maximises();
	}

	/**
	 * Optimises the objective a problem carries.
	 */
	@operator (
			value = "optimize",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Solves a problem for the objective it carries, in the direction that objective was declared with, and returns the solution. Only meaningful on a problem read from a file, since a problem built by hand states its objective in the call to minimize or maximize.",
			examples = { @example (
					value = "solution best <- optimize(read_mps(\"../includes/afiro.mps\"));",
					isExecutable = false) },
			see = { "minimize", "maximize", "read_mps" })
	@no_test
	public static GamaSolution optimize(final IScope scope, final GamaProblem problem) throws GamaRuntimeException {
		if (problem == null) throw GamaRuntimeException.error("Trying to optimise a nil problem", scope);
		final Term objective = problem.getObjective();
		if (objective == null) throw GamaRuntimeException.error("The problem " + problem.getProblemName()
				+ " carries no objective. Use minimize or maximize, naming the variable to optimise.", scope);
		if (!problem.isLinear()) throw GamaRuntimeException.error("Optimising the objective carried by a file is only "
				+ "available with a linear engine for now", scope);
		if (problem.getBackend() == GamaProblem.Backend.HIGHS)
			return gama.plugin.constraintprogramming.highs.HighsCompiler.solve(scope, problem, objective,
					problem.maximises(), null);
		return LinearCompiler.solve(scope, problem, objective, problem.maximises());
	}

	/**
	 * The value taken by the objective of a problem in a solution.
	 */
	@operator (
			value = "objective_value",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns the value the objective of the problem takes in this solution, or nil if the problem carries no objective or no solution was found.",
			examples = { @example (
					value = "write objective_value(best);",
					isExecutable = false) },
			see = { "optimize", "objective_of" })
	@no_test
	public static Double objectiveValue(final IScope scope, final GamaSolution solution) throws GamaRuntimeException {
		if (solution == null || !solution.exists()) return null;
		final GamaProblem problem = solution.getProblem();
		if (problem.getObjective() == null) return null;
		final LinearForm form = LinearForm.of(problem.getObjective());
		double total = form.getConstant();
		for (final Map.Entry<GamaVariable, Double> e : form.getCoefficients().entrySet()) {
			final Double v = solution.realValueOf(scope, e.getKey());
			if (v == null) return null;
			total += e.getValue() * v;
		}
		return total;
	}

	/**
	 * Reads a value without rounding it.
	 */
	@operator (
			value = "real_value_of",
			category = { CPUtils.CATEGORY },
			concept = { IConcept.OPTIMIZATION })
	@doc (
			value = "Returns the value taken by a variable in a solution, without rounding it. The linear engine carries continuous variables, whose value value_of would round to the nearest integer.",
			examples = { @example (
					value = "float exact <- real_value_of(best, quantity);",
					isExecutable = false) },
			see = { "value_of" })
	@no_test
	public static Double realValueOf(final IScope scope, final GamaSolution solution, final GamaVariable variable)
			throws GamaRuntimeException {
		if (solution == null) return null;
		return solution.realValueOf(scope, variable);
	}

}

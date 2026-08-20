package gama.plugin.constraintprogramming;

import java.util.Map;

import org.chocosolver.solver.Solution;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.SetVar;
import org.chocosolver.solver.variables.Variable;

import gama.annotations.doc;
import gama.annotations.getter;
import gama.annotations.variable;
import gama.annotations.vars;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;
import gama.api.types.map.GamaMapFactory;
import gama.api.types.map.IMap;
import gama.api.types.misc.IValue;
import gama.api.utils.json.IJson;
import gama.api.utils.json.IJsonValue;

/**
 * A solution of a {@link GamaProblem}: the value taken by each of its variables. Wraps a Choco {@link Solution}, which
 * records the values independently of the state of the solver, so that a solution stays readable after the search is
 * over or after another search has been run.
 *
 * <p>
 * A solution whose {@code exists} attribute is false denotes the absence of solution: either the problem has none, or
 * the search was interrupted before finding one.
 * </p>
 */
@SuppressWarnings ("unchecked")
@vars ({ @variable (
		name = "exists",
		type = IType.BOOL,
		doc = { @doc ("Whether a solution was actually found. False if the problem has none, or if the search was interrupted before finding one") }),
		@variable (
				name = "values",
				type = IType.MAP,
				index = IType.STRING,
				of = IType.INT,
				doc = { @doc ("The values of all the int and bool variables of the problem, indexed by variable name") }) })
public class GamaSolution implements IValue {

	/** The problem this solution belongs to. */
	private final GamaProblem problem;

	/** The recorded Choco solution, null when the solution comes from another engine or when there is none. */
	private final Solution solution;

	/** The values read from an engine that is not Choco, null otherwise. */
	private final Map<String, Double> values;

	/**
	 * Instantiates a new solution.
	 *
	 * @param problem
	 *            the problem
	 * @param solution
	 *            the recorded solution, or null if none was found
	 */
	public GamaSolution(final GamaProblem problem, final Solution solution) {
		this.problem = problem;
		this.solution = solution;
		this.values = null;
	}

	/**
	 * Instantiates a solution from the values produced by an engine that has no solution object of its own.
	 *
	 * @param problem
	 *            the problem
	 * @param values
	 *            the value of each variable, by name, or null if no solution was found
	 */
	public GamaSolution(final GamaProblem problem, final Map<String, Double> values) {
		this.problem = problem;
		this.solution = null;
		this.values = values;
	}

	/**
	 * Gets the problem this solution belongs to.
	 *
	 * @return the problem
	 */
	public GamaProblem getProblem() { return problem; }

	/**
	 * Gets the underlying Choco solution.
	 *
	 * @return the solution, or null if none was found
	 */
	public Solution getSolution() { return solution; }

	/**
	 * Returns the value taken by an int or bool variable in this solution.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @param variable
	 *            the variable to read
	 * @return the value of the variable, or null if no solution was found
	 * @throws GamaRuntimeException
	 *             if the variable does not belong to the problem or is not an int variable
	 */
	public Integer valueOf(final IScope scope, final GamaVariable variable) throws GamaRuntimeException {
		if (variable == null) throw GamaRuntimeException.error("Trying to read the value of a nil variable", scope);
		if (values != null) {
			final Double v = values.get(variable.getVariableName());
			return v == null ? null : (int) Math.round(v);
		}
		if (solution == null) return null;
		if (variable.getProblem() != problem) throw GamaRuntimeException.error("The variable "
				+ variable.getVariableName() + " does not belong to the problem " + problem.getProblemName(), scope);
		try {
			return solution.getIntVal(variable.asIntVar(scope));
		} catch (final Exception e) {
			throw GamaRuntimeException.error(
					"Impossible to read the value of " + variable.getVariableName() + ": " + e.getMessage(), scope);
		}
	}

	/**
	 * Returns the values taken by a set variable in this solution.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @param variable
	 *            the set variable to read
	 * @return the elements of the set, or null if no solution was found
	 * @throws GamaRuntimeException
	 *             if the variable is not a set variable
	 */
	public IList<Integer> setValueOf(final IScope scope, final GamaVariable variable) throws GamaRuntimeException {
		if (values != null) throw GamaRuntimeException
				.error("Set variables are only available with the 'choco' engine", scope);
		if (solution == null) return null;
		final SetVar sv = variable.asSetVar(scope);
		final IList<Integer> result = GamaListFactory.create(Types.INT);
		for (final int i : solution.getSetVal(sv)) { result.add(i); }
		return result;
	}

	@getter ("exists")
	public boolean exists() { return solution != null || values != null; }

	@getter ("values")
	public IMap<String, Integer> getValues() {
		final IMap<String, Integer> result = GamaMapFactory.create(Types.STRING, Types.INT);
		if (values != null) {
			values.forEach((n, v) -> result.put(n, (int) Math.round(v)));
			return result;
		}
		if (solution == null) return result;
		for (final Variable v : problem.getModel().getVars()) {
			if (v instanceof IntVar iv) {
				try {
					result.put(iv.getName(), solution.getIntVal(iv));
				} catch (final Exception e) {
					// a variable that was not recorded in this solution is simply skipped
				}
			}
		}
		return result;
	}

	@Override
	public IType<?> getGamlType() { return Types.get(GamaSolutionType.id); }

	@Override
	public String stringValue(final IScope scope) throws GamaRuntimeException {
		if (values != null) return values.toString();
		if (solution == null) return "no solution";
		return solution.toString();
	}

	@Override
	public String serializeToGaml(final boolean includingBuiltIn) {
		return getValues().serializeToGaml(includingBuiltIn);
	}

	@Override
	public IValue copy(final IScope scope) throws GamaRuntimeException {
		return solution == null ? this : new GamaSolution(problem, solution.copySolution());
	}

	/**
	 * Whether this solution was produced by an engine other than Choco, in which case only the values of the int and
	 * bool variables are available.
	 *
	 * @return true if the solution holds plain values
	 */
	public boolean isPlain() { return values != null; }

	/**
	 * Returns the value of a variable without rounding it, which matters for the continuous variables a linear engine
	 * can carry.
	 *
	 * @param scope
	 *            the current scope
	 * @param variable
	 *            the variable to read
	 * @return the value, or null if no solution was found or the engine keeps no real value
	 */
	public Double realValueOf(final IScope scope, final GamaVariable variable) throws GamaRuntimeException {
		if (variable == null) throw GamaRuntimeException.error("Trying to read the value of a nil variable", scope);
		if (values != null) return values.get(variable.getVariableName());
		if (solution == null) return null;
		final Integer v = valueOf(scope, variable);
		return v == null ? null : (double) v;
	}

	@Override
	public IJsonValue serializeToJson(final IJson json) {
		return json.typedObject(getGamlType()).add("exists", exists()).add("values", json.valueOf(getValues()));
	}

}

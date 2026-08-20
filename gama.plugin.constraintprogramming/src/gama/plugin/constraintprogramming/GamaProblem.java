package gama.plugin.constraintprogramming;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.SettingsBuilder;
import org.chocosolver.solver.Solver;
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
import gama.api.types.misc.IValue;
import gama.api.utils.json.IJson;
import gama.api.utils.json.IJsonValue;

/**
 * A constraint satisfaction/optimisation problem. Wraps a Choco {@link Model} and keeps track of the variables declared
 * through it, so that they can be retrieved by name and read back from a solution.
 *
 * <p>
 * A problem is <b>mutable and stateful</b>: it carries a propagation engine and a backtracking trail. It is therefore
 * never copied ({@link #copy(IScope)} returns itself) and cannot be serialised. The recommended usage is to build a
 * fresh problem, search it, and let it be garbage-collected.
 * </p>
 */
@vars ({ @variable (
		name = "name",
		type = IType.STRING,
		doc = { @doc ("The name given to the problem at its creation") }),
		@variable (
				name = "nb_variables",
				type = IType.INT,
				doc = { @doc ("The number of variables currently declared in the problem") }),
		@variable (
				name = "nb_constraints",
				type = IType.INT,
				doc = { @doc ("The number of constraints currently posted in the problem") }),
		@variable (
				name = "variables",
				type = IType.LIST,
				of = GamaVariableType.id,
				doc = { @doc ("The list of the variables declared in the problem, in declaration order") }),
		@variable (
				name = "solutions",
				type = IType.INT,
				doc = { @doc ("The number of solutions found so far by the last search") }),
		@variable (
				name = "search_time",
				type = IType.FLOAT,
				doc = { @doc ("The time, in seconds, spent in the last search") }),
		@variable (
				name = "nodes",
				type = IType.INT,
				doc = { @doc ("The number of nodes explored during the last search") }),
		@variable (
				name = "fails",
				type = IType.INT,
				doc = { @doc ("The number of failures encountered during the last search") }) })
public class GamaProblem implements IValue {

	/** The underlying Choco model. */
	private final Model model;

	/** The variables declared through this problem, by name, in declaration order. */
	private final Map<String, GamaVariable> declared = new LinkedHashMap<>();

	/** Counter used to generate unique names for anonymous (derived) variables. */
	private int anonymous;

	/** The engine this problem is solved with. */
	private final Backend backend;

	/** The constraints posted to this problem, in the order they were posted. */
	private final List<GamaConstraint> posted = new ArrayList<>();

	/** The engines a problem can be solved with. */
	public enum Backend {

		/** Choco, the constraint engine. Handles everything the plugin exposes. */
		CHOCO("choco"),

		/** Choco with lazy clause generation. */
		CHOCO_LCG("choco_lcg"),

		/** The linear engine bundled with Choco. Only accepts linear constraints. */
		LP("lp");

		/** The name used in GAML. */
		private final String label;

		Backend(final String label) {
			this.label = label;
		}

		/**
		 * Gets the name used in GAML.
		 *
		 * @return the label
		 */
		public String getLabel() { return label; }

		/**
		 * Returns the engine designated by a name.
		 *
		 * @param name
		 *            the name
		 * @return the engine, or null if no engine bears that name
		 */
		public static Backend named(final String name) {
			for (final Backend b : values()) { if (b.label.equals(name)) return b; }
			return null;
		}
	}

	/**
	 * Instantiates a new problem.
	 *
	 * @param name
	 *            the name of the problem
	 */
	public GamaProblem(final String name) {
		this(name, Backend.CHOCO);
	}

	/**
	 * Instantiates a new problem solved with a given engine.
	 *
	 * @param name
	 *            the name of the problem
	 * @param backend
	 *            the engine
	 */
	public GamaProblem(final String name, final Backend backend) {
		this.backend = backend;
		final String actual = name == null ? "problem" : name;
		model = backend == Backend.CHOCO_LCG
				? new Model(actual, new SettingsBuilder().setLCG(true).build()) : new Model(actual);
	}

	/**
	 * Gets the engine this problem is solved with.
	 *
	 * @return the engine
	 */
	public Backend getBackend() { return backend; }

	/**
	 * Whether this problem is solved by a linear engine, which only accepts linear constraints.
	 *
	 * @return true if the engine is linear
	 */
	public boolean isLinear() { return backend == Backend.LP; }

	/**
	 * Records a constraint as posted.
	 *
	 * @param constraint
	 *            the constraint
	 */
	public void recordPosted(final GamaConstraint constraint) {
		posted.add(constraint);
	}

	/**
	 * Gets the constraints posted so far, in order.
	 *
	 * @return the constraints
	 */
	public List<GamaConstraint> getPosted() { return posted; }

	/**
	 * Gets the underlying Choco model.
	 *
	 * @return the model
	 */
	public Model getModel() { return model; }

	/**
	 * Gets the Choco solver attached to the model.
	 *
	 * @return the solver
	 */
	public Solver getSolver() { return model.getSolver(); }

	/**
	 * Registers a Choco variable in this problem and returns the GAML wrapper around it.
	 *
	 * @param v
	 *            the Choco variable
	 * @return the wrapper
	 */
	public GamaVariable register(final Variable v) {
		final GamaVariable wrapper = new GamaVariable(this, v);
		declared.put(v.getName(), wrapper);
		return wrapper;
	}

	/**
	 * Returns the variables declared in this problem, in declaration order, as a plain collection. Used by the engines,
	 * which have no reason to pay for a GAML list on every solve.
	 *
	 * @return the declared variables
	 */
	public Collection<GamaVariable> declaredVariables() { return declared.values(); }

	/**
	 * Returns the variable declared under this name, or null.
	 *
	 * @param name
	 *            the name of the variable
	 * @return the variable, or null if none is declared under this name
	 */
	public GamaVariable getVariable(final String name) {
		return declared.get(name);
	}

	/**
	 * Generates a unique name for a derived variable, based on a prefix.
	 *
	 * @param prefix
	 *            the prefix
	 * @return a name not yet used in this problem
	 */
	public String newName(final String prefix) {
		String candidate;
		do {
			candidate = prefix + "_" + anonymous++;
		} while (declared.containsKey(candidate));
		return candidate;
	}

	@getter ("name")
	public String getProblemName() { return model.getName(); }

	@getter ("nb_variables")
	public int getNbVariables() { return model.getNbVars(); }

	@getter ("nb_constraints")
	public int getNbConstraints() { return model.getNbCstrs(); }

	@getter ("variables")
	public IList<GamaVariable> getVariables() {
		final IList<GamaVariable> result = GamaListFactory.create(Types.get(GamaVariableType.id));
		result.addAll(declared.values());
		return result;
	}

	@getter ("solutions")
	public int getNbSolutions() { return (int) model.getSolver().getSolutionCount(); }

	@getter ("search_time")
	public double getSearchTime() { return model.getSolver().getTimeCount(); }

	@getter ("nodes")
	public int getNodes() { return (int) model.getSolver().getNodeCount(); }

	@getter ("fails")
	public int getFails() { return (int) model.getSolver().getFailCount(); }

	@Override
	public IType<?> getGamlType() { return Types.get(GamaProblemType.id); }

	@Override
	public String stringValue(final IScope scope) throws GamaRuntimeException {
		return "problem " + model.getName() + " (" + model.getNbVars() + " variables, " + model.getNbCstrs()
				+ " constraints)";
	}

	@Override
	public String serializeToGaml(final boolean includingBuiltIn) {
		return "problem(\"" + model.getName() + "\")";
	}

	/**
	 * A problem carries a propagation engine and a backtracking trail: it cannot be duplicated. Returns itself, which
	 * means that assigning a problem to another variable shares it rather than copying it.
	 */
	@Override
	public IValue copy(final IScope scope) throws GamaRuntimeException {
		return this;
	}

	@Override
	public IJsonValue serializeToJson(final IJson json) {
		return json.typedObject(getGamlType()).add("name", model.getName()).add("nb_variables", model.getNbVars())
				.add("nb_constraints", model.getNbCstrs());
	}

}

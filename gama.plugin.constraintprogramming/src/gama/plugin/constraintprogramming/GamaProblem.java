package gama.plugin.constraintprogramming;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.Variable;

import gama.plugin.constraintprogramming.engine.SolverEngine;
import gama.plugin.constraintprogramming.engine.LinearEngine;
import gama.plugin.constraintprogramming.engine.HighsEngine;
import gama.plugin.constraintprogramming.engine.ChocoEngine;
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
				doc = { @doc ("The number of variables declared in the problem. Counts the variables of the model, not the intermediate ones a constraint engine may create while decomposing an expression") }),
		@variable (
				name = "nb_constraints",
				type = IType.INT,
				doc = { @doc ("The number of constraints posted to the problem. Counted by the plugin rather than by an engine, so it reads the same whichever engine solves the problem") }),
		@variable (
				name = "variables",
				type = IType.LIST,
				of = GamaVariableType.id,
				doc = { @doc ("The list of the variables declared in the problem, in declaration order") }),
		@variable (
				name = "solutions",
				type = IType.INT,
				doc = { @doc ("How many solutions the last search found. A constraint engine counts every solution it went through on its way to the last one; a linear engine reports 1 when it came back with an assignment and 0 otherwise, since it does not enumerate") }),
		@variable (
				name = "search_time",
				type = IType.FLOAT,
				doc = { @doc ("The time, in seconds, spent in the last search, as reported by whichever engine ran it. Filled in on every engine") }),
		@variable (
				name = "nodes",
				type = IType.INT,
				doc = { @doc ("The number of nodes explored during the last search. Filled in on every engine, but it counts a branch and bound tree: a problem with no integer variable is settled without branching and genuinely reports zero, as does one small enough to be decided before branching starts") }),
		@variable (
				name = "fails",
				type = IType.INT,
				doc = { @doc ("The number of failures encountered during the last search. A failure is a dead end reached by propagation, which only a constraint engine has; on a linear engine this stays at zero") }) })
public class GamaProblem implements IValue {

	/** The engine this problem is solved with. Choco is one of them, not the shape the others have to fit. */
	private final SolverEngine engine;

	/** The name of the problem, held here rather than read back from an engine that may not keep one. */
	private final String name;

	/** The variables declared through this problem, by name, in declaration order. */
	private final Map<String, GamaVariable> declared = new LinkedHashMap<>();


	/** Counter used to generate unique names for anonymous (derived) variables. */
	private int anonymous;

	/** The engine this problem is solved with. */
	private final Backend backend;

	/** The constraints posted to this problem, in the order they were posted. */
	private final List<GamaConstraint> posted = new ArrayList<>();

	/** The objective declared with the problem, when it was read from a file, as a linear form. */
	private gama.plugin.constraintprogramming.terms.Term objective;

	/** Whether that objective has to be maximised. */
	private boolean maximises;

	/** The engines a problem can be solved with. */
	public enum Backend {

		/** Choco, the constraint engine. Handles everything the plugin exposes. */
		CHOCO("choco"),

		/** Choco with lazy clause generation. */
		CHOCO_LCG("choco_lcg"),

		/** The linear engine bundled with Choco. Only accepts linear constraints. */
		LP("lp"),

		/** HiGHS, a linear and mixed integer engine, loaded from a binary shipped with the plugin. */
		HIGHS("highs");

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
		this.name = name == null ? "problem" : name;
		this.engine = switch (backend) {
			case CHOCO -> new ChocoEngine(this.name, false);
			case CHOCO_LCG -> new ChocoEngine(this.name, true);
			case LP -> new LinearEngine();
			case HIGHS -> new HighsEngine();
		};
	}

	/**
	 * Gets the engine that solves this problem.
	 *
	 * @return the engine
	 */
	public SolverEngine getEngine() { return engine; }

	/**
	 * Gets the engine as a constraint engine, for an operator only a constraint engine can honour.
	 *
	 * <p>
	 * Asking for the Choco engine and checking that this problem has one are the same step here, so an operator cannot
	 * reach a Choco solver without having said which operator it is and what to do instead.
	 * </p>
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @param operator
	 *            the name of the operator asking, named in the error
	 * @return the constraint engine
	 * @throws GamaRuntimeException
	 *             if this problem is solved by another engine
	 */
	public ChocoEngine requireChoco(final IScope scope, final String operator) throws GamaRuntimeException {
		if (engine instanceof ChocoEngine c) return c;
		throw GamaRuntimeException.error(operator + " is only available with the 'choco' engine, and this problem "
				+ "uses '" + backend.getLabel() + "'.", scope);
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
	public boolean isLinear() { return backend == Backend.LP || backend == Backend.HIGHS; }

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
	 * Declares the objective of the problem, as stated by the file it was read from.
	 *
	 * @param objective
	 *            the variable to optimise
	 * @param maximises
	 *            whether it has to be maximised
	 */
	public void setObjective(final gama.plugin.constraintprogramming.terms.Term objective, final boolean maximises) {
		this.objective = objective;
		this.maximises = maximises;
	}

	/**
	 * Gets the objective declared with the problem, if any.
	 *
	 * @return the objective, or null
	 */
	public gama.plugin.constraintprogramming.terms.Term getObjective() { return objective; }

	/**
	 * Whether the declared objective has to be maximised.
	 *
	 * @return true if it has to be maximised
	 */
	public boolean maximises() { return maximises; }

	/**
	 * Gets the underlying Choco model.
	 *
	 * @return the model
	 */
	public Model getModel() { return requireChoco(null, "This operation").getModel(); }

	/**
	 * Gets the Choco solver attached to the model.
	 *
	 * @return the solver
	 */
	public Model getModelIfAny() { return engine instanceof ChocoEngine c ? c.getModel() : null; }

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
	 * Registers a variable described independently of any engine.
	 *
	 * @param v
	 *            the variable
	 * @return the same variable, for chaining
	 */
	public GamaVariable register(final GamaVariable v) {
		declared.put(v.getVariableName(), v);
		// A constraint engine searches over the variables its model holds, so every declared variable has to reach it,
		// including one no constraint ever mentions: it still takes a value in a solution. A linear engine reads the
		// descriptions instead and needs nothing built here.
		if (!isLinear()) { v.getVariable(); }
		return v;
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
	public String getProblemName() { return name; }

	@getter ("nb_variables")
	public int getNbVariables() { return declared.size(); }

	@getter ("nb_constraints")
	public int getNbConstraints() { return posted.size(); }

	@getter ("variables")
	public IList<GamaVariable> getVariables() {
		final IList<GamaVariable> result = GamaListFactory.create(Types.get(GamaVariableType.id));
		result.addAll(declared.values());
		return result;
	}

	@getter ("solutions")
	public int getNbSolutions() {
		return engine.getSolutions();
	}

	@getter ("search_time")
	public double getSearchTime() { return engine.getSearchTime(); }

	@getter ("nodes")
	public int getNodes() { return (int) engine.getNodes(); }

	@getter ("fails")
	public int getFails() { return (int) engine.getFails(); }

	@Override
	public IType<?> getGamlType() { return Types.get(GamaProblemType.id); }

	@Override
	public String stringValue(final IScope scope) throws GamaRuntimeException {
		return "problem " + name + " (" + getNbVariables() + " variables, " + getNbConstraints()
				+ " constraints)";
	}

	@Override
	public String serializeToGaml(final boolean includingBuiltIn) {
		return "problem(\"" + name + "\")";
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
		return json.typedObject(getGamlType()).add("name", name).add("nb_variables", getNbVariables())
				.add("nb_constraints", getNbConstraints());
	}

}

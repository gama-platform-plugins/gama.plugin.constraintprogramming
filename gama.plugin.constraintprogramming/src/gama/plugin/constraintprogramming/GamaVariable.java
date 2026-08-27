package gama.plugin.constraintprogramming;

import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.RealVar;
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
import gama.api.types.misc.IValue;
import gama.api.utils.json.IJson;
import gama.api.utils.json.IJsonValue;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * A decision variable of a {@link GamaProblem}. Wraps a Choco {@link Variable}, whatever its kind (integer, boolean, set or
 * real), and keeps a reference to the problem that declared it so that derived variables and constraints can be built
 * from it without having to pass the problem around.
 */
@vars ({ @variable (
		name = "name",
		type = IType.STRING,
		doc = { @doc ("The name of the variable in the problem") }),
		@variable (
				name = "kind",
				type = IType.STRING,
				doc = { @doc ("The kind of the variable: 'int', 'bool', 'set', 'real' or 'other'") }),
		@variable (
				name = "lb",
				type = IType.INT,
				doc = { @doc ("The current lower bound of the domain. Only meaningful for int and bool variables") }),
		@variable (
				name = "ub",
				type = IType.INT,
				doc = { @doc ("The current upper bound of the domain. Only meaningful for int and bool variables") }),
		@variable (
				name = "instantiated",
				type = IType.BOOL,
				doc = { @doc ("Whether the domain of the variable has been reduced to a single value") }),
		@variable (
				name = "value",
				type = IType.INT,
				doc = { @doc ("The value of the variable if it is instantiated, nil otherwise. To read the value of a variable in a given solution, use value_of(assignment, variable) instead") }) })
public class GamaVariable implements IValue {

	/** The problem this variable belongs to. */
	private final GamaProblem problem;

	/** The underlying Choco variable, built on demand and null until something needs one. */
	private Variable variable;

	/**
	 * What kind of variable this is, independently of any engine. Null only for a wrapper built directly around a Choco
	 * variable, which is still how set variables are declared, since a set domain is not described by two bounds.
	 */
	private final Kind kind;

	/** The name of the variable, null when it is carried by the Choco variable itself. */
	private final String name;

	/** The lower bound of the domain, meaningless for a set variable or an expression. */
	private final double lb;

	/** The upper bound of the domain, meaningless for a set variable or an expression. */
	private final double ub;

	/** Whether an integer variable is better held by its bounds than by its values, a hint for Choco only. */
	private final boolean bounded;

	/** The values an integer variable may take, null when its domain is given by its bounds. */
	private final int[] domain;

	/** The kinds of variable a problem can declare, as the plugin sees them rather than as Choco does. */
	public enum Kind {

		/** A boolean variable. */
		BOOL("bool"),
		/** An integer variable. */
		INT("int"),
		/** A continuous variable. */
		REAL("real"),
		/** A set variable. */
		SET("set"),
		/** Not a declared variable, but an unevaluated arithmetic term. */
		EXPRESSION("expression");

		/** The name the kind is reported under in GAML. */
		private final String label;

		Kind(final String label) {
			this.label = label;
		}

		/**
		 * The name this kind is reported under.
		 *
		 * @return the label
		 */
		public String getLabel() { return label; }
	}

	/**
	 * The arithmetic term this wrapper stands for, null when it wraps a declared variable. Terms are kept unevaluated
	 * and backend-neutral, so that the whole tree can be handed at once to whichever engine the problem uses.
	 */
	private final Term term;

	/**
	 * Instantiates a new variable wrapper.
	 *
	 * @param problem
	 *            the problem that declared the variable
	 * @param variable
	 *            the Choco variable
	 */
	public GamaVariable(final GamaProblem problem, final Variable variable) {
		this.problem = problem;
		this.variable = variable;
		this.term = null;
		this.kind = null;
		this.name = null;
		this.lb = 0;
		this.ub = 0;
		this.bounded = false;
		this.domain = null;
	}

	/**
	 * Instantiates a variable from its description alone, without building anything for an engine.
	 *
	 * <p>
	 * This is how variables are declared. The Choco form is built by {@link #getVariable()} the first time one is asked
	 * for, which happens only on a constraint engine: a linear engine reads the description and materialises nothing,
	 * where it used to pay for one Choco variable per declared variable.
	 * </p>
	 *
	 * @param problem
	 *            the problem that declares the variable
	 * @param name
	 *            its name
	 * @param kind
	 *            what kind of variable it is
	 * @param lb
	 *            the lower bound of its domain
	 * @param ub
	 *            the upper bound of its domain
	 * @param bounded
	 *            whether Choco should hold it by its bounds rather than by its values
	 * @param domain
	 *            the values it may take, or null when the bounds describe the domain
	 */
	private GamaVariable(final GamaProblem problem, final String name, final Kind kind, final double lb,
			final double ub, final boolean bounded, final int[] domain) {
		this.problem = problem;
		this.variable = null;
		this.term = null;
		this.kind = kind;
		this.name = name;
		this.lb = lb;
		this.ub = ub;
		this.bounded = bounded;
		this.domain = domain;
	}

	/**
	 * Describes a boolean variable.
	 *
	 * @param problem
	 *            the problem
	 * @param name
	 *            its name
	 * @return the variable
	 */
	public static GamaVariable ofBool(final GamaProblem problem, final String name) {
		return new GamaVariable(problem, name, Kind.BOOL, 0, 1, false, null);
	}

	/**
	 * Describes an integer variable by its bounds.
	 *
	 * @param problem
	 *            the problem
	 * @param name
	 *            its name
	 * @param lb
	 *            the lower bound
	 * @param ub
	 *            the upper bound
	 * @param bounded
	 *            whether Choco should hold it by its bounds rather than by its values
	 * @return the variable
	 */
	public static GamaVariable ofInt(final GamaProblem problem, final String name, final int lb, final int ub,
			final boolean bounded) {
		return new GamaVariable(problem, name, Kind.INT, lb, ub, bounded, null);
	}

	/**
	 * Describes an integer variable by the values it may take.
	 *
	 * @param problem
	 *            the problem
	 * @param name
	 *            its name
	 * @param values
	 *            the values, which must not be empty
	 * @return the variable
	 */
	public static GamaVariable ofInt(final GamaProblem problem, final String name, final int[] values) {
		int low = values[0];
		int high = values[0];
		for (final int v : values) {
			if (v < low) { low = v; }
			if (v > high) { high = v; }
		}
		return new GamaVariable(problem, name, Kind.INT, low, high, false, values);
	}

	/**
	 * Describes a continuous variable.
	 *
	 * @param problem
	 *            the problem
	 * @param name
	 *            its name
	 * @param lb
	 *            the lower bound
	 * @param ub
	 *            the upper bound
	 * @return the variable
	 */
	public static GamaVariable ofReal(final GamaProblem problem, final String name, final double lb, final double ub) {
		return new GamaVariable(problem, name, Kind.REAL, lb, ub, false, null);
	}

	/**
	 * Instantiates a wrapper around an arithmetic term that has not been materialised yet.
	 *
	 * @param problem
	 *            the problem the term is expressed over
	 * @param term
	 *            the term
	 */
	public GamaVariable(final GamaProblem problem, final Term term) {
		this.problem = problem;
		this.variable = null;
		this.term = term;
		this.kind = Kind.EXPRESSION;
		this.name = null;
		this.lb = 0;
		this.ub = 0;
		this.bounded = false;
		this.domain = null;
	}

	/**
	 * Gets the term this wrapper stands for.
	 *
	 * @return the term, or null if it wraps a declared variable
	 */
	public Term getTerm() { return term; }

	/**
	 * Whether this wrapper holds an expression that has not been turned into a variable yet.
	 *
	 * @return true if it is a pure expression
	 */
	public boolean isExpression() { return term != null && variable == null; }

	/**
	 * Returns this as a Choco arithmetic expression. Only called by the Choco compiler, on a leaf of a term tree.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @return the expression
	 * @throws GamaRuntimeException
	 *             if this wraps a variable that is not an integer one
	 */
	public org.chocosolver.solver.expression.discrete.arithmetic.ArExpression asChocoExpression(final IScope scope)
			throws GamaRuntimeException {
		if (term != null) return ChocoCompiler.compile(scope, problem, term);
		if (getVariable() instanceof IntVar iv) return iv;
		throw GamaRuntimeException.error("The variable " + getVariableName() + " is a " + getKind()
				+ " variable, and cannot take part in an arithmetic expression", scope);
	}

	/**
	 * Gets the problem this variable belongs to.
	 *
	 * @return the problem
	 */
	public GamaProblem getProblem() { return problem; }

	/**
	 * Gets the underlying Choco variable.
	 *
	 * @return the variable
	 */
	public Variable getVariable() {
		if (variable == null && name != null) { variable = materialise(); }
		return variable;
	}

	/**
	 * Builds the Choco form of this variable from its description.
	 *
	 * @return the Choco variable
	 */
	private Variable materialise() {
		final var model = problem.getModel();
		return switch (kind) {
			case BOOL -> model.boolVar(name);
			case INT -> domain == null ? model.intVar(name, (int) lb, (int) ub, bounded) : model.intVar(name, domain);
			case REAL -> model.realVar(name, lb, ub, model.getPrecision());
			default -> null;
		};
	}

	/**
	 * What kind of variable this is, without building anything for an engine.
	 *
	 * @return the kind, or null if it cannot be told
	 */
	public Kind getVariableKind() {
		if (kind != null) return kind;
		if (variable instanceof BoolVar) return Kind.BOOL;
		if (variable instanceof IntVar) return Kind.INT;
		if (variable instanceof SetVar) return Kind.SET;
		if (variable instanceof RealVar) return Kind.REAL;
		return null;
	}

	/**
	 * The lower bound of the domain, as it was declared.
	 *
	 * @return the lower bound
	 */
	public double getLowerBound() {
		if (kind != null) return lb;
		if (variable instanceof IntVar iv) return iv.getLB();
		if (variable instanceof RealVar rv) return rv.getLB();
		return 0;
	}

	/**
	 * The upper bound of the domain, as it was declared.
	 *
	 * @return the upper bound
	 */
	public double getUpperBound() {
		if (kind != null) return ub;
		if (variable instanceof IntVar iv) return iv.getUB();
		if (variable instanceof RealVar rv) return rv.getUB();
		return 0;
	}

	/**
	 * Gets the underlying variable as an integer variable.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @return the variable, seen as an IntVar
	 * @throws GamaRuntimeException
	 *             if the variable is not an integer (or boolean) variable
	 */
	public IntVar asIntVar(final IScope scope) throws GamaRuntimeException {
		if (getVariable() instanceof IntVar iv) return iv;
		if (variable == null && term != null) {
			// The term is materialised on first use and kept, so that reading it twice does not add a second variable
			// and a second propagator to the problem.
			final IntVar materialised = ChocoCompiler.compile(scope, problem, term).intVar();
			variable = materialised;
			problem.register(materialised);
			return materialised;
		}
		throw GamaRuntimeException.error("The variable " + getVariableName() + " is a " + getKind()
				+ " variable, whereas an int variable is expected here", scope);
	}

	/**
	 * Gets the underlying variable as a boolean variable.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @return the variable, seen as a BoolVar
	 * @throws GamaRuntimeException
	 *             if the variable is not a boolean variable
	 */
	public BoolVar asBoolVar(final IScope scope) throws GamaRuntimeException {
		if (getVariable() instanceof BoolVar bv) return bv;
		throw GamaRuntimeException.error("The variable " + getVariableName() + " is a " + getKind()
				+ " variable, whereas a bool variable is expected here", scope);
	}

	/**
	 * Gets the underlying variable as a real variable.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @return the variable, seen as a RealVar
	 * @throws GamaRuntimeException
	 *             if the variable is not a real variable
	 */
	public RealVar asRealVar(final IScope scope) throws GamaRuntimeException {
		if (getVariable() instanceof RealVar rv) return rv;
		throw GamaRuntimeException.error("The variable " + getVariableName() + " is a " + getKind()
				+ " variable, whereas a real variable is expected here", scope);
	}

	/**
	 * Whether this wraps a real variable.
	 *
	 * @return true if it is a real variable
	 */
	public boolean isReal() { return getVariableKind() == Kind.REAL; }

	/**
	 * Gets the underlying variable as a set variable.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @return the variable, seen as a SetVar
	 * @throws GamaRuntimeException
	 *             if the variable is not a set variable
	 */
	public SetVar asSetVar(final IScope scope) throws GamaRuntimeException {
		if (getVariable() instanceof SetVar sv) return sv;
		throw GamaRuntimeException.error("The variable " + getVariableName() + " is a " + getKind()
				+ " variable, whereas a set variable is expected here", scope);
	}

	@getter ("name")
	public String getVariableName() {
		if (name != null) return name;
		return variable == null ? "(expression)" : variable.getName();
	}

	@getter ("kind")
	public String getKind() {
		final Kind k = getVariableKind();
		return k == null ? "other" : k.getLabel();
	}

	@getter ("lb")
	public Integer getLb() {
		final Kind k = getVariableKind();
		return k == Kind.INT || k == Kind.BOOL ? (int) getLowerBound() : null;
	}

	@getter ("ub")
	public Integer getUb() {
		final Kind k = getVariableKind();
		return k == Kind.INT || k == Kind.BOOL ? (int) getUpperBound() : null;
	}

	@getter ("instantiated")
	public boolean isInstantiated() { return variable != null && variable.isInstantiated(); }

	@getter ("value")
	public Integer getValue() {
		if (variable instanceof IntVar iv && iv.isInstantiated()) return iv.getValue();
		return null;
	}

	@Override
	public IType<?> getGamlType() { return Types.get(GamaVariableType.id); }

	@Override
	public String stringValue(final IScope scope) throws GamaRuntimeException {
		if (term != null) return term.describe();
		return variable == null ? getVariableName() : variable.toString();
	}

	@Override
	public String serializeToGaml(final boolean includingBuiltIn) {
		return getVariableName();
	}

	/**
	 * A variable belongs to the problem that declared it and is identified by the constraints posted over it: copying it
	 * would be meaningless. Returns itself.
	 */
	@Override
	public IValue copy(final IScope scope) throws GamaRuntimeException {
		return this;
	}

	@Override
	public int intValue(final IScope scope) {
		final Integer v = getValue();
		return v == null ? 0 : v;
	}

	@Override
	public IJsonValue serializeToJson(final IJson json) {
		return json.typedObject(getGamlType()).add("name", getVariableName()).add("kind", getKind());
	}

}

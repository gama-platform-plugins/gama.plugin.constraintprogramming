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

	/** The underlying Choco variable, null while this is a pure expression. */
	private Variable variable;

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
		if (variable instanceof IntVar iv) return iv;
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
	public Variable getVariable() { return variable; }

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
		if (variable instanceof IntVar iv) return iv;
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
		if (variable instanceof BoolVar bv) return bv;
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
		if (variable instanceof RealVar rv) return rv;
		throw GamaRuntimeException.error("The variable " + getVariableName() + " is a " + getKind()
				+ " variable, whereas a real variable is expected here", scope);
	}

	/**
	 * Whether this wraps a real variable.
	 *
	 * @return true if it is a real variable
	 */
	public boolean isReal() { return variable instanceof RealVar; }

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
		if (variable instanceof SetVar sv) return sv;
		throw GamaRuntimeException.error("The variable " + getVariableName() + " is a " + getKind()
				+ " variable, whereas a set variable is expected here", scope);
	}

	@getter ("name")
	public String getVariableName() { return variable == null ? "(expression)" : variable.getName(); }

	@getter ("kind")
	public String getKind() {
		if (variable == null) return "expression";
		if (variable instanceof BoolVar) return "bool";
		if (variable instanceof IntVar) return "int";
		if (variable instanceof SetVar) return "set";
		if (variable instanceof RealVar) return "real";
		return "other";
	}

	@getter ("lb")
	public Integer getLb() {
		if (variable instanceof IntVar iv) return iv.getLB();
		return null;
	}

	@getter ("ub")
	public Integer getUb() {
		if (variable instanceof IntVar iv) return iv.getUB();
		return null;
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
		return variable == null ? term.describe() : variable.toString();
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

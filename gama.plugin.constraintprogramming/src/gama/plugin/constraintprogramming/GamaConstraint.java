package gama.plugin.constraintprogramming;

import org.chocosolver.solver.constraints.Constraint;

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

/**
 * A constraint over the variables of a {@link GamaProblem}. Wraps a Choco {@link Constraint}.
 *
 * <p>
 * Building a constraint does not add it to the problem: it has to be posted explicitly, with the {@code post}
 * statement. A constraint that is not posted can still be used as a building block, for instance to be reified into a
 * boolean variable, or combined with others through {@code and_all} and {@code or_all}.
 * </p>
 */
@vars ({ @variable (
		name = "name",
		type = IType.STRING,
		doc = { @doc ("The name of the constraint, as given by the solver") }),
		@variable (
				name = "posted",
				type = IType.BOOL,
				doc = { @doc ("Whether the constraint has already been posted to its problem") }) })
public class GamaConstraint implements IValue {

	/** The problem this constraint refers to. */
	private final GamaProblem problem;

	/** The underlying Choco constraint. */
	private final Constraint constraint;

	/** Whether the constraint has been posted. */
	private boolean posted;

	/**
	 * Instantiates a new constraint wrapper.
	 *
	 * @param problem
	 *            the problem over whose variables the constraint is expressed
	 * @param constraint
	 *            the Choco constraint
	 */
	public GamaConstraint(final GamaProblem problem, final Constraint constraint) {
		this.problem = problem;
		this.constraint = constraint;
	}

	/**
	 * Gets the problem this constraint refers to.
	 *
	 * @return the problem
	 */
	public GamaProblem getProblem() { return problem; }

	/**
	 * Gets the underlying Choco constraint.
	 *
	 * @return the constraint
	 */
	public Constraint getConstraint() { return constraint; }

	/**
	 * Posts the constraint to its problem. Posting twice is a no-op rather than an error, so that a constraint held in a
	 * GAML variable and posted in a loop does not silently duplicate propagators.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @throws GamaRuntimeException
	 *             if the solver refuses the constraint
	 */
	public void post(final IScope scope) throws GamaRuntimeException {
		if (posted) return;
		try {
			constraint.post();
			posted = true;
		} catch (final Exception e) {
			throw GamaRuntimeException.error("Impossible to post the constraint " + constraint.getName() + ": "
					+ e.getMessage(), scope);
		}
	}

	@getter ("name")
	public String getConstraintName() { return constraint.getName(); }

	@getter ("posted")
	public boolean isPosted() { return posted; }

	@Override
	public IType<?> getGamlType() { return Types.get(GamaConstraintType.id); }

	@Override
	public String stringValue(final IScope scope) throws GamaRuntimeException {
		return constraint.toString();
	}

	@Override
	public String serializeToGaml(final boolean includingBuiltIn) {
		return constraint.getName();
	}

	@Override
	public IValue copy(final IScope scope) throws GamaRuntimeException {
		return this;
	}

	@Override
	public IJsonValue serializeToJson(final IJson json) {
		return json.typedObject(getGamlType()).add("name", constraint.getName()).add("posted", posted);
	}

}

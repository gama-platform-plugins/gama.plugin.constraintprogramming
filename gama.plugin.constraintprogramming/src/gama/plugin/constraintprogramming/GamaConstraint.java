package gama.plugin.constraintprogramming;

import java.util.function.Supplier;

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
import gama.plugin.constraintprogramming.terms.Relation;

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

	/** The underlying Choco constraint, built on demand and never before. */
	private Constraint constraint;

	/**
	 * Builds the Choco form when one is asked for, null when the constraint is carried by its relation alone.
	 *
	 * <p>
	 * Held as a supplier rather than as a built constraint so that nothing Choco exists until an engine that speaks
	 * Choco asks for it. A linear engine reads the relation and never calls this, so declaring a constraint on a linear
	 * problem no longer creates the propagators, nor the auxiliary variables, of a model that will not be searched.
	 * </p>
	 */
	private Supplier<Constraint> builder;

	/** Whether the constraint has been posted. */
	private boolean posted;

	/** The relation this constraint was compiled from, null when it comes from a global constraint. */
	private final Relation relation;

	/**
	 * Instantiates a new constraint wrapper.
	 *
	 * @param problem
	 *            the problem over whose variables the constraint is expressed
	 * @param builder
	 *            builds the Choco constraint when one is needed
	 */
	public GamaConstraint(final GamaProblem problem, final Supplier<Constraint> builder) {
		this(problem, builder, null);
	}

	/**
	 * Instantiates a new constraint wrapper, remembering the relation it was decomposed from so that it can be
	 * recompiled differently, for instance into a table by {@code as_table}.
	 *
	 * @param problem
	 *            the problem over whose variables the constraint is expressed
	 * @param builder
	 *            builds the Choco constraint when one is needed
	 * @param relation
	 *            the relation it came from, or null
	 */
	public GamaConstraint(final GamaProblem problem, final Supplier<Constraint> builder, final Relation relation) {
		this.problem = problem;
		this.builder = builder;
		this.relation = relation;
	}

	/**
	 * Instantiates a constraint from a relation, without compiling it. The relation stays in its neutral form until an
	 * engine asks for it, which is what lets a linear engine read it directly instead of going through Choco.
	 *
	 * @param problem
	 *            the problem
	 * @param relation
	 *            the relation
	 */
	public GamaConstraint(final GamaProblem problem, final Relation relation) {
		this.problem = problem;
		this.builder = null;
		this.relation = relation;
	}

	/**
	 * Gets the relation this constraint was built from.
	 *
	 * @return the relation, or null if the constraint does not come from an arithmetic expression
	 */
	public Relation getRelation() { return relation; }

	/**
	 * Gets the problem this constraint refers to.
	 *
	 * @return the problem
	 */
	public GamaProblem getProblem() { return problem; }

	/**
	 * Gets the underlying Choco constraint, if one has been built.
	 *
	 * @return the constraint, or null while none has been asked for
	 */
	public Constraint getConstraint() { return constraint; }

	/**
	 * Returns the Choco constraint, compiling the relation the first time it is asked for.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @return the Choco constraint
	 */
	public Constraint getChocoConstraint(final IScope scope) throws GamaRuntimeException {
		if (constraint == null) {
			if (builder != null) {
				constraint = builder.get();
			} else if (relation != null) {
				constraint = ChocoCompiler.compile(scope, problem, relation).decompose();
			} else throw GamaRuntimeException.error("This constraint has neither a compiled form nor a relation", scope);
		}
		return constraint;
	}

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
		if (problem.isLinear()) {
			// A linear engine reads the relations at solve time; there is nothing to hand to Choco, and compiling the
			// relation for it would build propagators no one would ever run.
			if (relation == null) throw GamaRuntimeException.error("The constraint " + getConstraintName()
					+ " is a global constraint, which the '" + problem.getBackend().getLabel()
					+ "' engine does not handle. Use the 'choco' engine, or express it with linear constraints.", scope);
			posted = true;
			problem.recordPosted(this);
			return;
		}
		try {
			getChocoConstraint(scope).post();
			posted = true;
			problem.recordPosted(this);
		} catch (final GamaRuntimeException e) {
			throw e;
		} catch (final Exception e) {
			throw GamaRuntimeException
					.error("Impossible to post the constraint " + getConstraintName() + ": " + e.getMessage(), scope);
		}
	}

	@getter ("name")
	public String getConstraintName() {
		if (constraint != null) return constraint.getName();
		return relation == null ? "constraint" : relation.describe();
	}

	@getter ("posted")
	public boolean isPosted() { return posted; }

	@Override
	public IType<?> getGamlType() { return Types.get(GamaConstraintType.id); }

	@Override
	public String stringValue(final IScope scope) throws GamaRuntimeException {
		if (constraint != null) return constraint.toString();
		return relation.describe();
	}

	@Override
	public String serializeToGaml(final boolean includingBuiltIn) {
		return getConstraintName();
	}

	@Override
	public IValue copy(final IScope scope) throws GamaRuntimeException {
		return this;
	}

	@Override
	public IJsonValue serializeToJson(final IJson json) {
		return json.typedObject(getGamlType()).add("name", getConstraintName()).add("posted", posted);
	}

}

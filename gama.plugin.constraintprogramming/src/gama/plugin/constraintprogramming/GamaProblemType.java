package gama.plugin.constraintprogramming;

import gama.annotations.doc;
import gama.annotations.type;
import gama.annotations.support.IConcept;
import gama.annotations.support.ISymbolKind;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.GamaType;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.ITypesManager;
import gama.api.runtime.scope.IScope;

/**
 * The GAML type of {@link GamaProblem}. Casting a string to a problem creates a new, empty problem bearing that name, which
 * makes {@code problem("my_problem")} the natural way to start building a model.
 */
@type (
		name = "problem",
		id = GamaProblemType.id,
		wraps = { GamaProblem.class },
		kind = ISymbolKind.REGULAR,
		concept = { IConcept.TYPE, IConcept.OPTIMIZATION })
@doc ("A constraint satisfaction or optimisation problem: a set of variables, a set of constraints over them, and a solver able to search for assignments satisfying all the constraints.")
public class GamaProblemType extends GamaType<GamaProblem> {

	/** The id of the type. */
	public final static int id = IType.BEGINNING_OF_CUSTOM_TYPES + 771001;

	/**
	 * Instantiates a new problem type.
	 *
	 * @param typesManager
	 *            the types manager
	 */
	public GamaProblemType(final ITypesManager typesManager) {
		super(typesManager);
	}

	@Override
	public boolean canCastToConst() {
		return false;
	}

	@Override
	@doc ("Returns the argument if it is already a problem; creates a new empty problem named after the argument otherwise")
	public GamaProblem cast(final IScope scope, final Object obj, final Object param, final boolean copy)
			throws GamaRuntimeException {
		if (obj instanceof GamaProblem p) return p;
		if (obj == null) return null;
		return new GamaProblem(obj.toString());
	}

	@Override
	public GamaProblem getDefault() { return null; }

}

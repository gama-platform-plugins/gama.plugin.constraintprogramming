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
 * The GAML type of {@link GamaSolution}.
 */
@type (
		name = "solution",
		id = GamaSolutionType.id,
		wraps = { GamaSolution.class },
		kind = ISymbolKind.REGULAR,
		concept = { IConcept.TYPE, IConcept.OPTIMIZATION })
@doc ("A solution of a problem: the value taken by each of its variables. Returned by the search, minimize and maximize operators.")
public class GamaSolutionType extends GamaType<GamaSolution> {

	/** The id of the type. */
	public final static int id = IType.BEGINNING_OF_CUSTOM_TYPES + 771004;

	/**
	 * Instantiates a new solution type.
	 *
	 * @param typesManager
	 *            the types manager
	 */
	public GamaSolutionType(final ITypesManager typesManager) {
		super(typesManager);
	}

	@Override
	public boolean canCastToConst() {
		return false;
	}

	@Override
	@doc ("Returns the argument if it is a solution, nil otherwise")
	public GamaSolution cast(final IScope scope, final Object obj, final Object param, final boolean copy)
			throws GamaRuntimeException {
		if (obj instanceof GamaSolution s) return s;
		return null;
	}

	@Override
	public GamaSolution getDefault() { return null; }

}

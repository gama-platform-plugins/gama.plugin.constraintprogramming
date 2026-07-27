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
 * The GAML type of {@link GamaConstraint}.
 */
@type (
		name = "constraint",
		id = GamaConstraintType.id,
		wraps = { GamaConstraint.class },
		kind = ISymbolKind.REGULAR,
		concept = { IConcept.TYPE, IConcept.OPTIMIZATION })
@doc ("A relation that the variables of a problem must satisfy. Constraints are built by the constraint operators (all_different, arithm, element, ...) and added to the problem by the post statement.")
public class GamaConstraintType extends GamaType<GamaConstraint> {

	/** The id of the type. */
	public final static int id = IType.BEGINNING_OF_CUSTOM_TYPES + 771003;

	/**
	 * Instantiates a new constraint type.
	 *
	 * @param typesManager
	 *            the types manager
	 */
	public GamaConstraintType(final ITypesManager typesManager) {
		super(typesManager);
	}

	@Override
	public boolean canCastToConst() {
		return false;
	}

	@Override
	@doc ("Returns the argument if it is a constraint, nil otherwise")
	public GamaConstraint cast(final IScope scope, final Object obj, final Object param, final boolean copy)
			throws GamaRuntimeException {
		if (obj instanceof GamaConstraint c) return c;
		return null;
	}

	@Override
	public GamaConstraint getDefault() { return null; }

}

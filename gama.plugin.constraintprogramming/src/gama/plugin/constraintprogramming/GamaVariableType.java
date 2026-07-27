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
 * The GAML type of {@link GamaVariable}. Variables are never created by casting: they are always obtained from one of the
 * declaration operators ({@code int_var}, {@code bool_var}, ...) applied to a problem.
 */
@type (
		name = "pb_variable",
		id = GamaVariableType.id,
		wraps = { GamaVariable.class },
		kind = ISymbolKind.REGULAR,
		concept = { IConcept.TYPE, IConcept.OPTIMIZATION })
@doc ("A decision variable of a problem: an unknown whose value the solver has to determine, within the domain given at its declaration.")
public class GamaVariableType extends GamaType<GamaVariable> {

	/** The id of the type. */
	public final static int id = IType.BEGINNING_OF_CUSTOM_TYPES + 771002;

	/**
	 * Instantiates a new variable type.
	 *
	 * @param typesManager
	 *            the types manager
	 */
	public GamaVariableType(final ITypesManager typesManager) {
		super(typesManager);
	}

	@Override
	public boolean canCastToConst() {
		return false;
	}

	@Override
	@doc ("Returns the argument if it is a variable of a problem, nil otherwise")
	public GamaVariable cast(final IScope scope, final Object obj, final Object param, final boolean copy)
			throws GamaRuntimeException {
		if (obj instanceof GamaVariable v) return v;
		return null;
	}

	@Override
	public GamaVariable getDefault() { return null; }

}

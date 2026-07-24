package gama.plugin.constraintprogramming;

import gama.annotations.action;
import gama.annotations.doc;
import gama.annotations.skill;
import gama.api.kernel.skill.Skill;
import gama.api.runtime.scope.IScope;

/**
 * Entry point for your GAML skill.
 *
 * In GAML, agents can use this skill with:
 *   species my_agent skills: [constraintprogramming_skill] { ... }
 *
 * Annotate methods with @action, @getter, @setter to expose them to GAML.
 * The GamaProcessor annotation processor generates the necessary wiring at compile time.
 */
@skill(name = "constraintprogramming_skill")
@doc("Sample skill — replace with your implementation.")
public class ConstraintprogrammingSkill extends Skill {

	@action(name = "constraintprogramming_action")
	@doc("Sample action — replace or remove.")
	public Object myAction(final IScope scope) {
		// TODO: implement
		return null;
	}

}

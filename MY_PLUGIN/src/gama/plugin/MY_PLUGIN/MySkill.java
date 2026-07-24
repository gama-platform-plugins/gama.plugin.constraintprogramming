package gama.plugin.MY_PLUGIN;

import gama.annotations.precompiler.GamaAnnotations.action;
import gama.annotations.precompiler.GamaAnnotations.doc;
import gama.annotations.precompiler.GamaAnnotations.skill;
import gama.gaml.skills.Skill;

/**
 * Entry point for your GAML skill.
 *
 * In GAML, agents can use this skill with:
 *   species my_agent skills: [my_skill] { ... }
 *
 * Annotate methods with @action, @getter, @setter to expose them to GAML.
 * The GamaProcessor annotation processor generates the necessary wiring at compile time.
 */
@skill(name = "my_skill")
@doc("Sample skill — replace with your implementation.")
public class MySkill extends Skill {

	@action(name = "my_action")
	@doc("Sample action — replace or remove.")
	public Object myAction(final gama.core.runtime.IScope scope) {
		// TODO: implement
		return null;
	}

}

package br.com.cronicasdavila.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;

import br.com.cronicasdavila.village.OffDutySystem;

/**
 * Folga: quando a rotina manda um aldeão de folga para o "modo trabalho", ele fica no "modo livre".
 * Assim ele não vai ao bloco de trabalho nem faz o serviço (o fazendeiro não colhe, por exemplo).
 */
@Mixin(Brain.class)
public abstract class BrainMixin {
	@ModifyVariable(method = "setActiveActivity", at = @At("HEAD"), argsOnly = true)
	private Activity cronicasdavila$dayOff(Activity activity) {
		if (activity == Activity.WORK && OffDutySystem.isBrainOff((Brain<?>) (Object) this)) {
			return Activity.IDLE;
		}

		return activity;
	}
}

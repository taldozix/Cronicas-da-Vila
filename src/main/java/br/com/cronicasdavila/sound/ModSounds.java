package br.com.cronicasdavila.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import br.com.cronicasdavila.CronicasDaVila;

/**
 * Sons próprios do mod (ficam em assets/cronicasdavila/sounds/ e são listados em sounds.json).
 */
public final class ModSounds {
	public static final SoundEvent TYPING = register("ui.typing");
	public static final SoundEvent SELECT = register("ui.select");
	public static final SoundEvent OPEN = register("ui.open");
	public static final SoundEvent HUD = register("ui.hud");
	public static final SoundEvent QUEST_ACCEPT = register("quest.accept");
	public static final SoundEvent QUEST_READY = register("quest.ready");
	public static final SoundEvent QUEST_COMPLETE = register("quest.complete");
	public static final SoundEvent QUEST_ABANDON = register("quest.abandon");
	public static final SoundEvent LEVEL_UP = register("quest.levelup");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = CronicasDaVila.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		// Força o carregamento da classe (registra os sons).
	}
}

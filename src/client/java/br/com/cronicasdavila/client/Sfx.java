package br.com.cronicasdavila.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import br.com.cronicasdavila.sound.ModSounds;

/**
 * Sons da interface, tocados só para o jogador (não saem no mundo).
 */
final class Sfx {
	private static long lastTyping;

	private Sfx() {
	}

	static void play(SoundEvent sound, float pitch, float volume) {
		if (!ClientConfig.sounds()) {
			return;
		}

		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	static void select() {
		play(ModSounds.SELECT, 1.0F, 0.5F);
	}

	static void open() {
		play(ModSounds.OPEN, 1.0F, 0.45F);
	}

	static void hud() {
		play(ModSounds.HUD, 1.0F, 0.5F);
	}

	/** Pelo id que o servidor manda ("quest.complete", "quest.levelup"...). */
	static void byId(String id) {
		switch (id) {
			case "quest.accept" -> play(ModSounds.QUEST_ACCEPT, 1.0F, 0.7F);
			case "quest.ready" -> play(ModSounds.QUEST_READY, 1.0F, 0.6F);
			case "quest.complete" -> play(ModSounds.QUEST_COMPLETE, 1.0F, 0.75F);
			case "quest.abandon" -> play(ModSounds.QUEST_ABANDON, 1.0F, 0.7F);
			case "quest.levelup" -> play(ModSounds.LEVEL_UP, 1.0F, 0.85F);
			default -> {
			}
		}
	}

	/** O "blip" das letras aparecendo: cada tipo de NPC tem uma voz (mais aguda ou mais grave). */
	static void typing(String kind, boolean child) {
		long now = System.currentTimeMillis();

		if (now - lastTyping < 55) {
			return;
		}

		lastTyping = now;
		float pitch = switch (kind) {
			case "piglin" -> 0.72F;
			case "pillager" -> 0.82F;
			case "shulker" -> 0.6F;
			case "witch" -> 1.18F;
			default -> 1.0F;
		};

		if (child) {
			pitch *= 1.35F;
		}

		pitch += (float) ((Math.random() - 0.5) * 0.12);
		play(ModSounds.TYPING, pitch, 0.32F);
	}
}

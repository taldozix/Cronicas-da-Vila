package br.com.cronicasdavila.quest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import br.com.cronicasdavila.CronicasDaVila;

/**
 * Confere, ao iniciar o servidor, se todos os ids usados nas missões existem no jogo.
 * Assim um erro de digitação aparece no log em vez de virar uma missão impossível.
 */
public final class QuestValidator {
	private QuestValidator() {
	}

	public static void validate() {
		int problems = 0;

		for (Quest quest : QuestRegistry.all()) {
			if (quest.objective == null || quest.objective.type == null) {
				CronicasDaVila.LOGGER.warn("[Missão {}] sem objetivo!", quest.id);
				problems++;
				continue;
			}

			for (String target : quest.objective.targets) {
				if (target.startsWith("#")) {
					continue;
				}

				Identifier id = Identifier.tryParse(target);
				boolean ok = id != null && switch (quest.objective.type) {
					case "kill" -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
					case "deliver", "craft" -> BuiltInRegistries.ITEM.containsKey(id);
					case "mine" -> BuiltInRegistries.BLOCK.containsKey(id);
					case "stat" -> BuiltInRegistries.CUSTOM_STAT.containsKey(id);
					default -> false;
				};

				if (!ok) {
					CronicasDaVila.LOGGER.warn("[Missão {}] alvo desconhecido: {} ({})", quest.id, target, quest.objective.type);
					problems++;
				}
			}

			for (QuestReward reward : quest.rewards) {
				Identifier id = Identifier.tryParse(reward.item);

				if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
					CronicasDaVila.LOGGER.warn("[Missão {}] recompensa desconhecida: {}", quest.id, reward.item);
					problems++;
				}
			}
		}

		if (problems == 0) {
			CronicasDaVila.LOGGER.info("Crônicas da Vila: todas as {} missões foram validadas.", QuestRegistry.all().size());
		} else {
			CronicasDaVila.LOGGER.warn("Crônicas da Vila: {} problema(s) encontrados nas missões.", problems);
		}
	}
}

package br.com.cronicasdavila.village;

import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import br.com.cronicasdavila.CronicasDaVila;
import br.com.cronicasdavila.quest.QuestRegistry;
import br.com.cronicasdavila.rank.Professions;

/**
 * Compatibilidade com mods que adicionam profissões de aldeão (More Villagers: Re-employed e afins).
 * Ao abrir o mundo, toda profissão que não é do jogo ganha nome em português, falas e missões.
 */
public final class ModdedProfessions {
	/** Nomes conhecidos: {singular, plural}. As outras usam o id "embelezado". */
	private static final Map<String, String[]> KNOWN = Map.ofEntries(
			Map.entry("oceanographer", new String[] {"Oceanógrafo", "oceanógrafos"}),
			Map.entry("netherian", new String[] {"Netherólogo", "netherólogos"}),
			Map.entry("netherologist", new String[] {"Netherólogo", "netherólogos"}),
			Map.entry("woodworker", new String[] {"Silvicultor", "silvicultores"}),
			Map.entry("forester", new String[] {"Silvicultor", "silvicultores"}),
			Map.entry("carpenter", new String[] {"Carpinteiro", "carpinteiros"}),
			Map.entry("florist", new String[] {"Florista", "floristas"}),
			Map.entry("hunter", new String[] {"Caçador", "caçadores"}),
			Map.entry("miner", new String[] {"Minerador", "mineradores"}),
			Map.entry("engineer", new String[] {"Engenheiro", "engenheiros"}),
			Map.entry("enderian", new String[] {"Enderólogo", "enderólogos"}),
			Map.entry("iceman", new String[] {"Geleiro", "geleiros"}),
			Map.entry("explorer", new String[] {"Explorador", "exploradores"}),
			Map.entry("beekeeper", new String[] {"Apicultor", "apicultores"}),
			Map.entry("apiarist", new String[] {"Apicultor", "apicultores"}),
			Map.entry("baker", new String[] {"Padeiro", "padeiros"}),
			Map.entry("chef", new String[] {"Cozinheiro", "cozinheiros"}),
			Map.entry("cook", new String[] {"Cozinheiro", "cozinheiros"}),
			Map.entry("gardener", new String[] {"Jardineiro", "jardineiros"}),
			Map.entry("blacksmith", new String[] {"Ferreiro", "ferreiros"}),
			Map.entry("alchemist", new String[] {"Alquimista", "alquimistas"}),
			Map.entry("guard", new String[] {"Guarda", "guardas"}),
			Map.entry("banker", new String[] {"Banqueiro", "banqueiros"}),
			Map.entry("jeweler", new String[] {"Joalheiro", "joalheiros"}),
			Map.entry("tailor", new String[] {"Alfaiate", "alfaiates"}),
			Map.entry("brewer", new String[] {"Cervejeiro", "cervejeiros"}),
			Map.entry("glassblower", new String[] {"Vidreiro", "vidreiros"}),
			Map.entry("musician", new String[] {"Músico", "músicos"}),
			Map.entry("lumberjack", new String[] {"Lenhador", "lenhadores"}),
			Map.entry("potter", new String[] {"Oleiro", "oleiros"}),
			Map.entry("smith", new String[] {"Ferreiro", "ferreiros"})
	);

	private ModdedProfessions() {
	}

	public static void register() {
		int professions = 0;
		int quests = 0;

		for (Identifier id : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
			String key = id.getPath();

			if (id.getNamespace().equals("minecraft") || Professions.givesQuests(key)
					|| key.equals(Professions.UNEMPLOYED) || key.equals(Professions.NITWIT)) {
				continue;
			}

			String[] names = KNOWN.getOrDefault(key, new String[] {pretty(key), pretty(key).toLowerCase() + "s"});
			Professions.registerModded(key, names[0], names[1]);
			quests += QuestRegistry.registerModded(key);
			professions++;
		}

		if (professions > 0) {
			CronicasDaVila.LOGGER.info("Crônicas da Vila: {} profissão(ões) de outros mods reconhecidas, com {} missões.", professions, quests);
		}
	}

	/** "stone_carver" → "Stone Carver". */
	private static String pretty(String key) {
		StringBuilder result = new StringBuilder();

		for (String part : key.split("_")) {
			if (part.isEmpty()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}

		return result.toString();
	}
}

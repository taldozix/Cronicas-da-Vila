package br.com.cronicasdavila.rank;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Todas as "profissões" que dão missões. Os aldeões usam o id da profissão do vanilla
 * (farmer, librarian...). Piglins, saqueadores, bruxas e shulkers usam chaves especiais.
 */
public final class Professions {
	public static final String PIGLIN = "piglin";
	public static final String PILLAGER = "pillager";
	public static final String SHULKER = "shulker";
	public static final String WITCH = "witch";
	public static final String PIGLIN_BRUTE = "piglin_brute";
	public static final String ENDERMAN = "enderman";
	public static final String NITWIT = "nitwit";
	public static final String UNEMPLOYED = "none";
	public static final String CHILD = "child";
	public static final String WANDERING_TRADER = "wandering_trader";

	/**
	 * O "lado" de quem dá a missão.
	 * <ul>
	 *     <li>GOOD: aldeões — sobem o nível de Amador a Mestre e contam para o Herói da Vila.</li>
	 *     <li>VILLAIN: saqueadores, piglins e bruxas — sobem a Vilania e contam para o Vilão da História.</li>
	 *     <li>NEUTRAL: shulkers — missões difíceis, só recompensas.</li>
	 * </ul>
	 */
	public enum Faction {
		GOOD, VILLAIN, NEUTRAL
	}

	private static final Map<String, String> NAMES = new LinkedHashMap<>();

	static {
		NAMES.put("farmer", "Fazendeiro");
		NAMES.put("fisherman", "Pescador");
		NAMES.put("shepherd", "Pastor");
		NAMES.put("butcher", "Açougueiro");
		NAMES.put("leatherworker", "Coureiro");
		NAMES.put("fletcher", "Flecheiro");
		NAMES.put("mason", "Pedreiro");
		NAMES.put("toolsmith", "Ferramenteiro");
		NAMES.put("weaponsmith", "Ferreiro de Armas");
		NAMES.put("armorer", "Armeiro");
		NAMES.put("cartographer", "Cartógrafo");
		NAMES.put("librarian", "Bibliotecário");
		NAMES.put("cleric", "Clérigo");
		NAMES.put(PIGLIN, "Piglin");
		NAMES.put(PILLAGER, "Saqueador");
		NAMES.put(WITCH, "Bruxa");
		NAMES.put(PIGLIN_BRUTE, "Piglin Bruto");
		NAMES.put(SHULKER, "Shulker");
	}

	/** Profissões de aldeão que contam para o efeito "Herói da Vila". */
	public static final List<String> VILLAGER_PROFESSIONS = List.of(
			"farmer", "fisherman", "shepherd", "butcher", "leatherworker", "fletcher", "mason",
			"toolsmith", "weaponsmith", "armorer", "cartographer", "librarian", "cleric"
	);

	/**
	 * Profissões de trabalho pesado: esses aldeões só pegam no batente uma vez a cada 3 dias.
	 * Nos outros dias estão de folga e não passam missões (mas recebem as entregas).
	 */
	public static final java.util.Set<String> HEAVY_PROFESSIONS = java.util.Set.of(
			"farmer", "fisherman", "butcher", "mason", "toolsmith", "weaponsmith", "armorer"
	);
	public static final int HEAVY_WORK_EVERY_DAYS = 3;

	/** Inimigos que dão missões e contam para o efeito "Vilão da História". */
	public static final List<String> VILLAIN_FACTIONS = List.of(PILLAGER, PIGLIN, WITCH, PIGLIN_BRUTE);

	/** Nível de Vilania (Renegado) para o Piglin Bruto aceitar conversa. */
	public static final int BRUTE_MIN_VILLAIN_RANK = 3;

	/**
	 * Missões para virar uma lenda, dos dois lados: o mínimo para dominar as 13 profissões de aldeão
	 * (2 missões em cada um dos 4 primeiros níveis + 1 de Mestre = 9 por profissão). O Vilão da História
	 * precisa do mesmo número de missões de vilão.
	 */
	public static final int LEGEND_QUESTS = 13 * 9;

	/** Todas as chaves que têm arquivo de missões. */
	public static final List<String> QUEST_GIVERS;

	static {
		QUEST_GIVERS = new java.util.ArrayList<>(VILLAGER_PROFESSIONS);
		QUEST_GIVERS.add(PIGLIN);
		QUEST_GIVERS.add(PILLAGER);
		QUEST_GIVERS.add(WITCH);
		QUEST_GIVERS.add(PIGLIN_BRUTE);
		QUEST_GIVERS.add(SHULKER);
	}

	/** Profissões de outros mods (ex.: More Villagers), registradas ao abrir o mundo. */
	public static final java.util.Set<String> MODDED = new java.util.LinkedHashSet<>();
	private static final Map<String, String> MODDED_PLURALS = new java.util.HashMap<>();

	private Professions() {
	}

	/** Passa a tratar uma profissão de outro mod como as do jogo: falas, missões e afinidade. */
	public static void registerModded(String key, String name, String plural) {
		if (NAMES.containsKey(key)) {
			return;
		}

		NAMES.put(key, name);
		MODDED.add(key);
		MODDED_PLURALS.put(key, plural);
		QUEST_GIVERS.add(key);
	}

	/** Plural das profissões de outros mods ("marceneiros"); null se não for uma delas. */
	public static String moddedPlural(String key) {
		return MODDED_PLURALS.get(key);
	}

	public static boolean givesQuests(String key) {
		return NAMES.containsKey(key);
	}

	public static Faction faction(String key) {
		return switch (key) {
			case PILLAGER, PIGLIN, WITCH, PIGLIN_BRUTE -> Faction.VILLAIN;
			case SHULKER -> Faction.NEUTRAL;
			default -> Faction.GOOD;
		};
	}

	public static String displayName(String key) {
		return switch (key) {
			case NITWIT -> "Bobão da Vila";
			case UNEMPLOYED -> "Desempregado";
			case CHILD -> "Criança";
			case WANDERING_TRADER -> "Vendedor Ambulante";
			case ENDERMAN -> "Enderman";
			default -> NAMES.getOrDefault(key, "Aldeão");
		};
	}

	/** Nome do nível de afinidade: inimigos usam os nomes de Vilania. */
	public static String levelName(String key, int level) {
		return faction(key) == Faction.VILLAIN ? VillainRank.ofTier(level).displayName() : Rank.ofTier(level).displayName();
	}

	public static boolean isNether(String key) {
		return PIGLIN.equals(key) || PIGLIN_BRUTE.equals(key);
	}

	public static boolean isSwamp(String key) {
		return WITCH.equals(key);
	}

	public static boolean isEnd(String key) {
		return SHULKER.equals(key);
	}
}

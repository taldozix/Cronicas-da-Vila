package br.com.cronicasdavila.rank;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Todas as "profissões" que dão missões. Os aldeões usam o id da profissão do vanilla
 * (farmer, librarian...) e os piglins usam a chave especial {@code piglin}.
 */
public final class Professions {
	public static final String PIGLIN = "piglin";
	public static final String NITWIT = "nitwit";
	public static final String UNEMPLOYED = "none";
	public static final String CHILD = "child";

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
	}

	/** Profissões de aldeão que contam para o efeito "Herói da Vila". */
	public static final List<String> VILLAGER_PROFESSIONS = List.of(
			"farmer", "fisherman", "shepherd", "butcher", "leatherworker", "fletcher", "mason",
			"toolsmith", "weaponsmith", "armorer", "cartographer", "librarian", "cleric"
	);

	/** Todas as chaves que têm arquivo de missões. */
	public static final List<String> QUEST_GIVERS;

	static {
		QUEST_GIVERS = new java.util.ArrayList<>(VILLAGER_PROFESSIONS);
		QUEST_GIVERS.add(PIGLIN);
	}

	private Professions() {
	}

	public static boolean givesQuests(String key) {
		return NAMES.containsKey(key);
	}

	public static String displayName(String key) {
		return switch (key) {
			case NITWIT -> "Bobão da Vila";
			case UNEMPLOYED -> "Desempregado";
			case CHILD -> "Criança";
			default -> NAMES.getOrDefault(key, "Aldeão");
		};
	}

	public static boolean isNether(String key) {
		return PIGLIN.equals(key);
	}
}

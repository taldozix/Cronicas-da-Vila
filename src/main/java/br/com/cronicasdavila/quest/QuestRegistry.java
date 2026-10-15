package br.com.cronicasdavila.quest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import br.com.cronicasdavila.CronicasDaVila;
import br.com.cronicasdavila.rank.Professions;

/**
 * Carrega todas as missões e falas dos arquivos JSON que vêm dentro do mod.
 */
public final class QuestRegistry {
	private static final Gson GSON = new Gson();
	private static final Map<String, Quest> BY_ID = new LinkedHashMap<>();
	private static final Map<String, List<Quest>> BY_PROFESSION = new HashMap<>();
	private static final Map<String, NpcDialogue> DIALOGUES = new HashMap<>();
	private static final Map<String, NpcDialogue> BIOMES = new HashMap<>();
	/** Tipos de vila do jogo, cada um com falas próprias. */
	public static final List<String> VILLAGE_BIOMES = List.of("plains", "desert", "savanna", "snow", "taiga", "jungle", "swamp");
	private static NpcDialogue common = new NpcDialogue();

	private QuestRegistry() {
	}

	public static void load() {
		BY_ID.clear();
		BY_PROFESSION.clear();
		DIALOGUES.clear();

		common = readJson("/cronicasdavila/npcs/_common.json", NpcDialogue.class);

		if (common == null) {
			common = new NpcDialogue();
		}

		for (String key : Professions.QUEST_GIVERS) {
			JsonObject root = readJson("/cronicasdavila/quests/" + key + ".json", JsonObject.class);

			if (root != null && root.has("quests")) {
				List<Quest> quests = GSON.fromJson(root.get("quests"), new TypeToken<List<Quest>>() { }.getType());

				for (Quest quest : quests) {
					quest.profession = key;
					BY_ID.put(quest.id, quest);
					BY_PROFESSION.computeIfAbsent(key, k -> new ArrayList<>()).add(quest);
				}
			}
		}

		List<String> dialogueKeys = new ArrayList<>(Professions.QUEST_GIVERS);
		dialogueKeys.add(Professions.NITWIT);
		dialogueKeys.add(Professions.UNEMPLOYED);
		dialogueKeys.add(Professions.CHILD);

		for (String key : dialogueKeys) {
			NpcDialogue dialogue = readJson("/cronicasdavila/npcs/" + key + ".json", NpcDialogue.class);

			if (dialogue == null) {
				dialogue = new NpcDialogue();
			}

			dialogue.fillFrom(common);
			DIALOGUES.put(key, dialogue);
		}

		BIOMES.clear();

		for (String biome : VILLAGE_BIOMES) {
			NpcDialogue dialogue = readJson("/cronicasdavila/npcs/biomes/" + biome + ".json", NpcDialogue.class);

			if (dialogue != null) {
				BIOMES.put(biome, dialogue);
			}
		}

		CronicasDaVila.LOGGER.info("Crônicas da Vila: {} missões carregadas para {} tipos de NPC.", BY_ID.size(), BY_PROFESSION.size());
	}

	private static <T> T readJson(String path, Class<T> type) {
		try (InputStream stream = QuestRegistry.class.getResourceAsStream(path)) {
			if (stream == null) {
				CronicasDaVila.LOGGER.warn("Arquivo não encontrado: {}", path);
				return null;
			}

			return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), type);
		} catch (Exception e) {
			CronicasDaVila.LOGGER.error("Erro ao ler {}", path, e);
			return null;
		}
	}

	public static Quest get(String id) {
		return BY_ID.get(id);
	}

	public static Collection<Quest> all() {
		return Collections.unmodifiableCollection(BY_ID.values());
	}

	public static List<Quest> forProfession(String profession) {
		return BY_PROFESSION.getOrDefault(profession, List.of());
	}

	public static List<Quest> forProfessionAndTier(String profession, int tier) {
		List<Quest> result = new ArrayList<>();

		for (Quest quest : forProfession(profession)) {
			if (quest.tier == tier) {
				result.add(quest);
			}
		}

		return result;
	}

	/** Falas da profissão misturadas com as do bioma da vila (se houver). */
	public static NpcDialogue dialogue(String key, String biome) {
		NpcDialogue base = dialogue(key);
		NpcDialogue biomeLines = biome == null ? null : BIOMES.get(biome);
		return biomeLines == null ? base : base.mergedWith(biomeLines);
	}

	public static NpcDialogue dialogue(String key) {
		NpcDialogue dialogue = DIALOGUES.get(key);
		return dialogue != null ? dialogue : common;
	}
}

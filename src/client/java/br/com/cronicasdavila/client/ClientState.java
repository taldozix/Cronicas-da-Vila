package br.com.cronicasdavila.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Último estado recebido do servidor (nível, karma e missões) — usado pelo Diário e pelo HUD.
 */
public final class ClientState {
	private static JsonObject data = new JsonObject();

	private ClientState() {
	}

	public static void update(String json) {
		try {
			data = JsonParser.parseString(json).getAsJsonObject();
		} catch (Exception e) {
			data = new JsonObject();
		}
	}

	public static void clear() {
		data = new JsonObject();
	}

	public static boolean hasData() {
		return data.has("rank");
	}

	public static JsonObject root() {
		return data;
	}

	public static int getInt(String key, int fallback) {
		return data.has(key) ? data.get(key).getAsInt() : fallback;
	}

	public static String getString(String key, String fallback) {
		return data.has(key) ? data.get(key).getAsString() : fallback;
	}

	public static boolean getBool(String key) {
		return data.has(key) && data.get(key).getAsBoolean();
	}

	public static List<JsonObject> quests() {
		return list("quests");
	}

	public static List<JsonObject> professions() {
		return list("professions");
	}

	private static List<JsonObject> list(String key) {
		List<JsonObject> result = new ArrayList<>();

		if (data.has(key) && data.get(key).isJsonArray()) {
			JsonArray array = data.getAsJsonArray(key);

			for (JsonElement element : array) {
				result.add(element.getAsJsonObject());
			}
		}

		return result;
	}

	public static JsonObject trackedQuest() {
		String tracked = getString("tracked", "");
		List<JsonObject> quests = quests();

		for (JsonObject quest : quests) {
			if (quest.get("prof").getAsString().equals(tracked)) {
				return quest;
			}
		}

		return null;
	}

	public static String str(JsonObject object, String key, String fallback) {
		return object.has(key) ? object.get(key).getAsString() : fallback;
	}

	public static int num(JsonObject object, String key, int fallback) {
		return object.has(key) ? object.get(key).getAsInt() : fallback;
	}

	public static List<String> strings(JsonObject object, String key) {
		List<String> result = new ArrayList<>();

		if (object.has(key) && object.get(key).isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray(key)) {
				result.add(element.getAsString());
			}
		}

		return result;
	}
}

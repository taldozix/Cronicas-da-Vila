package br.com.cronicasdavila.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;

import br.com.cronicasdavila.CronicasDaVila;

/**
 * Opções do mundo (o "mod menu" da aba Opções). Ficam em {@code config/cronicasdavila.json}
 * e só o dono do mundo ou um operador pode mudar.
 */
public final class ModSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Os valores salvos. Os nomes dos campos são as chaves usadas pela tela de opções. */
	public static final class Values {
		public int questXp = 100;
		public int questRewards = 100;
		public int luckyBonus = 30;
		public boolean questVariation = true;
		public boolean variedRewards = false;
		public int karmaRate = 100;
		public int bargainBonus = 0;
		public int bargainMinAffinity = 3;
		public boolean heavyDayOff = true;
		public int heavyEveryDays = 3;
		public boolean theft = true;
		public boolean loreOncePerDay = true;
		public boolean villainCurse = true;
	}

	/** Descrição de cada opção: chave, nome, explicação e limites (para números). */
	public record Option(String key, String name, String description, boolean toggle, int min, int max, int step, String unit) {
	}

	public static final List<Option> OPTIONS = List.of(
			new Option("questXp", "XP das missões", "Quanta experiência cada missão entrega.", false, 25, 400, 25, "%"),
			new Option("questRewards", "Recompensas das missões", "Multiplica a quantidade de itens das recompensas.", false, 25, 400, 25, "%"),
			new Option("variedRewards", "Recompensas variadas", "Troca parte das recompensas por itens surpresa de valor parecido.", true, 0, 1, 1, ""),
			new Option("luckyBonus", "Chance de bônus da sorte", "Chance de uma missão vir com um item extra.", false, 0, 100, 10, "%"),
			new Option("questVariation", "Missões sorteadas", "A quantidade pedida varia um pouco a cada missão.", true, 0, 1, 1, ""),
			new Option("karmaRate", "Intensidade do karma", "Quanto o karma sobe ou desce com cada ação.", false, 25, 300, 25, "%"),
			new Option("bargainBonus", "Chance extra na barganha", "Soma (ou tira) pontos da chance de cada barganha.", false, -30, 50, 5, "%"),
			new Option("bargainMinAffinity", "Afinidade para barganhar", "Nível de afinidade com a profissão que libera a barganha (1 Amador ... 5 Mestre).", false, 1, 5, 1, ""),
			new Option("heavyDayOff", "Folga dos aldeões", "Cada aldeão tem o seu dia de folga: sem uniforme, sem serviço e sem missões.", true, 0, 1, 1, ""),
			new Option("heavyEveryDays", "Ciclo de folga", "Cada aldeão folga um dia a cada X dias (trabalhos pesados, dois). Cada vila muda um pouco.", false, 3, 7, 1, " dias"),
			new Option("theft", "Vigiar roubo de baús", "Aldeões que te veem roubando chamam os golens.", true, 0, 1, 1, ""),
			new Option("loreOncePerDay", "Uma curiosidade por dia", "Cada NPC conta só uma história por dia.", true, 0, 1, 1, ""),
			new Option("villainCurse", "Maldição do Vilão", "O Vilão da História sente fadiga e queima longe do Nether.", true, 0, 1, 1, "")
	);

	private static Values values = new Values();

	private ModSettings() {
	}

	public static Values get() {
		return values;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("cronicasdavila.json");
	}

	public static void load() {
		Path file = file();

		if (!Files.exists(file)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Values loaded = GSON.fromJson(reader, Values.class);
			values = loaded != null ? loaded : new Values();
		} catch (Exception e) {
			CronicasDaVila.LOGGER.warn("Não consegui ler {}: {}", file, e.getMessage());
			values = new Values();
		}

		clampAll();
	}

	public static void save() {
		try {
			Files.createDirectories(file().getParent());

			try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				GSON.toJson(values, writer);
			}
		} catch (Exception e) {
			CronicasDaVila.LOGGER.warn("Não consegui salvar as opções: {}", e.getMessage());
		}
	}

	private static Option option(String key) {
		for (Option option : OPTIONS) {
			if (option.key().equals(key)) {
				return option;
			}
		}

		return null;
	}

	/** Valor atual de uma opção (0/1 para liga/desliga). */
	public static int value(String key) {
		JsonObject json = GSON.toJsonTree(values).getAsJsonObject();

		if (!json.has(key)) {
			return 0;
		}

		return json.get(key).getAsJsonPrimitive().isBoolean() ? (json.get(key).getAsBoolean() ? 1 : 0) : json.get(key).getAsInt();
	}

	/** Muda uma opção (respeitando os limites). Retorna false se a chave não existir. */
	public static boolean set(String key, int value) {
		Option option = option(key);

		if (option == null) {
			return false;
		}

		int clamped = Math.max(option.min(), Math.min(option.max(), value));
		JsonObject json = GSON.toJsonTree(values).getAsJsonObject();

		if (option.toggle()) {
			json.addProperty(key, clamped == 1);
		} else {
			json.addProperty(key, clamped);
		}

		values = GSON.fromJson(json, Values.class);
		save();
		return true;
	}

	public static void reset() {
		values = new Values();
		save();
	}

	private static void clampAll() {
		for (Option option : OPTIONS) {
			set(option.key(), value(option.key()));
		}
	}

	/** Lista de opções com o valor atual, para a tela do cliente. */
	public static JsonArray toJson() {
		JsonArray array = new JsonArray();

		for (Option option : OPTIONS) {
			JsonObject object = new JsonObject();
			object.addProperty("key", option.key());
			object.addProperty("name", option.name());
			object.addProperty("desc", option.description());
			object.addProperty("toggle", option.toggle());
			object.addProperty("min", option.min());
			object.addProperty("max", option.max());
			object.addProperty("step", option.step());
			object.addProperty("unit", option.unit());
			object.addProperty("value", value(option.key()));
			array.add(object);
		}

		return array;
	}

	public static List<String> keys() {
		List<String> keys = new ArrayList<>();

		for (Option option : OPTIONS) {
			keys.add(option.key());
		}

		return keys;
	}
}

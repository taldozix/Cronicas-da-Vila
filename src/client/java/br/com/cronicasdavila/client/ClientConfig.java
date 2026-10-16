package br.com.cronicasdavila.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Preferências de tela de cada jogador (ficam no computador dele, em config/cronicasdavila-cliente.json).
 */
public final class ClientConfig {
	public static final String FULL = "completo";
	public static final String COMPACT = "compacto";
	public static final String HIDDEN = "oculto";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class Values {
		String trackerMode = FULL;
		/** Lado do rastreador compacto: "esquerda" ou "direita". */
		String trackerSide = "esquerda";
		/** Mostrar os avisos do mod na tela. */
		boolean notices = true;
		/** Sons do mod (escrita, menu, missões, subir de nível). */
		boolean sounds = true;
	}

	private static Values values = new Values();
	/** Modo anterior e quando mudou (para o rastreador sair/entrar deslizando). */
	private static String previousMode = FULL;
	private static long changedAt = 0L;

	private ClientConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("cronicasdavila-cliente.json");
	}

	public static void load() {
		try {
			if (Files.exists(file())) {
				try (Reader reader = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
					Values loaded = GSON.fromJson(reader, Values.class);
					values = loaded != null ? loaded : new Values();
				}
			}
		} catch (Exception e) {
			values = new Values();
		}

		setTrackerMode(values.trackerMode);
		previousMode = values.trackerMode;
		changedAt = 0L;
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());

			try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				GSON.toJson(values, writer);
			}
		} catch (Exception ignored) {
			// Sem permissão para gravar? Fica só nesta sessão.
		}
	}

	public static String trackerMode() {
		return values.trackerMode;
	}

	public static void setTrackerMode(String mode) {
		String next = mode == null || !(mode.equals(FULL) || mode.equals(COMPACT) || mode.equals(HIDDEN)) ? FULL : mode;

		if (!next.equals(values.trackerMode)) {
			previousMode = values.trackerMode;
			changedAt = System.currentTimeMillis();
		}

		values.trackerMode = next;
		save();
	}

	public static String previousTrackerMode() {
		return previousMode;
	}

	public static long trackerChangedAt() {
		return changedAt;
	}

	/** Completo → Compacto → Oculto → Completo. */
	public static String cycleTrackerMode() {
		setTrackerMode(switch (values.trackerMode) {
			case FULL -> COMPACT;
			case COMPACT -> HIDDEN;
			default -> FULL;
		});
		return values.trackerMode;
	}

	public static String modeName(String mode) {
		return switch (mode) {
			case COMPACT -> "Compacto";
			case HIDDEN -> "Oculto";
			default -> "Completo";
		};
	}

	public static boolean trackerOnRight() {
		return "direita".equals(values.trackerSide);
	}

	public static void toggleTrackerSide() {
		values.trackerSide = trackerOnRight() ? "esquerda" : "direita";
		save();
	}

	public static boolean sounds() {
		return values.sounds;
	}

	public static void toggleSounds() {
		values.sounds = !values.sounds;
		save();
	}

	public static boolean notices() {
		return values.notices;
	}

	public static void toggleNotices() {
		values.notices = !values.notices;
		save();
	}
}

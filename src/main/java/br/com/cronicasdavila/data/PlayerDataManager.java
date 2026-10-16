package br.com.cronicasdavila.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import br.com.cronicasdavila.CronicasDaVila;

/**
 * Guarda os dados do RPG em um "attachment" persistente do jogador (salvo no mundo,
 * mantido após a morte). Em memória fica um cache por UUID.
 */
public final class PlayerDataManager {
	private static final Gson GSON = new GsonBuilder().create();

	public static final AttachmentType<String> RPG_DATA = AttachmentRegistry.create(
			CronicasDaVila.id("rpg_data"),
			builder -> builder.persistent(Codec.STRING).copyOnDeath()
	);

	private static final Map<UUID, PlayerData> CACHE = new HashMap<>();
	private static final Set<UUID> DIRTY = new HashSet<>();

	private PlayerDataManager() {
	}

	public static void init() {
		// Força o carregamento da classe (registra o attachment).
	}

	public static PlayerData get(ServerPlayer player) {
		return CACHE.computeIfAbsent(player.getUUID(), uuid -> {
			String json = player.getAttached(RPG_DATA);
			PlayerData data = null;

			if (json != null && !json.isEmpty()) {
				try {
					data = GSON.fromJson(json, PlayerData.class);
				} catch (Exception e) {
					CronicasDaVila.LOGGER.error("Dados do RPG corrompidos para {}, recomeçando.", player.getScoreboardName(), e);
				}
			}

			if (data == null) {
				data = new PlayerData();
			}

			if (data.tierCompletions == null || data.tierCompletions.length < 6) {
				int[] fixed = new int[6];

				if (data.tierCompletions != null) {
					System.arraycopy(data.tierCompletions, 0, fixed, 0, data.tierCompletions.length);
				}

				data.tierCompletions = fixed;
			}

			if (data.villainTierCompletions == null || data.villainTierCompletions.length < 6) {
				data.villainTierCompletions = new int[6];
			}

			if (data.villainRank < 1) {
				data.villainRank = 1;
			}

			if (data.loreDay == null) {
				data.loreDay = new java.util.HashMap<>();
			}

			if (data.bargainRefused == null) {
				data.bargainRefused = new java.util.HashMap<>();
			}

			if (data.ownChests == null) {
				data.ownChests = new java.util.HashSet<>();
			}

			return data;
		});
	}

	/** Grava os dados no jogador e agenda uma sincronização com o cliente. */
	public static void save(ServerPlayer player) {
		PlayerData data = get(player);
		player.setAttached(RPG_DATA, GSON.toJson(data));
		DIRTY.add(player.getUUID());
	}

	/** Retorna e limpa a marca de "precisa sincronizar". */
	public static boolean consumeDirty(ServerPlayer player) {
		return DIRTY.remove(player.getUUID());
	}

	public static void markDirty(ServerPlayer player) {
		DIRTY.add(player.getUUID());
	}

	/** Quando o jogador renasce, o objeto ServerPlayer muda: regrava no novo. */
	public static void onRespawn(ServerPlayer newPlayer) {
		PlayerData data = CACHE.get(newPlayer.getUUID());

		if (data != null) {
			newPlayer.setAttached(RPG_DATA, GSON.toJson(data));
		}

		DIRTY.add(newPlayer.getUUID());
	}

	public static void unload(ServerPlayer player) {
		if (CACHE.containsKey(player.getUUID())) {
			player.setAttached(RPG_DATA, GSON.toJson(CACHE.get(player.getUUID())));
		}

		CACHE.remove(player.getUUID());
		DIRTY.remove(player.getUUID());
	}

	public static void reset(ServerPlayer player) {
		CACHE.put(player.getUUID(), new PlayerData());
		save(player);
	}

	public static void clearAll() {
		CACHE.clear();
		DIRTY.clear();
	}
}

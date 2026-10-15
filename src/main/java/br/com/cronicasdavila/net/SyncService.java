package br.com.cronicasdavila.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import br.com.cronicasdavila.data.PlayerData;
import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.karma.KarmaSystem;
import br.com.cronicasdavila.karma.KarmaTier;
import br.com.cronicasdavila.quest.Quest;
import br.com.cronicasdavila.quest.QuestRegistry;
import br.com.cronicasdavila.quest.QuestReward;
import br.com.cronicasdavila.quest.QuestService;
import br.com.cronicasdavila.rank.Professions;
import br.com.cronicasdavila.rank.Rank;
import br.com.cronicasdavila.rank.VillainRank;

/**
 * Envia ao cliente o estado do jogador (para o Diário de Missões e o rastreador do HUD).
 */
public final class SyncService {
	private static final Map<UUID, String> LAST_SENT = new HashMap<>();

	private SyncService() {
	}

	public static void syncIfNeeded(ServerPlayer player) {
		if (!PlayerDataManager.consumeDirty(player)) {
			return;
		}

		String json = build(player).toString();

		if (!json.equals(LAST_SENT.get(player.getUUID()))) {
			LAST_SENT.put(player.getUUID(), json);
			ServerPlayNetworking.send(player, new ModPayloads.SyncPayload(json));
		}
	}

	public static void forget(ServerPlayer player) {
		LAST_SENT.remove(player.getUUID());
	}

	public static JsonObject build(ServerPlayer player) {
		PlayerData data = PlayerDataManager.get(player);
		Rank rank = QuestService.effectiveRank(player, data);
		Rank questRank = data.questRank();
		KarmaTier karma = KarmaSystem.tier(data);

		JsonObject root = new JsonObject();
		root.addProperty("rank", rank.tier());
		root.addProperty("rankName", rank.displayName());
		root.addProperty("rankColor", rank.color());
		root.addProperty("questRank", questRank.tier());
		root.addProperty("xpLevel", player.experienceLevel);
		root.addProperty("nextRankName", rank.next().displayName());
		root.addProperty("nextRankXp", rank.next().minXpLevel());
		root.addProperty("tierDone", data.tierCompletions[questRank.tier()]);
		root.addProperty("tierNeeded", questRank.questsToPromote());
		root.addProperty("karma", data.karma);
		root.addProperty("karmaTier", karma.displayName());
		root.addProperty("karmaColor", karma.color());
		root.addProperty("karmaDesc", karma.description());
		root.addProperty("hero", QuestService.isVillageHero(data));
		root.addProperty("mastered", QuestService.masteredCount(data));
		root.addProperty("totalCompleted", data.totalCompleted);
		root.addProperty("tracked", data.tracked == null ? "" : data.tracked);

		VillainRank villainRank = VillainRank.ofTier(data.villainRank);
		root.addProperty("villainRank", villainRank.tier());
		root.addProperty("villainName", villainRank.displayName());
		root.addProperty("villainColor", villainRank.color());
		root.addProperty("villainNextName", villainRank.next().displayName());
		root.addProperty("villainTierDone", data.villainTierCompletions[villainRank.tier()]);
		root.addProperty("villainTierNeeded", villainRank.questsToPromote());
		root.addProperty("villainQuests", data.villainQuests);
		root.addProperty("villain", QuestService.isVillain(data));
		root.addProperty("villainMastered", QuestService.villainMasteredCount(data));
		root.addProperty("villainFactions", Professions.VILLAIN_FACTIONS.size());
		root.addProperty("overworldTicks", data.overworldTicks);

		JsonArray professions = new JsonArray();

		for (String key : Professions.QUEST_GIVERS) {
			PlayerData.ProfessionProgress progress = data.profession(key);
			JsonObject object = new JsonObject();
			object.addProperty("key", key);
			object.addProperty("name", Professions.displayName(key));
			object.addProperty("level", progress.level);
			object.addProperty("levelName", Professions.levelName(key, progress.level));
			object.addProperty("faction", Professions.faction(key).name());
			object.addProperty("done", progress.doneAtLevel);
			object.addProperty("need", QuestService.QUESTS_PER_PROFESSION_LEVEL);
			object.addProperty("mastered", progress.mastered);
			object.addProperty("total", progress.totalDone);
			professions.add(object);
		}

		root.add("professions", professions);

		JsonArray quests = new JsonArray();

		for (Map.Entry<String, PlayerData.ActiveQuest> entry : data.active.entrySet()) {
			PlayerData.ActiveQuest active = entry.getValue();
			Quest quest = QuestRegistry.get(active.questId);

			if (quest == null) {
				continue;
			}

			JsonObject object = new JsonObject();
			object.addProperty("prof", entry.getKey());
			object.addProperty("profName", Professions.displayName(entry.getKey()));
			object.addProperty("id", quest.id);
			object.addProperty("title", quest.title);
			object.addProperty("tier", quest.tier);
			object.addProperty("tierName", Professions.levelName(entry.getKey(), quest.tier));
			object.addProperty("tone", quest.tone);
			object.addProperty("nether", Professions.isNether(entry.getKey()));
			object.addProperty("giver", active.giverName);
			object.addProperty("giverPos", active.giverX + ", " + active.giverY + ", " + active.giverZ);
			object.addProperty("giverDim", active.giverDimension);
			object.addProperty("objective", quest.objective.desc);
			object.addProperty("type", quest.objective.type);
			object.addProperty("progress", QuestService.progress(player, active, quest));
			object.addProperty("target", quest.objective.count);
			object.addProperty("repeat", active.repeat);

			JsonArray summary = new JsonArray();

			for (String line : quest.offer) {
				summary.add(line.replace("{player}", player.getScoreboardName()).replace("{npc}", active.giverName));
			}

			object.add("story", summary);

			JsonArray hints = new JsonArray();

			for (String line : quest.progress) {
				hints.add(line.replace("{player}", player.getScoreboardName()).replace("{npc}", active.giverName));
			}

			object.add("hints", hints);

			JsonArray rewards = new JsonArray();

			for (QuestReward reward : quest.rewards) {
				JsonObject r = new JsonObject();
				r.addProperty("item", reward.item);
				r.addProperty("count", active.repeat ? Math.max(1, reward.count / 2) : reward.count);

				if (reward.enchant != null) {
					r.addProperty("enchant", reward.enchant);
					r.addProperty("level", reward.level);
				}

				rewards.add(r);
			}

			object.add("rewards", rewards);
			object.addProperty("karmaReward", quest.karmaReward(active.repeat));
			quests.add(object);
		}

		root.add("quests", quests);
		return root;
	}
}

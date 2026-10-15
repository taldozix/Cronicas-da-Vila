package br.com.cronicasdavila.quest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import br.com.cronicasdavila.compat.VanillaCompat;
import br.com.cronicasdavila.data.PlayerData;
import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.rank.Professions;
import br.com.cronicasdavila.rank.Rank;
import br.com.cronicasdavila.util.Text;

/**
 * Regras das missões: qual missão oferecer, progresso, entrega e progressão de níveis.
 */
public final class QuestService {
	/** Missões que precisam ser concluídas em um nível de profissão para subir para o próximo. */
	public static final int QUESTS_PER_PROFESSION_LEVEL = 2;

	private QuestService() {
	}

	// ---------------------------------------------------------------------------------------------
	// Níveis
	// ---------------------------------------------------------------------------------------------

	/** Nível efetivo = o maior entre o nível por missões (permanente) e o nível pela XP atual. */
	public static Rank effectiveRank(ServerPlayer player, PlayerData data) {
		Rank byXp = Rank.fromXpLevel(player.experienceLevel);
		Rank byQuests = data.questRank();
		return byXp.tier() > byQuests.tier() ? byXp : byQuests;
	}

	/** Nível das missões que essa profissão vai oferecer agora. */
	public static int offeredTier(ServerPlayer player, PlayerData data, String profession) {
		int professionLevel = data.profession(profession).level;
		return Math.min(professionLevel, effectiveRank(player, data).tier());
	}

	/** Escolhe a próxima missão: primeiro as inéditas, depois repetições. */
	public static Offer pickQuest(ServerPlayer player, PlayerData data, String profession) {
		int tier = offeredTier(player, data, profession);
		List<Quest> candidates = QuestRegistry.forProfessionAndTier(profession, tier);

		if (candidates.isEmpty()) {
			return null;
		}

		List<Quest> fresh = new ArrayList<>();

		for (Quest quest : candidates) {
			if (!data.completed.contains(quest.id)) {
				fresh.add(quest);
			}
		}

		if (!fresh.isEmpty()) {
			// Oferece em ordem, para a história de cada profissão fazer sentido.
			return new Offer(fresh.get(0), false);
		}

		Quest repeat = candidates.get(player.getRandom().nextInt(candidates.size()));
		return new Offer(repeat, true);
	}

	public record Offer(Quest quest, boolean repeat) {
	}

	/** O jogador está "travado" pelo nível geral (a profissão já liberou mais, mas ele ainda não tem nível)? */
	public static boolean blockedByRank(ServerPlayer player, PlayerData data, String profession) {
		return data.profession(profession).level > effectiveRank(player, data).tier();
	}

	// ---------------------------------------------------------------------------------------------
	// Aceitar e progresso
	// ---------------------------------------------------------------------------------------------

	public static PlayerData.ActiveQuest accept(ServerPlayer player, PlayerData data, Quest quest, boolean repeat, Entity giver, String giverName) {
		PlayerData.ActiveQuest active = new PlayerData.ActiveQuest();
		active.questId = quest.id;
		active.giverName = giverName;
		active.giverUuid = giver.getUUID().toString();
		active.giverX = giver.blockPosition().getX();
		active.giverY = giver.blockPosition().getY();
		active.giverZ = giver.blockPosition().getZ();
		active.giverDimension = giver.level().dimension().identifier().toString();
		active.repeat = repeat;
		active.baseline = statValue(player, quest.objective);
		data.active.put(quest.profession, active);

		if (data.tracked == null || !data.active.containsKey(data.tracked)) {
			data.tracked = quest.profession;
		}

		PlayerDataManager.save(player);
		return active;
	}

	private static int statValue(ServerPlayer player, QuestObjective objective) {
		if (objective.isStat()) {
			int sum = 0;

			for (String target : objective.targets) {
				sum += VanillaCompat.customStat(player, target);
			}

			return sum;
		}

		if (objective.isCraft()) {
			int sum = 0;

			for (String target : objective.targets) {
				Item item = item(target);

				if (item != null) {
					sum += VanillaCompat.craftedStat(player, item);
				}
			}

			return sum;
		}

		return 0;
	}

	/** Progresso atual (limitado ao alvo). */
	public static int progress(ServerPlayer player, PlayerData.ActiveQuest active, Quest quest) {
		QuestObjective objective = quest.objective;
		int value;

		if (objective.isDeliver()) {
			value = countInInventory(player, objective.targets);
		} else if (objective.isStat() || objective.isCraft()) {
			value = statValue(player, objective) - active.baseline;
		} else {
			value = active.progress;
		}

		return Math.max(0, Math.min(objective.count, value));
	}

	public static boolean isComplete(ServerPlayer player, PlayerData.ActiveQuest active, Quest quest) {
		return progress(player, active, quest) >= quest.objective.count;
	}

	public static int countInInventory(ServerPlayer player, List<String> targets) {
		int total = 0;

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);

			if (!stack.isEmpty() && matchesItem(stack, targets)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	private static void removeFromInventory(ServerPlayer player, List<String> targets, int amount) {
		int remaining = amount;

		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);

			if (!stack.isEmpty() && matchesItem(stack, targets)) {
				int take = Math.min(remaining, stack.getCount());
				stack.shrink(take);
				remaining -= take;
			}
		}

		player.getInventory().setChanged();
	}

	public static boolean matchesItem(ItemStack stack, List<String> targets) {
		for (String target : targets) {
			if (target.startsWith("#")) {
				Identifier id = Identifier.tryParse(target.substring(1));

				if (id != null && stack.is(TagKey.create(Registries.ITEM, id))) {
					return true;
				}
			} else {
				Item item = item(target);

				if (item != null && stack.is(item)) {
					return true;
				}
			}
		}

		return false;
	}

	public static boolean matchesBlock(BlockState state, List<String> targets) {
		for (String target : targets) {
			if (target.startsWith("#")) {
				Identifier id = Identifier.tryParse(target.substring(1));

				if (id != null && state.is(TagKey.create(Registries.BLOCK, id))) {
					return true;
				}
			} else {
				Identifier id = Identifier.tryParse(target);
				Block block = state.getBlock();

				if (id != null && BuiltInRegistries.BLOCK.getKey(block).equals(id)) {
					return true;
				}
			}
		}

		return false;
	}

	public static Item item(String id) {
		Identifier identifier = Identifier.tryParse(id);

		if (identifier == null) {
			return null;
		}

		return BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
	}

	// ---------------------------------------------------------------------------------------------
	// Eventos de progresso
	// ---------------------------------------------------------------------------------------------

	public static void onKill(ServerPlayer player, LivingEntity victim) {
		PlayerData data = PlayerDataManager.get(player);

		if (data.active.isEmpty()) {
			return;
		}

		String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
		boolean changed = false;

		for (PlayerData.ActiveQuest active : data.active.values()) {
			Quest quest = QuestRegistry.get(active.questId);

			if (quest != null && quest.objective.isKill() && quest.objective.targets.contains(typeId)
					&& active.progress < quest.objective.count) {
				active.progress++;
				changed = true;

				if (active.progress < quest.objective.count) {
					player.sendOverlayMessage(Text.parse("<y>" + quest.title + "</y>: " + active.progress + "/" + quest.objective.count));
				}
			}
		}

		if (changed) {
			PlayerDataManager.save(player);
		}
	}

	public static void onBlockBreak(ServerPlayer player, BlockState state) {
		PlayerData data = PlayerDataManager.get(player);

		if (data.active.isEmpty()) {
			return;
		}

		boolean changed = false;

		for (PlayerData.ActiveQuest active : data.active.values()) {
			Quest quest = QuestRegistry.get(active.questId);

			if (quest != null && quest.objective.isMine() && active.progress < quest.objective.count
					&& matchesBlock(state, quest.objective.targets)) {
				active.progress++;
				changed = true;

				if (active.progress % 8 == 0 || active.progress >= quest.objective.count) {
					player.sendOverlayMessage(Text.parse("<y>" + quest.title + "</y>: " + active.progress + "/" + quest.objective.count));
				}
			}
		}

		if (changed) {
			PlayerDataManager.save(player);
		}
	}

	/** Chamado a cada segundo: avisa quando uma missão fica pronta para entrega. */
	public static void tickPlayer(ServerPlayer player) {
		PlayerData data = PlayerDataManager.get(player);
		boolean changed = false;

		for (PlayerData.ActiveQuest active : data.active.values()) {
			Quest quest = QuestRegistry.get(active.questId);

			if (quest == null) {
				continue;
			}

			boolean complete = isComplete(player, active, quest);

			if (complete && !active.notified) {
				active.notified = true;
				changed = true;
				player.sendSystemMessage(Text.parse("<g>✔ Objetivo cumprido:</g> <y>" + quest.title + "</y>. Volte e fale com <a>"
						+ active.giverName + "</a> (" + Professions.displayName(quest.profession) + ") para receber a recompensa."));
				player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
			} else if (!complete && active.notified && quest.objective.isDeliver()) {
				// Itens saíram do inventário: volta a mostrar como pendente.
				active.notified = false;
				changed = true;
			}
		}

		if (changed) {
			PlayerDataManager.save(player);
		} else {
			// Missões de entrega/estatística mudam sem eventos: mantém o diário atualizado.
			PlayerDataManager.markDirty(player);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Entrega e recompensas
	// ---------------------------------------------------------------------------------------------

	/**
	 * Entrega a missão. Retorna as linhas extras para mostrar no diálogo (recompensas, subidas de nível)
	 * ou {@code null} se ainda não dá para entregar.
	 */
	public static List<String> turnIn(ServerPlayer player, PlayerData data, String profession) {
		PlayerData.ActiveQuest active = data.active.get(profession);

		if (active == null) {
			return null;
		}

		Quest quest = QuestRegistry.get(active.questId);

		if (quest == null) {
			data.active.remove(profession);
			PlayerDataManager.save(player);
			return null;
		}

		if (!isComplete(player, active, quest)) {
			return null;
		}

		if (quest.objective.isDeliver()) {
			removeFromInventory(player, quest.objective.targets, quest.objective.count);
		}

		List<String> lines = new ArrayList<>();
		boolean repeat = active.repeat;
		data.active.remove(profession);

		if (profession.equals(data.tracked)) {
			data.tracked = data.active.isEmpty() ? null : data.active.keySet().iterator().next();
		}

		// Recompensas (metade nas repetições, mínimo 1)
		StringBuilder rewardText = new StringBuilder();

		for (QuestReward reward : quest.rewards) {
			int count = repeat ? Math.max(1, reward.count / 2) : reward.count;
			giveReward(player, reward, count);

			if (!rewardText.isEmpty()) {
				rewardText.append(", ");
			}

			rewardText.append("<o>").append(count).append("x {item:").append(reward.item).append("}");

			if (reward.enchant != null) {
				rewardText.append(" ({ench:").append(reward.enchant).append("} ").append(reward.level).append(")");
			}

			rewardText.append("</o>");
		}

		int karma = repeat ? Math.max(1, quest.karmaReward() / 2) : quest.karmaReward();
		int xp = repeat ? quest.xpReward() / 2 : quest.xpReward();
		data.addKarma(karma);

		if (xp > 0) {
			player.giveExperiencePoints(xp);
		}

		lines.add("<g>Recompensa recebida:</g> " + rewardText + ". <a>+" + karma + " de karma</a>.");

		// Progresso geral
		data.totalCompleted++;
		boolean firstTime = data.completed.add(quest.id);

		if (!repeat || firstTime) {
			Rank questRank = data.questRank();

			if (quest.tier == questRank.tier() && questRank != Rank.MESTRE) {
				data.tierCompletions[quest.tier]++;

				if (data.tierCompletions[quest.tier] >= questRank.questsToPromote()) {
					data.questRank = questRank.next().tier();
					lines.add("<o>★ Você subiu de nível pelas missões! Agora você é " + questRank.next().displayName()
							+ ".</o> Esse nível é seu para sempre, mesmo se perder XP.");
				}
			}

			// Progresso com a profissão
			PlayerData.ProfessionProgress prof = data.profession(profession);
			prof.totalDone++;

			if (quest.tier == 5 && !prof.mastered) {
				prof.mastered = true;
				lines.add("<o>★ Você é MESTRE entre os " + plural(profession) + "!</o>");
			} else if (quest.tier == prof.level && prof.level < 5) {
				prof.doneAtLevel++;

				if (prof.doneAtLevel >= QUESTS_PER_PROFESSION_LEVEL) {
					prof.level++;
					prof.doneAtLevel = 0;
					lines.add("<g>▲ Sua afinidade com os " + plural(profession) + " subiu para <o>"
							+ Rank.ofTier(prof.level).displayName() + "</o>!</g> Missões mais difíceis (e melhores) foram liberadas.");
				}
			}
		}

		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
		PlayerDataManager.save(player);
		return lines;
	}

	public static void giveReward(ServerPlayer player, QuestReward reward, int count) {
		Item item = item(reward.item);

		if (item == null) {
			return;
		}

		int remaining = count;

		while (remaining > 0) {
			ItemStack stack = new ItemStack(item);
			int amount = Math.min(remaining, stack.getMaxStackSize());
			stack.setCount(amount);
			remaining -= amount;

			if (reward.enchant != null) {
				Holder<Enchantment> enchantment = VanillaCompat.enchantment((ServerLevel) player.level(), reward.enchant);

				if (enchantment != null) {
					stack.enchant(enchantment, reward.level);
				}
			}

			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
	}

	public static void abandon(ServerPlayer player, PlayerData data, String profession) {
		if (data.active.remove(profession) != null) {
			data.addKarma(-5);

			if (profession.equals(data.tracked)) {
				data.tracked = data.active.isEmpty() ? null : data.active.keySet().iterator().next();
			}

			PlayerDataManager.save(player);
		}
	}

	public static String plural(String profession) {
		return switch (profession) {
			case "farmer" -> "fazendeiros";
			case "fisherman" -> "pescadores";
			case "shepherd" -> "pastores";
			case "butcher" -> "açougueiros";
			case "leatherworker" -> "coureiros";
			case "fletcher" -> "flecheiros";
			case "mason" -> "pedreiros";
			case "toolsmith" -> "ferramenteiros";
			case "weaponsmith" -> "ferreiros de armas";
			case "armorer" -> "armeiros";
			case "cartographer" -> "cartógrafos";
			case "librarian" -> "bibliotecários";
			case "cleric" -> "clérigos";
			case "piglin" -> "piglins";
			default -> "aldeões";
		};
	}

	/** Todas as profissões de aldeão dominadas? (efeito Herói da Vila) */
	public static boolean isVillageHero(PlayerData data) {
		for (String profession : Professions.VILLAGER_PROFESSIONS) {
			PlayerData.ProfessionProgress progress = data.professions.get(profession);

			if (progress == null || !progress.mastered) {
				return false;
			}
		}

		return true;
	}

	public static int masteredCount(PlayerData data) {
		int count = 0;

		for (String profession : Professions.VILLAGER_PROFESSIONS) {
			PlayerData.ProfessionProgress progress = data.professions.get(profession);

			if (progress != null && progress.mastered) {
				count++;
			}
		}

		return count;
	}
}

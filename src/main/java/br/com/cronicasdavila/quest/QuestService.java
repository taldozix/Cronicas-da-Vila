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
import net.minecraft.world.entity.item.ItemEntity;
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
import br.com.cronicasdavila.rank.VillainRank;
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

	/**
	 * O "teto" de nível para as missões de cada lado:
	 * aldeões usam o nível do herói; saqueadores, piglins e bruxas usam a Vilania; shulkers aceitam o maior dos dois.
	 */
	public static int factionRank(ServerPlayer player, PlayerData data, String profession) {
		return switch (Professions.faction(profession)) {
			case GOOD -> effectiveRank(player, data).tier();
			case VILLAIN -> data.villainRank;
			case NEUTRAL -> Math.max(effectiveRank(player, data).tier(), data.villainRank);
		};
	}

	/** Nível das missões que essa profissão vai oferecer agora. */
	public static int offeredTier(ServerPlayer player, PlayerData data, String profession) {
		int professionLevel = data.profession(profession).level;
		return Math.min(professionLevel, factionRank(player, data, profession));
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
			// Sorteia entre as que o jogador ainda não fez, para cada conversa ser uma surpresa.
			return roll(player, fresh.get(player.getRandom().nextInt(fresh.size())), false);
		}

		// Acabaram as novas deste nível? Antes de repetir, oferece alguma nova de um nível abaixo.
		for (int lower = tier - 1; lower >= 1; lower--) {
			List<Quest> older = new ArrayList<>();

			for (Quest quest : QuestRegistry.forProfessionAndTier(profession, lower)) {
				if (!data.completed.contains(quest.id)) {
					older.add(quest);
				}
			}

			if (!older.isEmpty()) {
				return roll(player, older.get(player.getRandom().nextInt(older.size())), false);
			}
		}

		Quest repeat = candidates.get(player.getRandom().nextInt(candidates.size()));
		return roll(player, repeat, true);
	}

	/**
	 * Uma missão oferecida, já "sorteada" para este jogador: a quantidade pedida e as recompensas
	 * variam um pouco a cada vez, e às vezes vem um bônus da sorte. Assim cada jogada é diferente.
	 */
	public record Offer(Quest quest, boolean repeat, int target, List<QuestReward> rewards) {
	}


	/** Bônus da sorte por nível da missão: {item, quantidade}. */
	private static final String[][][] BONUS_POOL = {
			{},
			{{"minecraft:experience_bottle", "4"}, {"minecraft:golden_carrot", "6"}, {"minecraft:emerald", "4"}, {"minecraft:ender_pearl", "2"}},
			{{"minecraft:experience_bottle", "8"}, {"minecraft:diamond", "1"}, {"minecraft:golden_apple", "1"}, {"minecraft:emerald", "8"}},
			{{"minecraft:experience_bottle", "12"}, {"minecraft:diamond", "2"}, {"minecraft:golden_apple", "1"}, {"minecraft:name_tag", "1"}},
			{{"minecraft:experience_bottle", "16"}, {"minecraft:diamond", "4"}, {"minecraft:golden_apple", "2"}, {"minecraft:netherite_scrap", "1"}},
			{{"minecraft:experience_bottle", "24"}, {"minecraft:diamond", "6"}, {"minecraft:netherite_scrap", "2"}, {"minecraft:enchanted_golden_apple", "1"}}
	};

	/** Sorteia a quantidade (75% a 135% da padrão), ajusta as recompensas na mesma proporção e talvez dá um bônus. */
	public static Offer roll(ServerPlayer player, Quest quest, boolean repeat) {
		QuestObjective objective = quest.objective;
		int unit = objective.displayUnit();
		int base = objective.count / unit;
		int target = objective.count;
		double ratio = 1.0;

		// Chefes e pedidos pequenos (1 a 3) continuam exatos.
		br.com.cronicasdavila.config.ModSettings.Values settings = br.com.cronicasdavila.config.ModSettings.get();

		if (base >= 4 && settings.questVariation) {
			double factor = 0.75 + player.getRandom().nextDouble() * 0.6;
			int rolled = Math.max(4, (int) Math.round(base * factor));

			if (rolled >= 1000) {
				rolled = Math.round(rolled / 100F) * 100;
			} else if (rolled >= 100) {
				rolled = Math.round(rolled / 10F) * 10;
			}

			target = rolled * unit;
			ratio = rolled / (double) base;
		}

		List<QuestReward> rewards = new ArrayList<>();

		for (QuestReward reward : quest.rewards) {
			int count = reward.count;

			// Só as recompensas "a granel" acompanham a quantidade; itens únicos (livros, totens...) ficam iguais.
			if (count >= 3) {
				count = Math.max(1, (int) Math.round(count * (0.4 + 0.6 * ratio)));
			}

			// Opção "Recompensas das missões" (multiplica o que é empilhável).
			if (count >= 2 || settings.questRewards >= 200) {
				count = Math.max(1, (int) Math.round(count * settings.questRewards / 100.0));
			}

			QuestReward rolled = reward.copyWithCount(count);

			// Opção "Recompensas variadas": às vezes troca por um item surpresa de valor parecido.
			if (settings.variedRewards && reward.enchant == null && count >= 3 && player.getRandom().nextDouble() < 0.35) {
				QuestReward surprise = surprise(player, rolled);

				if (surprise != null) {
					rolled = surprise;
					count = rolled.count;
				}
			}

			if (repeat) {
				count = Math.max(1, count / 2);
			}

			rewards.add(rolled.copyWithCount(count));
		}

		int tier = Math.max(1, Math.min(5, quest.tier));

		if (!repeat && player.getRandom().nextInt(100) < settings.luckyBonus) {
			String[] bonus = BONUS_POOL[tier][player.getRandom().nextInt(BONUS_POOL[tier].length)];
			QuestReward extra = new QuestReward(bonus[0], Integer.parseInt(bonus[1]));
			extra.bonus = true;
			rewards.add(extra);
		}

		return new Offer(quest, repeat, target, rewards);
	}

	/** Itens que podem aparecer no lugar de uma recompensa (opção "Recompensas variadas"). */
	private static final String[] SURPRISES = {
			"minecraft:emerald", "minecraft:gold_ingot", "minecraft:iron_ingot", "minecraft:experience_bottle", "minecraft:ender_pearl",
			"minecraft:diamond", "minecraft:golden_apple", "minecraft:blaze_rod", "minecraft:amethyst_shard", "minecraft:lapis_lazuli"
	};

	private static QuestReward surprise(ServerPlayer player, QuestReward original) {
		Item originalItem = item(original.item);

		if (originalItem == null) {
			return null;
		}

		double total = br.com.cronicasdavila.trade.BargainMath.value(new ItemStack(originalItem, original.count));

		for (int tries = 0; tries < 6; tries++) {
			String id = SURPRISES[player.getRandom().nextInt(SURPRISES.length)];
			Item item = item(id);

			if (item == null || id.equals(original.item)) {
				continue;
			}

			double each = br.com.cronicasdavila.trade.BargainMath.value(new ItemStack(item));
			int count = (int) Math.round(total / Math.max(0.01, each));

			if (count >= 1 && count <= 64) {
				return new QuestReward(id, count);
			}
		}

		return null;
	}

	/** Ícone (id de item) da missão para o rastreador do HUD: o item, o bloco, o ovo do mob ou algo que lembre o objetivo. */
	public static String icon(Quest quest) {
		QuestObjective objective = quest.objective;

		if (objective.isStat()) {
			String stat = objective.targets.isEmpty() ? "" : objective.targets.get(0);
			return switch (stat.replace("minecraft:", "")) {
				case "aviate_one_cm" -> "minecraft:elytra";
				case "boat_one_cm" -> "minecraft:oak_boat";
				case "strider_one_cm" -> "minecraft:warped_fungus_on_a_stick";
				case "raid_trigger" -> "minecraft:ominous_bottle";
				case "raid_win" -> "minecraft:bell";
				case "interact_with_brewingstand" -> "minecraft:brewing_stand";
				case "animals_bred" -> "minecraft:wheat";
				case "fish_caught" -> "minecraft:fishing_rod";
				case "enchant_item" -> "minecraft:enchanting_table";
				case "target_hit" -> "minecraft:target";
				default -> "minecraft:clock";
			};
		}

		for (String target : objective.targets) {
			String candidate;

			if (target.startsWith("#")) {
				candidate = switch (target.substring(1).replace("minecraft:", "")) {
					case "crops" -> "minecraft:wheat";
					case "wooden_doors" -> "minecraft:oak_door";
					case "beds" -> "minecraft:red_bed";
					case "logs" -> "minecraft:oak_log";
					case "wool" -> "minecraft:white_wool";
					case "flowers", "small_flowers" -> "minecraft:poppy";
					default -> null;
				};
			} else if (objective.isKill()) {
				candidate = target + "_spawn_egg";
			} else {
				candidate = target;
			}

			if (candidate != null && item(candidate) != null) {
				return candidate;
			}
		}

		return objective.isKill() ? "minecraft:iron_sword" : "minecraft:chest";
	}

	/** Quantidade que este jogador precisa cumprir. */
	public static int target(PlayerData.ActiveQuest active, Quest quest) {
		return active != null && active.target > 0 ? active.target : quest.objective.count;
	}

	/** Recompensas que este jogador vai receber (as sorteadas, ou as padrão em missões antigas). */
	public static List<QuestReward> rewards(PlayerData.ActiveQuest active, Quest quest) {
		if (active != null && active.rewards != null && !active.rewards.isEmpty()) {
			return active.rewards;
		}

		List<QuestReward> rewards = new ArrayList<>();

		for (QuestReward reward : quest.rewards) {
			rewards.add(reward.copyWithCount(active != null && active.repeat ? Math.max(1, reward.count / 2) : reward.count));
		}

		return rewards;
	}

	/** Troca o número da missão no texto ("traga 32 frutas") pela quantidade sorteada. */
	public static String withTarget(String text, Quest quest, int target) {
		int unit = quest.objective.displayUnit();
		int original = quest.objective.count / unit;
		int rolled = target / unit;

		if (text == null || original == rolled) {
			return text;
		}

		String dotted = String.format(java.util.Locale.GERMANY, "%,d", original);
		String newDotted = String.format(java.util.Locale.GERMANY, "%,d", rolled);
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?<![\\d.])" + java.util.regex.Pattern.quote(dotted) + "(?![\\d.,]\\d|\\d)").matcher(text);

		if (matcher.find()) {
			return text.substring(0, matcher.start()) + newDotted + text.substring(matcher.end());
		}

		matcher = java.util.regex.Pattern.compile("(?<![\\d.])" + original + "(?!\\d)").matcher(text);
		return matcher.find() ? text.substring(0, matcher.start()) + rolled + text.substring(matcher.end()) : text;
	}

	/** Descrição do objetivo com a quantidade deste jogador. */
	public static String objectiveDesc(PlayerData.ActiveQuest active, Quest quest) {
		return withTarget(quest.objective.desc, quest, target(active, quest));
	}

	/** Texto da lista de recompensas: "<o>3x Diamante</o>, <o>1x Maçã</o> e <a>bônus</a>...". */
	public static String rewardList(List<QuestReward> rewards) {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < rewards.size(); i++) {
			QuestReward reward = rewards.get(i);

			if (i > 0) {
				builder.append(i == rewards.size() - 1 ? " e " : ", ");
			}

			builder.append(reward.bonus ? "<a>" : "<o>").append(reward.count).append("x {item:").append(reward.item).append("}");

			if (reward.enchant != null) {
				builder.append(" de {ench:").append(reward.enchant).append("} ").append(reward.level);
			}

			builder.append(reward.bonus ? " (bônus da sorte)</a>" : "</o>");
		}

		return builder.toString();
	}

	/** O jogador está "travado" pelo nível geral (a profissão já liberou mais, mas ele ainda não tem nível)? */
	public static boolean blockedByRank(ServerPlayer player, PlayerData data, String profession) {
		return data.profession(profession).level > factionRank(player, data, profession);
	}

	// ---------------------------------------------------------------------------------------------
	// Aceitar e progresso
	// ---------------------------------------------------------------------------------------------

	public static PlayerData.ActiveQuest accept(ServerPlayer player, PlayerData data, Offer offer, Entity giver, String giverName) {
		Quest quest = offer.quest();
		boolean repeat = offer.repeat();
		PlayerData.ActiveQuest active = new PlayerData.ActiveQuest();
		active.target = offer.target();
		active.rewards = new ArrayList<>(offer.rewards());
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

		return Math.max(0, Math.min(target(active, quest), value));
	}

	public static boolean isComplete(ServerPlayer player, PlayerData.ActiveQuest active, Quest quest) {
		return progress(player, active, quest) >= target(active, quest);
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
					&& active.progress < target(active, quest)) {
				active.progress++;
				changed = true;

				if (active.progress < target(active, quest)) {
					player.sendOverlayMessage(Text.parse("<y>" + quest.title + "</y>: " + active.progress + "/" + target(active, quest)));
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

			if (quest != null && quest.objective.isMine() && active.progress < target(active, quest)
					&& matchesBlock(state, quest.objective.targets)) {
				active.progress++;
				changed = true;

				if (active.progress % 8 == 0 || active.progress >= target(active, quest)) {
					player.sendOverlayMessage(Text.parse("<y>" + quest.title + "</y>: " + active.progress + "/" + target(active, quest)));
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
				br.com.cronicasdavila.net.Notify.send(player, ("<g>✔ Objetivo cumprido:</g> <y>" + quest.title + "</y>. Volte e fale com <a>"
						+ active.giverName + "</a> (" + Professions.displayName(quest.profession) + ") para receber a recompensa."));
				br.com.cronicasdavila.net.Notify.sound(player, "quest.ready");
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
			removeFromInventory(player, quest.objective.targets, target(active, quest));
		}

		List<String> lines = new ArrayList<>();
		// Comemorações na tela (subir de nível, afinidade, Mestre...): título, subtítulo e cor.
		List<String[]> celebrations = new ArrayList<>();
		boolean repeat = active.repeat;
		data.active.remove(profession);

		if (profession.equals(data.tracked)) {
			data.tracked = data.active.isEmpty() ? null : data.active.keySet().iterator().next();
		}

		// Recompensas sorteadas para este jogador (já com a metade nas repetições)
		List<QuestReward> rewards = rewards(active, quest);

		for (QuestReward reward : rewards) {
			giveReward(player, reward, reward.count);
		}

		String rewardText = rewardList(rewards);

		int karma = (int) Math.round(quest.karmaReward(repeat) * br.com.cronicasdavila.config.ModSettings.get().karmaRate / 100.0);
		int xp = xpFor(quest, repeat);
		data.addKarma(karma);

		if (xp > 0) {
			player.giveExperiencePoints(xp);
		}

		lines.add("<g>Recompensa recebida:</g> " + rewardText + "." + karmaText(karma));

		// Progresso geral
		data.totalCompleted++;
		boolean firstTime = data.completed.add(quest.id);

		Professions.Faction faction = Professions.faction(profession);

		if (faction == Professions.Faction.VILLAIN) {
			data.villainQuests++;
		}

		if (!repeat || firstTime) {
			if (faction == Professions.Faction.GOOD) {
				Rank questRank = data.questRank();

				if (quest.tier == questRank.tier() && questRank != Rank.MESTRE) {
					data.tierCompletions[quest.tier]++;

					if (data.tierCompletions[quest.tier] >= questRank.questsToPromote()) {
						data.questRank = questRank.next().tier();
						// A comemoração já sai aqui; o aviso de "novo nível" pela XP não precisa repetir.
						data.lastAnnouncedRank = Math.max(data.lastAnnouncedRank, effectiveRank(player, data).tier());
						lines.add("<o>★ Você subiu de nível pelas missões! Agora você é " + questRank.next().displayName()
								+ ".</o> Esse nível é seu para sempre, mesmo se perder XP.");
						celebrations.add(new String[] {"SUBIU DE NÍVEL!", "Agora você é " + questRank.next().displayName(), String.valueOf(0xFFD36B)});
					}
				}
			} else if (faction == Professions.Faction.VILLAIN) {
				VillainRank villainRank = VillainRank.ofTier(data.villainRank);

				if (quest.tier == villainRank.tier() && villainRank != VillainRank.ARQUIVILAO) {
					data.villainTierCompletions[quest.tier]++;

					if (data.villainTierCompletions[quest.tier] >= villainRank.questsToPromote()) {
						data.villainRank = villainRank.next().tier();
						lines.add("<r>☠ Sua Vilania cresceu! Agora você é <b>" + villainRank.next().displayName()
								+ "</b>.</r> <d>Os inimigos das vilas confiam missões piores a você.</d>");
						celebrations.add(new String[] {"VILANIA AUMENTOU!", "Agora você é " + villainRank.next().displayName(), String.valueOf(0xE05A5A)});
					}
				}
			}

			// Progresso com a profissão
			PlayerData.ProfessionProgress prof = data.profession(profession);
			prof.totalDone++;

			if (quest.tier == 5 && !prof.mastered) {
				prof.mastered = true;
				lines.add(faction == Professions.Faction.VILLAIN
						? "<r>☠ " + capitalize(withArticle(profession)) + " te reconhecem como <b>Arquivilão</b>!</r>"
						: "<o>★ Você é MESTRE entre " + withArticle(profession) + "!</o>");
				celebrations.add(faction == Professions.Faction.VILLAIN
						? new String[] {"ARQUIVILÃO!", capitalize(withArticle(profession)) + " te temem", String.valueOf(0xE05A5A)}
						: new String[] {"MESTRE!", "Você domina a arte " + (profession.equals(Professions.WITCH) ? "das " : "dos ") + plural(profession), String.valueOf(0xFFAA00)});
			} else if (quest.tier == prof.level && prof.level < 5) {
				prof.doneAtLevel++;

				if (prof.doneAtLevel >= QUESTS_PER_PROFESSION_LEVEL) {
					prof.level++;
					prof.doneAtLevel = 0;
					lines.add("<g>▲ Sua afinidade com " + withArticle(profession) + " subiu para <o>"
							+ Professions.levelName(profession, prof.level) + "</o>!</g> Missões mais difíceis (e melhores) foram liberadas.");
					celebrations.add(new String[] {"AFINIDADE!", capitalize(plural(profession)) + ": " + Professions.levelName(profession, prof.level),
							String.valueOf(faction == Professions.Faction.VILLAIN ? 0xE05A5A : 0x5EC4D1)});
				}
			}
		}

		// Som da missão concluída e, se subiu algum nível, a comemoração na tela (com a fanfarra).
		br.com.cronicasdavila.net.Notify.sound(player, "quest.complete");

		for (String[] celebration : celebrations) {
			br.com.cronicasdavila.net.Notify.celebrate(player, celebration[0], celebration[1], Integer.parseInt(celebration[2]));
		}

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
				ServerLevel level = (ServerLevel) player.level();
				level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack));
			}
		}
	}

	/** XP que a missão entrega (já com a opção "XP das missões"). */
	public static int xpFor(Quest quest, boolean repeat) {
		int base = repeat ? quest.xpReward() / 2 : quest.xpReward();
		return (int) Math.round(base * br.com.cronicasdavila.config.ModSettings.get().questXp / 100.0);
	}

	/** Karma ao desistir: largar um serviço de vilão é um ato de bondade (+5); largar um aldeão na mão, não (-5). */
	public static int abandonKarma(String profession) {
		return Professions.faction(profession) == Professions.Faction.VILLAIN ? 5 : -5;
	}

	public static void abandon(ServerPlayer player, PlayerData data, String profession) {
		if (data.active.remove(profession) != null) {
			data.addKarma(abandonKarma(profession));
			br.com.cronicasdavila.net.Notify.sound(player, "quest.abandon");

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
			case "pillager" -> "saqueadores";
			case "shulker" -> "shulkers";
			case "witch" -> "bruxas";
			default -> "aldeões";
		};
	}

	/** "os saqueadores", "as bruxas"... */
	public static String withArticle(String profession) {
		return (Professions.WITCH.equals(profession) ? "as " : "os ") + plural(profession);
	}

	private static String capitalize(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	public static String karmaText(int karma) {
		if (karma > 0) {
			return " <a>+" + karma + " de karma</a>.";
		}

		return karma < 0 ? " <r>" + karma + " de karma</r>." : "";
	}

	/**
	 * Vilão da História: Arquivilão, dominou todos os inimigos que dão missões (saqueadores, piglins e bruxas)
	 * e tem karma negativo.
	 */
	public static boolean isVillain(PlayerData data) {
		if (data.villainPermanent) {
			return true;
		}

		if (data.villainRank < VillainRank.ARQUIVILAO.tier() || data.karma >= 0) {
			return false;
		}

		for (String faction : Professions.VILLAIN_FACTIONS) {
			PlayerData.ProfessionProgress progress = data.professions.get(faction);

			if (progress == null || !progress.mastered) {
				return false;
			}
		}

		return true;
	}

	/** Porcentagem de Vilania: quantas missões de vilão (de todas as facções inimigas) já foram feitas. */
	public static int villainPercent(PlayerData data) {
		int total = 0;
		int done = 0;

		for (String faction : Professions.VILLAIN_FACTIONS) {
			for (Quest quest : QuestRegistry.forProfession(faction)) {
				total++;

				if (data.completed.contains(quest.id)) {
					done++;
				}
			}
		}

		return total == 0 ? 0 : done * 100 / total;
	}

	/**
	 * 100% de Vilania: nível máximo, todas as missões de vilão feitas e karma no fundo do poço.
	 * Quem chega lá vira o Vilão da História para sempre. Retorna true na primeira vez.
	 */
	public static boolean checkPermanentVillain(PlayerData data) {
		if (data.villainPermanent || data.villainRank < VillainRank.ARQUIVILAO.tier() || data.karma > PlayerData.KARMA_MIN
				|| villainPercent(data) < 100) {
			return false;
		}

		data.villainPermanent = true;
		return true;
	}

	public static int villainMasteredCount(PlayerData data) {
		int count = 0;

		for (String faction : Professions.VILLAIN_FACTIONS) {
			PlayerData.ProfessionProgress progress = data.professions.get(faction);

			if (progress != null && progress.mastered) {
				count++;
			}
		}

		return count;
	}

	/** Todas as profissões de aldeão dominadas e karma não negativo? (efeito Herói da Vila) */
	public static boolean isVillageHero(PlayerData data) {
		if (data.karma < 0) {
			return false;
		}

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

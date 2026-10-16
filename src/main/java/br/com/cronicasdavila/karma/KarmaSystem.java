package br.com.cronicasdavila.karma;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import br.com.cronicasdavila.compat.VanillaCompat;
import br.com.cronicasdavila.data.PlayerData;
import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.effect.ModEffects;
import br.com.cronicasdavila.quest.QuestService;
import br.com.cronicasdavila.rank.Rank;
import br.com.cronicasdavila.util.Text;

/**
 * O sistema de karma: ganha-se ajudando os aldeões, perde-se fazendo mal a eles.
 * Karma baixo traz castigos; karma alto traz bênçãos (sem quebrar o equilíbrio).
 */
public final class KarmaSystem {
	private static final int DAILY_TRADE_CAP = 30;
	private static final int DAILY_DEFEND_CAP = 25;

	private static final Set<String> VILLAGE_ENEMIES = Set.of(
			"minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:ravager",
			"minecraft:vex", "minecraft:witch", "minecraft:illusioner"
	);

	private static final Set<String> WORKSTATIONS = Set.of(
			"minecraft:composter", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:cartography_table",
			"minecraft:brewing_stand", "minecraft:fletching_table", "minecraft:smithing_table", "minecraft:stonecutter",
			"minecraft:loom", "minecraft:grindstone", "minecraft:lectern", "minecraft:barrel", "minecraft:cauldron"
	);

	private KarmaSystem() {
	}

	public static KarmaTier tier(PlayerData data) {
		return KarmaTier.of(data.karma);
	}

	private static long day(ServerPlayer player) {
		return player.level().getGameTime() / 24000L;
	}

	public static void change(ServerPlayer player, int amount, String reason) {
		amount = (int) Math.round(amount * br.com.cronicasdavila.config.ModSettings.get().karmaRate / 100.0);

		if (amount == 0 || player.isCreative() || player.isSpectator()) {
			return;
		}

		PlayerData data = PlayerDataManager.get(player);
		KarmaTier before = tier(data);
		data.addKarma(amount);
		KarmaTier after = tier(data);
		PlayerDataManager.save(player);

		String sign = amount > 0 ? "<g>+" + amount : "<r>" + amount;
		player.sendOverlayMessage(Text.parse(sign + " karma</" + (amount > 0 ? "g" : "r") + "> <d>(" + reason + ")</d>"));

		if (before != after) {
			String color = after.ordinal() > before.ordinal() ? "g" : "r";
			br.com.cronicasdavila.net.Notify.send(player, ("<" + color + ">Seu karma mudou: agora você é <b>" + after.displayName()
					+ "</b>.</" + color + "> <d>" + after.description() + "</d>"));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Eventos
	// ---------------------------------------------------------------------------------------------

	public static void onPlayerHurt(ServerPlayer player, LivingEntity victim) {
		String type = typeId(victim);

		switch (type) {
			case "minecraft:villager" -> change(player, victim.isBaby() ? -25 : -10, "você machucou um aldeão");
			case "minecraft:iron_golem" -> change(player, -3, "você atacou um golem da vila");
			case "minecraft:wandering_trader" -> change(player, -5, "você atacou um mercador");
			default -> {
			}
		}
	}

	public static void onPlayerKill(ServerPlayer player, LivingEntity victim) {
		String type = typeId(victim);
		ServerLevel level = (ServerLevel) player.level();

		switch (type) {
			case "minecraft:villager" -> change(player, victim.isBaby() ? -250 : -150, "você matou um aldeão");
			case "minecraft:iron_golem" -> change(player, -30, "você destruiu um protetor da vila");
			case "minecraft:wandering_trader" -> change(player, -40, "você matou um mercador");
			case "minecraft:piglin" -> change(player, -10, "você matou um piglin");
			default -> {
				boolean nearVillage = VanillaCompat.isVillage(level, victim.blockPosition());
				boolean enemy = VILLAGE_ENEMIES.contains(type);

				if ((nearVillage && victim.getType().getCategory() == MobCategory.MONSTER) || (enemy && nearVillage)) {
					PlayerData data = PlayerDataManager.get(player);
					data.resetDailyIfNeeded(day(player));

					if (data.dailyDefendKarma < DAILY_DEFEND_CAP) {
						int amount = enemy ? 3 : 1;
						data.dailyDefendKarma += amount;
						change(player, amount, "você protegeu a vila");
					}
				}
			}
		}
	}

	public static void onBlockBreak(ServerPlayer player, BlockPos pos, BlockState state) {
		ServerLevel level = (ServerLevel) player.level();

		if (!VanillaCompat.isVillage(level, pos)) {
			return;
		}

		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

		if (id.equals("minecraft:bell")) {
			change(player, -40, "você arrancou o sino da vila");
		} else if (id.endsWith("_bed")) {
			change(player, -8, "você quebrou a cama de um aldeão");
		} else if (WORKSTATIONS.contains(id)) {
			change(player, -8, "você destruiu o local de trabalho de um aldeão");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Tick (a cada segundo)
	// ---------------------------------------------------------------------------------------------

	public static void tickPlayer(ServerPlayer player, int secondCounter) {
		if (player.isSpectator()) {
			return;
		}

		PlayerData data = PlayerDataManager.get(player);
		data.resetDailyIfNeeded(day(player));

		checkStatKarma(player, data);
		FactionSystem.tickSecond(player, data);
		announceRank(player, data);

		if (secondCounter % 5 == 0) {
			applyEffects(player, data);
		}

		if (!player.isCreative()) {
			applyPunishments(player, data, secondCounter);
		}
	}

	private static void checkStatKarma(ServerPlayer player, PlayerData data) {
		int trades = VanillaCompat.customStat(player, "minecraft:traded_with_villager");
		int raids = VanillaCompat.customStat(player, "minecraft:raid_win");

		if (data.lastTradeStat < 0 || data.lastRaidStat < 0) {
			data.lastTradeStat = trades;
			data.lastRaidStat = raids;
			PlayerDataManager.save(player);
			return;
		}

		if (trades > data.lastTradeStat) {
			int gained = (trades - data.lastTradeStat) * 2;
			data.lastTradeStat = trades;
			int allowed = Math.max(0, Math.min(gained, DAILY_TRADE_CAP - data.dailyTradeKarma));
			data.dailyTradeKarma += allowed;
			PlayerDataManager.save(player);

			if (allowed > 0) {
				change(player, allowed, "comércio justo com os aldeões");
			}
		}

		if (raids > data.lastRaidStat) {
			int won = raids - data.lastRaidStat;
			data.lastRaidStat = raids;
			PlayerDataManager.save(player);
			change(player, 100 * won, "você salvou a vila de uma invasão");
		}
	}

	private static void announceRank(ServerPlayer player, PlayerData data) {
		Rank rank = QuestService.effectiveRank(player, data);

		if (rank.tier() != data.lastAnnouncedRank) {
			if (rank.tier() > data.lastAnnouncedRank && data.lastAnnouncedRank >= 1) {
				br.com.cronicasdavila.net.Notify.celebrate(player, "SUBIU DE NÍVEL!", "Agora você é " + rank.displayName(), rank.color());
			}

			if (rank.tier() > data.lastAnnouncedRank) {
				br.com.cronicasdavila.net.Notify.send(player, ("<o>★ Novo nível: <b>" + rank.displayName() + "</b>!</o> Missões de nível "
						+ rank.displayName() + " foram liberadas com os aldeões e shulkers."));
			} else {
				br.com.cronicasdavila.net.Notify.send(player, ("<r>Você perdeu experiência e voltou a ser " + rank.displayName()
						+ ".</r> <d>Complete missões para garantir seu nível de forma permanente.</d>"));
			}

			data.lastAnnouncedRank = rank.tier();
			PlayerDataManager.save(player);
		}
	}

	private static void effect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		player.addEffect(new MobEffectInstance(effect, 140, amplifier, true, false, true));
	}

	/** Quem já fez serviço de vilão escolheu esse caminho: ganha os mesmos benefícios do herói, espelhados. */
	public static boolean onVillainPath(PlayerData data) {
		return data.villainQuests > 0 && data.karma < 0;
	}

	private static void applyEffects(ServerPlayer player, PlayerData data) {
		KarmaTier tier = tier(data);

		// Herói e vilão são equilibrados: os mesmos efeitos nas mesmas distâncias do zero.
		// Herói: Respeitado (+250) Sorte I, Honrado (+500) Velocidade I, Lendário (+750) Força I.
		// Vilão: -250 Sorte I, -500 Velocidade I, -750 Força I.
		int power = onVillainPath(data) ? -data.karma : data.karma;

		if (power >= 250) {
			effect(player, MobEffects.LUCK, 0);
		}

		if (power >= 500) {
			effect(player, MobEffects.SPEED, 0);
		}

		if (power >= 750) {
			effect(player, MobEffects.STRENGTH, 0);
		}

		// Azar só para quem faz mal às vilas sem estar do lado dos vilões.
		if (tier.atMost(KarmaTier.SUSPEITO) && !onVillainPath(data)) {
			effect(player, MobEffects.UNLUCK, 0);
		}

		if (QuestService.checkPermanentVillain(data)) {
			PlayerDataManager.save(player);
			br.com.cronicasdavila.net.Notify.send(player, ("<r><b>☠ 100% DE VILANIA ☠</b></r>\n<p>Todas as missões de vilão feitas, karma no fundo do poço "
					+ "e o nível máximo de Vilania. O Vilão da História agora é seu <b>para sempre</b>.</p>"));
		}

		if (QuestService.isVillageHero(data)) {
			effect(player, MobEffects.HERO_OF_THE_VILLAGE, 1);

			if (!data.heroAnnounced) {
				data.heroAnnounced = true;
				PlayerDataManager.save(player);
				br.com.cronicasdavila.net.Notify.send(player, ("<o><b>★★★ HERÓI DA VILA ★★★</b></o>\n<g>Você dominou todas as profissões dos aldeões. "
						+ "Seu nome será cantado em cada vila deste mundo.</g> <d>(Herói da Vila permanente)</d>"));
			}
		}

		if (QuestService.isVillain(data)) {
			effect(player, ModEffects.VILAO_DA_HISTORIA, 0);

			if (!data.villainAnnounced) {
				data.villainAnnounced = true;
				PlayerDataManager.save(player);
				br.com.cronicasdavila.net.Notify.send(player, ("<r><b>☠☠☠ VILÃO DA HISTÓRIA ☠☠☠</b></r>\n<p>Saqueadores, piglins e bruxas se curvam diante de você. "
						+ "Toda criatura viva foge quando você se aproxima.</p> <d>Mas o mundo dos vivos te rejeita: fadiga no mundo normal "
						+ "e, depois de um dia longe do Nether, você queima.</d>"));
			}
		}
	}

	private static void applyPunishments(ServerPlayer player, PlayerData data, int secondCounter) {
		KarmaTier tier = tier(data);

		if (!tier.atMost(KarmaTier.MAL_VISTO)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		boolean villain = QuestService.isVillain(data);

		// O Vilão da História espanta todo mundo (aura de medo): golens, endermen e animais não o enfrentam.
		if (villain) {
			curse(player, level, tier, secondCounter);
			return;
		}

		// Golens de ferro atacam (Mal-visto ou pior)
		if (secondCounter % 2 == 0) {
			List<Mob> golems = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
					mob -> typeId(mob).equals("minecraft:iron_golem") && mob.getTarget() == null);

			for (Mob golem : golems) {
				golem.setTarget(player);
			}
		}

		if (!tier.atMost(KarmaTier.ODIADO)) {
			return;
		}

		// Endermen ficam furiosos (Odiado ou pior)
		if (secondCounter % 3 == 0) {
			List<Mob> endermen = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24),
					mob -> typeId(mob).equals("minecraft:enderman") && mob.getTarget() == null);

			for (Mob enderman : endermen) {
				enderman.setTarget(player);
			}
		}

		// Animais pacíficos vêm dar cabeçadas
		AABB area = player.getBoundingBox().inflate(10);
		List<Mob> animals = level.getEntitiesOfClass(Mob.class, area,
				mob -> mob.getType().getCategory() == MobCategory.CREATURE && !mob.isNoAi() && mob.isAlive());

		for (Mob animal : animals) {
			double distance = animal.distanceTo(player);

			if (distance > 1.9) {
				animal.getNavigation().moveTo(player, 1.3);
			} else if (player.getRandom().nextFloat() < 0.35F) {
				// O dano de "ataque de criatura" já empurra o jogador para trás.
				player.hurtServer(level, level.damageSources().mobAttack(animal), 1.0F);
			}
		}

		curse(player, level, tier, secondCounter);
	}

	private static void curse(ServerPlayer player, ServerLevel level, KarmaTier tier, int secondCounter) {
		if (!tier.atMost(KarmaTier.AMALDICOADO)) {
			return;
		}

		// Raios durante tempestades (Amaldiçoado)
		if (secondCounter % 5 == 0 && level.isThundering() && level.canSeeSky(player.blockPosition())
				&& player.getRandom().nextFloat() < 0.4F) {
			VanillaCompat.strikeLightning(level, player.position());
			player.sendOverlayMessage(Text.parse("<r>Os céus não esqueceram o que você fez...</r>"));
		}

		// Fome que não passa
		if (secondCounter % 30 == 0 && player.getRandom().nextFloat() < 0.5F) {
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0));
		}
	}

	public static String typeId(LivingEntity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}
}

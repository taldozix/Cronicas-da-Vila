package br.com.cronicasdavila.karma;

import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;

import br.com.cronicasdavila.compat.VanillaCompat;
import br.com.cronicasdavila.data.PlayerData;
import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.effect.ModEffects;
import br.com.cronicasdavila.quest.QuestService;
import br.com.cronicasdavila.rank.Professions;
import br.com.cronicasdavila.util.Text;

/**
 * Alianças com os inimigos das vilas (saqueadores, shulkers) e os poderes/maldições do Vilão da História.
 */
public final class FactionSystem {
	/** Os saqueadores e seus aliados das invasões. */
	public static final Set<String> ILLAGERS = Set.of(
			"minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:illusioner",
			"minecraft:ravager", "minecraft:vex", "minecraft:witch"
	);

	/** Chefes não sentem medo de ninguém. */
	private static final Set<String> BOSSES = Set.of(
			"minecraft:wither", "minecraft:ender_dragon", "minecraft:warden", "minecraft:elder_guardian"
	);

	private static final double FEAR_RADIUS = 10.0;
	private static final long ONE_DAY = 24000L;

	private FactionSystem() {
	}

	private static String typeId(Mob mob) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
	}

	private static String held(ServerPlayer player) {
		return BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
	}

	/** Os saqueadores te deixam em paz? (garrafa ominosa na mão, missão ativa, ou já é cúmplice com karma negativo) */
	public static boolean isPillagerAlly(ServerPlayer player, PlayerData data) {
		if (QuestService.isVillain(data) || held(player).equals("minecraft:ominous_bottle")) {
			return true;
		}

		if (data.active.containsKey(Professions.PILLAGER)) {
			return true;
		}

		PlayerData.ProfessionProgress progress = data.professions.get(Professions.PILLAGER);
		return progress != null && progress.totalDone > 0 && data.karma < 0;
	}

	/** Os shulkers te deixam em paz? (fruta do coro na mão, missão ativa, ou já trabalhou para eles) */
	public static boolean isShulkerAlly(ServerPlayer player, PlayerData data) {
		if (held(player).equals("minecraft:chorus_fruit") || data.active.containsKey(Professions.SHULKER)) {
			return true;
		}

		PlayerData.ProfessionProgress progress = data.professions.get(Professions.SHULKER);
		return progress != null && progress.totalDone > 0;
	}

	/** A cada meio segundo: aliados param de atacar e, se for o Vilão, as criaturas fogem. */
	public static void tickFast(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}

		PlayerData data = PlayerDataManager.get(player);
		ServerLevel level = (ServerLevel) player.level();
		boolean pillagerAlly = isPillagerAlly(player, data);
		boolean shulkerAlly = isShulkerAlly(player, data);

		if (pillagerAlly || shulkerAlly) {
			List<Mob> attackers = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), mob -> mob.getTarget() == player);

			for (Mob mob : attackers) {
				String id = typeId(mob);
				boolean ally = (pillagerAlly && ILLAGERS.contains(id)) || (shulkerAlly && id.equals("minecraft:shulker"));

				// Se você bater neles, eles revidam normalmente.
				if (ally && !VanillaCompat.recentlyHurtBy(mob, player)) {
					VanillaCompat.forgetTarget(mob);
				}
			}
		}

		if (player.hasEffect(ModEffects.VILAO_DA_HISTORIA)) {
			fearAura(player, level);
		}
	}

	/** Aura de medo do Vilão da História: todos fogem (aldeões fogem como fogem de zumbis). */
	private static void fearAura(ServerPlayer player, ServerLevel level) {
		List<Mob> nearby = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FEAR_RADIUS),
				mob -> mob.isAlive() && !mob.isNoAi());

		for (Mob mob : nearby) {
			String id = typeId(mob);

			// Chefes não têm medo; os saqueadores respeitam o seu líder; quem apanhou revida.
			if (BOSSES.contains(id) || ILLAGERS.contains(id) || VanillaCompat.recentlyHurtBy(mob, player)) {
				continue;
			}

			if (mob.getTarget() == player) {
				VanillaCompat.forgetTarget(mob);
			}

			if (mob instanceof Villager villager) {
				VanillaCompat.villagerFlee(villager, player);
			}

			double dx = mob.getX() - player.getX();
			double dz = mob.getZ() - player.getZ();
			double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
			mob.getNavigation().moveTo(mob.getX() + dx / length * 10.0, mob.getY(), mob.getZ() + dz / length * 10.0, 1.35);
		}
	}

	/** A cada segundo: a maldição do mundo normal para o Vilão da História. */
	public static void tickSecond(ServerPlayer player, PlayerData data) {
		if (!QuestService.isVillain(data) || player.isCreative() || player.isSpectator()) {
			data.overworldTicks = 0;
			return;
		}

		if (VanillaCompat.inNether(player)) {
			if (data.overworldTicks > 0) {
				data.overworldTicks = 0;
				PlayerDataManager.save(player);
				player.sendOverlayMessage(Text.parse("<n>O Nether te acolhe de volta. O sol perdeu o poder sobre você.</n>"));
			}

			return;
		}

		if (!VanillaCompat.inOverworld(player)) {
			return; // No Fim, nada muda.
		}

		player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 0, true, false, true));
		long before = data.overworldTicks;
		data.overworldTicks += 20;

		if (crossed(before, data.overworldTicks, ONE_DAY / 2)) {
			player.sendSystemMessage(Text.parse("<r>☠ O mundo dos vivos te rejeita.</r> <d>Meio dia longe do Nether. Volte antes que o sol te queime.</d>"));
			PlayerDataManager.save(player);
		} else if (crossed(before, data.overworldTicks, ONE_DAY - 2400)) {
			player.sendSystemMessage(Text.parse("<r>☠ Sua pele começa a fumegar...</r> <d>Faltam 2 minutos para você pegar fogo. Corra para o Nether!</d>"));
		} else if (crossed(before, data.overworldTicks, ONE_DAY)) {
			player.sendSystemMessage(Text.parse("<r><b>☠ VOCÊ ESTÁ QUEIMANDO!</b></r> <d>O Vilão da História não pertence a este mundo. Volte ao Nether.</d>"));
			PlayerDataManager.save(player);
		}

		if (data.overworldTicks > ONE_DAY) {
			VanillaCompat.setOnFire(player, 60);
		}
	}

	private static boolean crossed(long before, long after, long mark) {
		return before < mark && after >= mark;
	}
}

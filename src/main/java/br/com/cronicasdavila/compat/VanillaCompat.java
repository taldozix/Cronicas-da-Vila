package br.com.cronicasdavila.compat;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Todas as chamadas ao código do Minecraft que mudam com mais frequência entre versões
 * ficam isoladas aqui. Se uma atualização do jogo quebrar a compilação, provavelmente
 * só este arquivo precisa ser ajustado.
 */
public final class VanillaCompat {
	private VanillaCompat() {
	}

	/** Id da profissão do aldeão sem o namespace (ex.: "farmer", "none", "nitwit"). */
	public static String professionKey(Villager villager) {
		return villager.getVillagerData().profession().unwrapKey()
				.map(key -> key.identifier().getPath())
				.orElse("none");
	}

	/** Tipo/bioma do aldeão (plains, desert, savanna, snow, taiga, jungle, swamp). */
	public static String villagerType(Villager villager) {
		return villager.getVillagerData().type().unwrapKey()
				.map(key -> key.identifier().getPath())
				.orElse("plains");
	}

	/** Faz o aldeão entrar em pânico e fugir, como foge de um zumbi. */
	public static void villagerFlee(Villager villager, LivingEntity threat) {
		villager.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, threat);
	}

	/** A criatura foi atacada por esse jogador nos últimos 5 segundos? */
	public static boolean recentlyHurtBy(LivingEntity mob, LivingEntity attacker) {
		return mob.getLastHurtByMob() == attacker && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100;
	}

	public static boolean inOverworld(ServerPlayer player) {
		return player.level().dimension() == Level.OVERWORLD;
	}

	public static boolean inNether(ServerPlayer player) {
		return player.level().dimension() == Level.NETHER;
	}

	public static void setOnFire(ServerPlayer player, int ticks) {
		if (player.getRemainingFireTicks() < ticks) {
			player.setRemainingFireTicks(ticks);
		}
	}

	/** Abre a tela de trocas vanilla (com os descontos de reputação aplicados pelo jogo). */
	public static void openTrade(Villager villager, ServerPlayer player) {
		villager.mobInteract(player, InteractionHand.MAIN_HAND);
	}

	/**
	 * Adiciona "fofoca" sobre o jogador no aldeão. O próprio jogo usa isso para
	 * calcular os preços: fofoca positiva = mais barato, negativa = mais caro.
	 */
	public static void addGossip(Villager villager, UUID player, boolean positive, int amount) {
		villager.getGossips().add(player, positive ? GossipType.MINOR_POSITIVE : GossipType.MINOR_NEGATIVE, amount);
	}

	/** Dá experiência de troca ao aldeão — ele sobe de nível (e libera trocas melhores) na próxima troca. */
	public static void addVillagerXp(Villager villager, int amount) {
		villager.setVillagerXp(villager.getVillagerXp() + amount);
	}

	public static boolean isVillage(ServerLevel level, BlockPos pos) {
		return level.isVillage(pos);
	}

	/** Valor atual de uma estatística "custom" do jogo (ex.: minecraft:fish_caught). */
	public static int customStat(ServerPlayer player, String statId) {
		Identifier id = Identifier.tryParse(statId);

		if (id == null) {
			return 0;
		}

		Identifier registered = BuiltInRegistries.CUSTOM_STAT.getValue(id);

		if (registered == null) {
			return 0;
		}

		return player.getStats().getValue(Stats.CUSTOM.get(registered));
	}

	/** Quantos itens desse tipo o jogador já fabricou. */
	public static int craftedStat(ServerPlayer player, Item item) {
		return player.getStats().getValue(Stats.ITEM_CRAFTED.get(item));
	}

	public static void strikeLightning(ServerLevel level, Vec3 pos) {
		Entity bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);

		if (bolt != null) {
			bolt.snapTo(pos);
			level.addFreshEntity(bolt);
		}
	}

	public static @Nullable Holder<Enchantment> enchantment(ServerLevel level, String id) {
		Identifier identifier = Identifier.tryParse(id);

		if (identifier == null) {
			return null;
		}

		ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, identifier);
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
	}

	/** Quem deve receber o crédito pela morte (inclui mortes por queda/lava após um golpe do jogador). */
	public static @Nullable LivingEntity killCredit(LivingEntity victim) {
		return victim.getKillCredit();
	}
}

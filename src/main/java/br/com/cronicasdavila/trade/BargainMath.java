package br.com.cronicasdavila.trade;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * As contas da barganha, iguais no servidor e no cliente (para a tela mostrar a mesma chance que o servidor usa).
 * Os valores são em "esmeraldas": quanto um aldeão acha que cada item vale.
 */
public final class BargainMath {
	/** Afinidade mínima com a profissão para o aldeão aceitar barganhar (Aventureiro). */
	public static final int MIN_AFFINITY = 3;

	private static final Map<String, Double> VALUES = new HashMap<>();

	static {
		put(1.0, "emerald");
		put(9.0, "emerald_block");
		put(4.0, "diamond");
		put(36.0, "diamond_block");
		put(25.0, "netherite_ingot");
		put(6.0, "netherite_scrap", "ancient_debris");
		put(0.4, "gold_ingot");
		put(3.6, "gold_block");
		put(0.04, "gold_nugget");
		put(0.3, "iron_ingot");
		put(2.7, "iron_block");
		put(0.03, "iron_nugget");
		put(0.08, "copper_ingot");
		put(0.7, "copper_block");
		put(0.1, "lapis_lazuli", "quartz");
		put(0.9, "lapis_block");
		put(0.05, "redstone", "wheat", "string");
		put(0.45, "redstone_block");
		put(0.06, "coal", "charcoal");
		put(0.55, "coal_block");
		put(0.15, "amethyst_shard", "bread", "leather");
		put(0.5, "ender_pearl", "blaze_rod");
		put(1.0, "ender_eye", "ghast_tear");
		put(3.0, "golden_apple");
		put(30.0, "enchanted_golden_apple", "nether_star");
		put(12.0, "totem_of_undying");
		put(0.4, "experience_bottle");
		put(0.3, "book");
		put(2.5, "enchanted_book");
		put(2.0, "name_tag", "saddle");
		put(0.04, "paper");
	}

	private BargainMath() {
	}

	private static void put(double value, String... ids) {
		for (String id : ids) {
			VALUES.put("minecraft:" + id, value);
		}
	}

	/** Quanto o aldeão acha que essa pilha vale, em esmeraldas. */
	public static double value(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}

		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		Double known = VALUES.get(id);
		double each;

		if (known != null) {
			each = known;
		} else if (stack.isDamageableItem()) {
			// Ferramentas, armas e armaduras valem pelo material.
			each = id.startsWith("minecraft:netherite_") ? 25.0
					: id.startsWith("minecraft:diamond_") ? 6.0
					: id.startsWith("minecraft:iron_") || id.startsWith("minecraft:chainmail_") ? 1.0
					: id.startsWith("minecraft:golden_") ? 0.8 : 0.6;
		} else {
			each = switch (stack.getRarity()) {
				case COMMON -> 0.04;
				case UNCOMMON -> 0.5;
				case RARE -> 2.5;
				case EPIC -> 8.0;
			};
		}

		if (stack.isEnchanted()) {
			each += 1.5;
		}

		return each * stack.getCount();
	}

	/**
	 * Chance (0 a 100) de o aldeão aceitar: depende de quanto vale o que você oferece comparado ao preço,
	 * da sua afinidade com a profissão e do seu karma. Nunca é garantido (máximo 95%), nem impossível (mínimo 5%).
	 */
	public static int chance(double offered, double cost, int affinity, boolean mastered, int karma) {
		double ratio = offered / Math.max(0.05, cost);
		double chance = ratio >= 1.0 ? 0.6 + 0.3 * Math.min(1.0, ratio - 1.0) : 0.6 * Math.pow(ratio, 1.2);
		chance += (Math.min(5, affinity) - MIN_AFFINITY) * 0.06 + (mastered ? 0.04 : 0.0);
		chance += karma / 1000.0 * 0.08;
		return (int) Math.round(Math.max(0.05, Math.min(0.95, chance)) * 100);
	}
}

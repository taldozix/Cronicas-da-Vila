package br.com.cronicasdavila.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import br.com.cronicasdavila.CronicasDaVila;
import br.com.cronicasdavila.config.ModSettings;

/**
 * Itens do mod. Por enquanto só o Tradutor Ender, que não tem receita: aparece nos baús das
 * Cidades do Fim (a chance fica em Opções, de 5% a 12%).
 */
public final class ModItems {
	public static final ResourceKey<Item> ENDER_TRANSLATOR_KEY = ResourceKey.create(Registries.ITEM, CronicasDaVila.id("ender_translator"));
	public static final Item ENDER_TRANSLATOR = Registry.register(BuiltInRegistries.ITEM, ENDER_TRANSLATOR_KEY,
			new EnderTranslatorItem(new Item.Properties().setId(ENDER_TRANSLATOR_KEY).stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

	private ModItems() {
	}

	/** Força o registro dos itens durante a inicialização do mod. */
	public static void init() {
	}

	/** O jogador tem o Tradutor Ender em algum lugar do inventário? */
	public static boolean hasTranslator(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ENDER_TRANSLATOR)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Ao abrir pela primeira vez um baú de tesouro de Cidade do Fim, sorteia o Tradutor Ender.
	 * O conteúdo do baú é gerado aqui mesmo (como o jogo faria ao abrir) e o tradutor vai para um espaço vazio.
	 */
	public static void onUseBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);

		if (!(blockEntity instanceof RandomizableContainer container)
				|| !BuiltInLootTables.END_CITY_TREASURE.equals(container.getLootTable())) {
			return;
		}

		container.unpackLootTable(player);

		if (player.getRandom().nextInt(100) >= ModSettings.get().translatorChance) {
			return;
		}

		Container inventory = container;
		int size = inventory.getContainerSize();
		int start = player.getRandom().nextInt(Math.max(1, size));

		for (int i = 0; i < size; i++) {
			int slot = (start + i) % size;

			if (inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, new ItemStack(ENDER_TRANSLATOR));
				inventory.setChanged();
				return;
			}
		}
	}
}

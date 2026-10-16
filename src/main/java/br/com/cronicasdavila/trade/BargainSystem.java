package br.com.cronicasdavila.trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import br.com.cronicasdavila.compat.VanillaCompat;
import br.com.cronicasdavila.data.PlayerData;
import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.dialogue.DialogueManager;
import br.com.cronicasdavila.net.ModPayloads;
import br.com.cronicasdavila.quest.NpcDialogue;
import br.com.cronicasdavila.quest.QuestRegistry;
import br.com.cronicasdavila.rank.Professions;

/**
 * Barganha: na tela de trocas, quem tem afinidade alta com a profissão pode tentar pagar uma troca
 * com outro item no lugar do preço normal. A chance de dar certo aparece na tela antes de tentar.
 * Se o aldeão recusar, ele fica ofendido e não barganha mais com você até o dia seguinte (você não perde o item).
 */
public final class BargainSystem {
	private static final double MAX_DISTANCE = 8.0;
	private static final Map<UUID, Villager> TRADERS = new HashMap<>();

	private BargainSystem() {
	}

	/** O jogador clicou num aldeão: guarda quem é e conta ao cliente se o botão de barganha aparece. */
	public static void rememberTrader(ServerPlayer player, Villager villager) {
		TRADERS.put(player.getUUID(), villager);
		JsonObject json = context(player, villager);
		json.addProperty("type", "trade_context");
		ServerPlayNetworking.send(player, new ModPayloads.DialoguePayload(json.toString()));
	}

	public static void forget(ServerPlayer player) {
		TRADERS.remove(player.getUUID());
	}

	private static Villager trader(ServerPlayer player) {
		Villager villager = TRADERS.get(player.getUUID());

		if (villager == null || !villager.isAlive() || villager.isRemoved() || villager.level() != player.level()
				|| villager.distanceTo(player) > MAX_DISTANCE) {
			return null;
		}

		return villager;
	}

	private static boolean eligible(PlayerData data, String profession) {
		if (!Professions.VILLAGER_PROFESSIONS.contains(profession)) {
			return false;
		}

		PlayerData.ProfessionProgress progress = data.profession(profession);
		return progress.mastered || progress.level >= br.com.cronicasdavila.config.ModSettings.get().bargainMinAffinity;
	}

	private static boolean refusedToday(PlayerData data, Villager villager) {
		Long day = data.bargainRefused.get(villager.getUUID().toString());
		return day != null && day == VanillaCompat.day(villager.level());
	}

	private static JsonObject context(ServerPlayer player, Villager villager) {
		PlayerData data = PlayerDataManager.get(player);
		String profession = VanillaCompat.professionKey(villager);
		PlayerData.ProfessionProgress progress = data.profession(profession);
		JsonObject json = new JsonObject();
		json.addProperty("entity", villager.getId());
		json.addProperty("profession", profession);
		json.addProperty("professionName", Professions.displayName(profession));
		json.addProperty("level", progress.level);
		json.addProperty("levelName", Professions.levelName(profession, progress.level));
		json.addProperty("mastered", progress.mastered);
		json.addProperty("karma", data.karma);
		json.addProperty("canBargain", eligible(data, profession));
		json.addProperty("refused", refusedToday(data, villager));
		json.addProperty("bonus", br.com.cronicasdavila.config.ModSettings.get().bargainBonus);
		return json;
	}

	/** Ações vindas da tela: "bargain_open", "bargain_back" e "bargain_try:<troca>:<slot>". Retorna true se tratou. */
	public static boolean onAction(ServerPlayer player, String action) {
		if (!action.startsWith("bargain_")) {
			return false;
		}

		Villager villager = trader(player);

		if (villager == null) {
			player.sendOverlayMessage(br.com.cronicasdavila.util.Text.parse("<r>O aldeão está longe demais para negociar.</r>"));
			return true;
		}

		if (action.equals("bargain_back")) {
			VanillaCompat.openTrade(villager, player);
			return true;
		}

		PlayerData data = PlayerDataManager.get(player);
		String profession = VanillaCompat.professionKey(villager);

		if (!eligible(data, profession)) {
			player.sendOverlayMessage(br.com.cronicasdavila.util.Text.parse("<y>Para barganhar, sua afinidade com os "
					+ br.com.cronicasdavila.quest.QuestService.plural(profession) + " precisa ser pelo menos Aventureiro.</y>"));
			return true;
		}

		if (action.equals("bargain_open")) {
			send(player, villager, null, null);
			return true;
		}

		if (action.startsWith("bargain_try:")) {
			String[] parts = action.split(":");

			if (parts.length == 3) {
				try {
					attempt(player, villager, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
				} catch (NumberFormatException ignored) {
					send(player, villager, null, null);
				}
			}
		}

		return true;
	}

	private static double cost(MerchantOffer offer) {
		return BargainMath.value(offer.getCostA()) + BargainMath.value(offer.getCostB());
	}

	private static void attempt(ServerPlayer player, Villager villager, int offerIndex, int slot) {
		PlayerData data = PlayerDataManager.get(player);
		NpcDialogue lines = QuestRegistry.dialogue(VanillaCompat.professionKey(villager), VanillaCompat.villagerType(villager));
		String name = DialogueManager.npcName(villager, lines);

		if (refusedToday(data, villager)) {
			send(player, villager, say(player, name, lines, "bargain_tired", "Já disse que não. Hoje não tem mais choro."), false);
			return;
		}

		MerchantOffers offers = villager.getOffers();

		if (offerIndex < 0 || offerIndex >= offers.size() || slot < 0 || slot >= 36) {
			send(player, villager, null, null);
			return;
		}

		MerchantOffer offer = offers.get(offerIndex);
		ItemStack offered = player.getInventory().getItem(slot);

		if (offer.isOutOfStock()) {
			send(player, villager, "<y>Essa troca está esgotada. Espere o aldeão reabastecer.</y>", false);
			return;
		}

		if (offered.isEmpty()) {
			send(player, villager, "<y>Escolha um item do seu inventário para oferecer.</y>", false);
			return;
		}

		if (ItemStack.isSameItem(offered, offer.getCostA())) {
			send(player, villager, "<y>Isso é exatamente o que ele pede. Para isso, é só trocar normalmente.</y>", false);
			return;
		}

		PlayerData.ProfessionProgress progress = data.profession(VanillaCompat.professionKey(villager));
		int chance = BargainMath.chance(BargainMath.value(offered), cost(offer), progress.level, progress.mastered, data.karma,
				br.com.cronicasdavila.config.ModSettings.get().bargainBonus);
		ServerLevel level = (ServerLevel) player.level();
		boolean success = player.getRandom().nextInt(100) < chance;

		if (success) {
			player.getInventory().setItem(slot, ItemStack.EMPTY);
			ItemStack result = offer.assemble();

			if (!player.getInventory().add(result)) {
				level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), result));
			}

			offer.increaseUses();
			VanillaCompat.addVillagerXp(villager, offer.getXp());
			VanillaCompat.addGossip(villager, player.getUUID(), true, 2);
			level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
			send(player, villager, say(player, name, lines, "bargain_ok", "Fechado! Você tem lábia, hein.")
					+ " <g>(" + chance + "% — deu certo!)</g>", true);
		} else {
			data.bargainRefused.put(villager.getUUID().toString(), VanillaCompat.day(villager.level()));
			data.bargainRefused.values().removeIf(day -> day < VanillaCompat.day(villager.level()));
			PlayerDataManager.save(player);
			VanillaCompat.addGossip(villager, player.getUUID(), false, 3);
			level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
			send(player, villager, say(player, name, lines, "bargain_fail", "Tá me achando com cara de bobo? Hoje não negocio mais com você.")
					+ " <r>(" + chance + "% — não rolou. Você ficou com o seu item.)</r>", false);
		}
	}

	private static String say(ServerPlayer player, String name, NpcDialogue lines, String key, String fallback) {
		List<String> options = lines.extra(key);
		String line = options.isEmpty() ? fallback : options.get(player.getRandom().nextInt(options.size()));
		return "<y>" + name + ":</y> " + line.replace("{player}", player.getScoreboardName()).replace("{npc}", name);
	}

	/** Manda (ou atualiza) a tela de barganha com as trocas do aldeão. */
	private static void send(ServerPlayer player, Villager villager, String message, Boolean success) {
		JsonObject json = context(player, villager);
		json.addProperty("type", "bargain");
		NpcDialogue lines = QuestRegistry.dialogue(VanillaCompat.professionKey(villager), VanillaCompat.villagerType(villager));
		json.addProperty("name", DialogueManager.npcName(villager, lines));

		if (message != null) {
			json.addProperty("message", message);
		}

		if (success != null) {
			json.addProperty("success", success);
		}

		JsonArray array = new JsonArray();
		MerchantOffers offers = villager.getOffers();

		for (int i = 0; i < offers.size(); i++) {
			MerchantOffer offer = offers.get(i);
			JsonObject object = new JsonObject();
			object.addProperty("index", i);
			object.add("result", stack(offer.getResult()));
			object.add("costA", stack(offer.getCostA()));

			if (!offer.getCostB().isEmpty()) {
				object.add("costB", stack(offer.getCostB()));
			}

			object.addProperty("cost", cost(offer));
			object.addProperty("out", offer.isOutOfStock());
			array.add(object);
		}

		json.add("offers", array);
		ServerPlayNetworking.send(player, new ModPayloads.DialoguePayload(json.toString()));
	}

	private static JsonObject stack(ItemStack stack) {
		JsonObject object = new JsonObject();
		object.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		object.addProperty("count", stack.getCount());
		object.addProperty("label", stack.getHoverName().getString());
		return object;
	}
}

package br.com.cronicasdavila.client;

import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import br.com.cronicasdavila.CronicasDaVila;
import br.com.cronicasdavila.net.ModPayloads;
import br.com.cronicasdavila.util.Text;

public class CronicasDaVilaClient implements ClientModInitializer {
	public static KeyMapping journalKey;
	public static KeyMapping trackerKey;

	/** Elementos do HUD escondidos durante a conversa (visual "cinema"). */
	private static final List<Identifier> HIDDEN_DURING_DIALOGUE = List.of(
			VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
			VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
			VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
			VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE
	);

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(CronicasDaVila.id("main"));
		journalKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cronicasdavila.journal", InputConstants.Type.KEYBOARD,
				InputConstants.KEY_J, category));
		trackerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cronicasdavila.tracker", InputConstants.Type.KEYBOARD,
				InputConstants.KEY_H, category));
		ClientConfig.load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			OffDutyClient.tick(client);

			while (journalKey.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new MenuScreen(null, "inicio"));
				}
			}

			while (trackerKey.consumeClick()) {
				if (client.player != null) {
					ClientConfig.cycleTrackerMode();
					Sfx.hud();
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.DialoguePayload.TYPE, (payload, context) -> handleDialogue(payload.json()));
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.SyncPayload.TYPE, (payload, context) -> ClientState.update(payload.json()));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			ClientState.clear();
			OffDutyClient.clear();
		});

		// Botão "Missões" no inventário (a nova "aba" de missões).
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				int x = width / 2 + 88 - 62;
				int y = height / 2 - 83 - 22;
				Screens.getWidgets(screen).add(Button.builder(Component.literal("§6✦ Missões"),
						button -> client.gui.setScreen(new MenuScreen(screen, "missoes"))).bounds(x, y, 62, 20).build());

				// Atalhos para cada aba do menu, ao lado do inventário.
				String[] names = {"Início", "Missões", "Reputação", "Guia", "Opções"};
				int bx = width / 2 - 88 - 72;
				int by = height / 2 - 83;

				for (int i = 0; i < names.length; i++) {
					String tab = MenuScreen.TAB_KEYS[i];
					Screens.getWidgets(screen).add(Button.builder(Component.literal(names[i]),
							button -> client.gui.setScreen(new MenuScreen(screen, tab))).bounds(bx, by + i * 22, 68, 20).build());
				}
			}

			// Botão "Barganhar" na tela de trocas, para quem tem afinidade alta com a profissão.
			if (screen instanceof MerchantScreen && ClientState.canBargain()) {
				int x = width / 2 + 138 - 92;
				int y = height / 2 - 83 - 22;
				Screens.getWidgets(screen).add(Button.builder(Component.literal("§e⚖ Barganhar"), button -> {
					ClientPlayNetworking.send(new ModPayloads.ActionPayload("bargain_open"));

					if (client.player != null) {
						client.player.closeContainer();
					}
				}).bounds(x, y, 92, 20).build());
			}
		});

		// Esconde o HUD durante as conversas.
		for (Identifier id : HIDDEN_DURING_DIALOGUE) {
			HudElementRegistry.replaceElement(id, original -> (graphics, deltaTracker) -> {
				if (!(Minecraft.getInstance().gui.screen() instanceof DialogueScreen)) {
					original.extractRenderState(graphics, deltaTracker);
				}
			});
		}

		// Rastreador de missão (completo ou compacto) e os avisos do mod, no lugar do chat.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CronicasDaVila.id("quest_tracker"), Hud::drawTracker);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CronicasDaVila.id("notices"), Hud::drawNotices);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CronicasDaVila.id("celebration"), Hud::drawCelebration);
	}

	public static boolean isJournalKey(int key) {
		return journalKey != null && KeyMappingHelper.getBoundKeyOf(journalKey).getValue() == key;
	}

	private static void handleDialogue(String json) {
		Minecraft client = Minecraft.getInstance();
		JsonObject node = JsonParser.parseString(json).getAsJsonObject();
		String type = node.get("type").getAsString();
		Screen current = client.gui.screen();

		if (type.equals("off_duty")) {
			OffDutyClient.update(node.getAsJsonArray("ids"));
			return;
		}

		if (type.equals("trade_context")) {
			ClientState.setTradeContext(node);
			return;
		}

		if (type.equals("sound")) {
			Sfx.byId(node.get("id").getAsString());
			return;
		}

		if (type.equals("celebrate")) {
			Hud.celebrate(node.get("title").getAsString(), node.get("subtitle").getAsString(), node.get("color").getAsInt());
			return;
		}

		if (type.equals("notify")) {
			ClientState.addNotice(node.get("text").getAsString());
			return;
		}

		if (type.equals("open_menu")) {
			String tab = node.get("tab").getAsString();

			if (current instanceof MenuScreen menu) {
				menu.setTab(tab);
			} else {
				client.gui.setScreen(new MenuScreen(null, tab));
			}

			return;
		}

		if (type.equals("hud_mode")) {
			ClientConfig.setTrackerMode(node.get("mode").getAsString());
			return;
		}

		if (type.equals("bargain")) {
			ClientState.setTradeContext(node);

			if (current instanceof BargainScreen bargain) {
				bargain.update(node);
			} else {
				client.gui.setScreen(new BargainScreen(node));
			}

			return;
		}

		if (type.equals("close")) {
			if (current instanceof DialogueScreen dialogue) {
				dialogue.closedByServer = true;
				client.gui.setScreen(null);
			}

			return;
		}

		if (current instanceof DialogueScreen dialogue && dialogue.entityId() == node.get("entity").getAsInt()) {
			dialogue.setNode(node);
		} else {
			client.gui.setScreen(new DialogueScreen(node));
		}
	}
}

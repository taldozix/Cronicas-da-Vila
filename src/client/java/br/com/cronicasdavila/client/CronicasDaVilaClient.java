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

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (journalKey.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new QuestJournalScreen(null));
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.DialoguePayload.TYPE, (payload, context) -> handleDialogue(payload.json()));
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.SyncPayload.TYPE, (payload, context) -> ClientState.update(payload.json()));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientState.clear());

		// Botão "Missões" no inventário (a nova "aba" de missões).
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				int x = width / 2 + 88 - 62;
				int y = height / 2 - 83 - 22;
				Screens.getWidgets(screen).add(Button.builder(Component.literal("§6✦ Missões"),
						button -> client.gui.setScreen(new QuestJournalScreen(screen))).bounds(x, y, 62, 20).build());
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

		// Rastreador de missão no canto da tela.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CronicasDaVila.id("quest_tracker"), CronicasDaVilaClient::drawTracker);
	}

	public static boolean isJournalKey(int key) {
		return journalKey != null && KeyMappingHelper.getBoundKeyOf(journalKey).getValue() == key;
	}

	private static void handleDialogue(String json) {
		Minecraft client = Minecraft.getInstance();
		JsonObject node = JsonParser.parseString(json).getAsJsonObject();
		String type = node.get("type").getAsString();
		Screen current = client.gui.screen();

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

	private static void drawTracker(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.gui.screen() != null) {
			return;
		}

		JsonObject quest = ClientState.trackedQuest();

		if (quest == null) {
			return;
		}

		int progress = ClientState.num(quest, "progress", 0);
		int target = Math.max(1, ClientState.num(quest, "target", 1));
		boolean done = progress >= target;
		boolean danger = "danger".equals(ClientState.str(quest, "tone", ""));
		int width = 190;
		int x = 6;
		int y = 6;

		List<FormattedCharSequence> objective = client.font.split(Text.parse(done
				? "<g>✔ Volte e fale com " + ClientState.str(quest, "giver", "?") + "</g>"
				: ClientState.str(quest, "objective", "")), width - 12);
		int lines = Math.min(3, objective.size());
		int height = 30 + lines * 10;

		Ui.roundedRect(graphics, x, y, x + width, y + height, Ui.argb(150, 0x0B0B0F));
		graphics.fill(x + 1, y + 2, x + 3, y + height - 2, Ui.argb(255, done ? 0x55FF55 : (danger ? 0xFF5555 : 0xFFD24A)));
		graphics.text(client.font, "◆ " + ClientState.str(quest, "title", ""), x + 8, y + 5,
				Ui.argb(255, danger ? 0xFF8080 : 0xFFD36B), true);

		for (int i = 0; i < lines; i++) {
			graphics.text(client.font, objective.get(i), x + 8, y + 16 + i * 10, Ui.argb(255, 0xEEEEEE), true);
		}

		int barY = y + 18 + lines * 10;
		Ui.bar(graphics, x + 8, barY, width - 50, 3, progress / (float) target, Ui.argb(255, done ? 0x55FF55 : 0xFFD24A));
		graphics.text(client.font, progress + "/" + target, x + width - 38, barY - 3, Ui.argb(255, done ? 0x8CFF8C : 0xFFFFFF), false);
	}
}

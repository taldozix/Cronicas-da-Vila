package br.com.cronicasdavila.net;

import com.google.gson.JsonObject;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Avisos do mod sem poluir o chat: aparecem por alguns segundos no canto da tela.
 */
public final class Notify {
	private Notify() {
	}

	/** Mostra um aviso (texto com as marcações de cor do mod). */
	public static void send(ServerPlayer player, String markup) {
		JsonObject json = new JsonObject();
		json.addProperty("type", "notify");
		json.addProperty("text", markup);
		ServerPlayNetworking.send(player, new ModPayloads.DialoguePayload(json.toString()));
	}

	/** Abre o menu do mod numa aba ("inicio", "missoes", "reputacao", "guia", "opcoes"). */
	public static void openMenu(ServerPlayer player, String tab) {
		JsonObject json = new JsonObject();
		json.addProperty("type", "open_menu");
		json.addProperty("tab", tab);
		ServerPlayNetworking.send(player, new ModPayloads.DialoguePayload(json.toString()));
	}

	/** Muda o modo do rastreador de missões no HUD do jogador ("completo", "compacto", "oculto"). */
	public static void hudMode(ServerPlayer player, String mode) {
		JsonObject json = new JsonObject();
		json.addProperty("type", "hud_mode");
		json.addProperty("mode", mode);
		ServerPlayNetworking.send(player, new ModPayloads.DialoguePayload(json.toString()));
	}
}

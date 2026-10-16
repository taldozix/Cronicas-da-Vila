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

	/** Toca um som do mod só para esse jogador ("quest.complete", "quest.levelup"...). */
	public static void sound(ServerPlayer player, String sound) {
		JsonObject json = new JsonObject();
		json.addProperty("type", "sound");
		json.addProperty("id", sound);
		ServerPlayNetworking.send(player, new ModPayloads.DialoguePayload(json.toString()));
	}

	/** Comemoração na tela (subir de nível, afinidade, Vilania...), com o som de subir de nível. */
	public static void celebrate(ServerPlayer player, String title, String subtitle, int color) {
		JsonObject json = new JsonObject();
		json.addProperty("type", "celebrate");
		json.addProperty("title", title);
		json.addProperty("subtitle", subtitle);
		json.addProperty("color", color);
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

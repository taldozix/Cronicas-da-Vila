package br.com.cronicasdavila.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import br.com.cronicasdavila.CronicasDaVila;

/**
 * Pacotes de rede do mod. Para manter tudo simples e fácil de evoluir, o conteúdo
 * viaja como JSON dentro de uma string.
 */
public final class ModPayloads {
	private static final int MAX_JSON = 262144;

	private ModPayloads() {
	}

	/** Servidor → cliente: abrir/atualizar/fechar a caixa de diálogo. */
	public record DialoguePayload(String json) implements CustomPacketPayload {
		public static final Type<DialoguePayload> TYPE = new Type<>(CronicasDaVila.id("dialogue"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DialoguePayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.stringUtf8(MAX_JSON), DialoguePayload::json, DialoguePayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Servidor → cliente: estado do jogador (nível, karma, missões) para o diário e o HUD. */
	public record SyncPayload(String json) implements CustomPacketPayload {
		public static final Type<SyncPayload> TYPE = new Type<>(CronicasDaVila.id("sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.stringUtf8(MAX_JSON), SyncPayload::json, SyncPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Cliente → servidor: uma ação do jogador. Exemplos:
	 * "choice:accept", "close", "abandon:farmer", "track:farmer".
	 */
	public record ActionPayload(String action) implements CustomPacketPayload {
		public static final Type<ActionPayload> TYPE = new Type<>(CronicasDaVila.id("action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.stringUtf8(256), ActionPayload::action, ActionPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DialoguePayload.TYPE, DialoguePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
	}
}

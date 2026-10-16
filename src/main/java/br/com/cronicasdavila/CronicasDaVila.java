package br.com.cronicasdavila;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import br.com.cronicasdavila.command.CronicasCommand;
import br.com.cronicasdavila.compat.VanillaCompat;
import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.dialogue.DialogueManager;
import br.com.cronicasdavila.effect.ModEffects;
import br.com.cronicasdavila.karma.FactionSystem;
import br.com.cronicasdavila.karma.KarmaSystem;
import br.com.cronicasdavila.karma.TheftSystem;
import br.com.cronicasdavila.trade.BargainSystem;
import br.com.cronicasdavila.net.ModPayloads;
import br.com.cronicasdavila.net.SyncService;
import br.com.cronicasdavila.quest.QuestRegistry;
import br.com.cronicasdavila.quest.QuestService;
import br.com.cronicasdavila.quest.QuestValidator;

/**
 * Crônicas da Vila — um RPG de missões com aldeões, piglins, saqueadores, bruxas e shulkers.
 */
public class CronicasDaVila implements ModInitializer {
	public static final String MOD_ID = "cronicasdavila";
	public static final Logger LOGGER = LoggerFactory.getLogger("Crônicas da Vila");

	private static int tickCounter = 0;
	private static int secondCounter = 0;

	/** Versão do mod (a mesma do .jar). */
	public static String version() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		br.com.cronicasdavila.config.ModSettings.load();
		PlayerDataManager.init();
		DialogueManager.init();
		ModEffects.init();
		br.com.cronicasdavila.sound.ModSounds.init();
		ModPayloads.register();
		QuestRegistry.load();

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.ActionPayload.TYPE,
				(payload, context) -> DialogueManager.onAction(context.player(), payload.action()));

		// Botão direito em aldeões e nos outros NPCs abre o diálogo.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide()) {
				return DialogueManager.wantsDialogue(player, entity) ? InteractionResult.CONSUME : InteractionResult.PASS;
			}

			if (player instanceof ServerPlayer serverPlayer) {
				// Guarda com quem o jogador vai negociar (para a barganha na tela de trocas).
				if (entity instanceof net.minecraft.world.entity.npc.villager.Villager villager) {
					BargainSystem.rememberTrader(serverPlayer, villager);
				}

				return DialogueManager.onInteract(serverPlayer, entity);
			}

			return InteractionResult.PASS;
		});

		// Abrir baús da vila: vigia se o jogador está roubando.
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
				TheftSystem.onUseBlock(serverPlayer, (net.minecraft.server.level.ServerLevel) level, hitResult.getBlockPos());
			}

			return InteractionResult.PASS;
		});

		// Mortes: progresso de missões e karma.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			ServerPlayer killer = null;

			if (source.getEntity() instanceof ServerPlayer player) {
				killer = player;
			} else if (VanillaCompat.killCredit(entity) instanceof ServerPlayer player) {
				killer = player;
			}

			if (killer != null && killer != entity) {
				QuestService.onKill(killer, entity);
				KarmaSystem.onPlayerKill(killer, entity);
			}

			if (entity instanceof ServerPlayer player) {
				DialogueManager.close(player, true);
			}
		});

		// Dano: karma (bater em aldeão) e fecha o diálogo se o jogador for atacado.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (damageTaken <= 0) {
				return;
			}

			if (source.getEntity() instanceof ServerPlayer player && entity != player) {
				KarmaSystem.onPlayerHurt(player, (LivingEntity) entity);
			}

			if (entity instanceof ServerPlayer player && DialogueManager.inDialogue(player)) {
				DialogueManager.close(player, true);
			}
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				QuestService.onBlockBreak(serverPlayer, state);
				KarmaSystem.onBlockBreak(serverPlayer, pos, state);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			DialogueManager.tick(server);
			tickCounter++;

			if (tickCounter % 10 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					FactionSystem.tickFast(player);
					TheftSystem.tick(player);
				}
			}

			if (tickCounter % 20 != 0) {
				return;
			}

			secondCounter++;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				QuestService.tickPlayer(player);
				KarmaSystem.tickPlayer(player, secondCounter);
				SyncService.syncIfNeeded(player);
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> DialogueManager.onEntityLoad(entity));

		ServerPlayerEvents.JOIN.register(player -> {
			SyncService.forget(player);
			PlayerDataManager.markDirty(player);
		});

		ServerPlayerEvents.LEAVE.register(player -> {
			DialogueManager.close(player, false);
			TheftSystem.forget(player);
			BargainSystem.forget(player);
			SyncService.forget(player);
			PlayerDataManager.unload(player);
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PlayerDataManager.onRespawn(newPlayer);
			SyncService.forget(newPlayer);
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> QuestValidator.validate());

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DialogueManager.closeAll();

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PlayerDataManager.unload(player);
			}

			PlayerDataManager.clearAll();
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> CronicasCommand.register(dispatcher));

		LOGGER.info("Crônicas da Vila carregado! Que comecem as aventuras.");
	}
}

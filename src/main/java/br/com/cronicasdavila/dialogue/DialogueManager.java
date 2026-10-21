package br.com.cronicasdavila.dialogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.mojang.serialization.Codec;
import com.google.gson.JsonObject;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import br.com.cronicasdavila.compat.VanillaCompat;
import br.com.cronicasdavila.data.PlayerData;
import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.karma.FactionSystem;
import br.com.cronicasdavila.karma.KarmaSystem;
import br.com.cronicasdavila.karma.KarmaTier;
import br.com.cronicasdavila.net.ModPayloads;
import br.com.cronicasdavila.quest.NpcDialogue;
import br.com.cronicasdavila.quest.Quest;
import br.com.cronicasdavila.quest.QuestRegistry;
import br.com.cronicasdavila.quest.QuestReward;
import br.com.cronicasdavila.quest.QuestService;
import br.com.cronicasdavila.rank.Professions;
import br.com.cronicasdavila.rank.Rank;
import br.com.cronicasdavila.rank.VillainRank;

/**
 * Controla as conversas: quem fala, o que fala e o que acontece em cada escolha.
 * Toda a lógica roda no servidor; o cliente só desenha a caixa de diálogo.
 */
public final class DialogueManager {
	/** Marca o NPC congelado na conversa (guarda o valor anterior de NoAI). Fica salvo no mundo. */
	public static final AttachmentType<Boolean> FROZEN = AttachmentRegistry.createPersistent(
			br.com.cronicasdavila.CronicasDaVila.id("frozen_npc"), Codec.BOOL);
	private static final double MAX_DISTANCE = 7.0;

	private static final Map<UUID, DialogueSession> SESSIONS = new HashMap<>();

	private DialogueManager() {
	}

	/** Força o registro do attachment antes de qualquer entidade carregar. */
	public static void init() {
	}

	// ---------------------------------------------------------------------------------------------
	// Abrir / fechar
	// ---------------------------------------------------------------------------------------------

	/** Decide, dos dois lados (cliente e servidor), se o clique abre o nosso diálogo. */
	public static boolean wantsDialogue(net.minecraft.world.entity.player.Player player, Entity entity) {
		if (player.isSpectator() || player.isShiftKeyDown()) {
			return false;
		}

		String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		String held = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();

		// Etiqueta de nome e ovos de spawn continuam funcionando normalmente.
		if (held.equals("minecraft:name_tag") || held.endsWith("_spawn_egg")) {
			return false;
		}

		if (type.equals("minecraft:villager") || type.equals("minecraft:wandering_trader")
				|| type.equals("minecraft:piglin_brute") || type.equals("minecraft:enderman")) {
			return true;
		}

		if (type.equals("minecraft:piglin")) {
			// Segurando ouro = escambo normal dos piglins.
			return !held.equals("minecraft:gold_ingot");
		}

		return type.equals("minecraft:pillager") || type.equals("minecraft:shulker") || type.equals("minecraft:witch");
	}

	public static InteractionResult onInteract(ServerPlayer player, Entity entity) {
		if (!wantsDialogue(player, entity) || !(entity instanceof Mob npc) || !npc.isAlive()) {
			return InteractionResult.PASS;
		}

		if (SESSIONS.containsKey(player.getUUID())) {
			return InteractionResult.SUCCESS;
		}

		for (DialogueSession other : SESSIONS.values()) {
			if (other.npc == npc) {
				player.sendOverlayMessage(br.com.cronicasdavila.util.Text.parse("<y>Esse NPC já está conversando com outra pessoa.</y>"));
				return InteractionResult.SUCCESS;
			}
		}

		String kind = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		PlayerData playerData = PlayerDataManager.get(player);
		String biome = null;

		if (kind.equals("piglin") && npc.getTarget() == player) {
			// Piglin furioso não conversa: deixa o combate acontecer.
			return InteractionResult.PASS;
		}

		// Saqueadores, bruxas e shulkers no meio do ataque só conversam com quem eles reconhecem como aliado.
		if (kind.equals("pillager") && npc.getTarget() == player && !FactionSystem.isPillagerAlly(player, playerData)) {
			return InteractionResult.PASS;
		}

		if (kind.equals("shulker") && npc.getTarget() == player && !FactionSystem.isShulkerAlly(player, playerData)) {
			return InteractionResult.PASS;
		}

		if (kind.equals("witch") && npc.getTarget() == player && !FactionSystem.isWitchAlly(player, playerData)) {
			return InteractionResult.PASS;
		}

		String profession;

		if (kind.equals("piglin")) {
			profession = Professions.PIGLIN;
		} else if (kind.equals("pillager")) {
			profession = Professions.PILLAGER;
		} else if (kind.equals("shulker")) {
			profession = Professions.SHULKER;
		} else if (kind.equals("witch")) {
			profession = Professions.WITCH;
		} else if (kind.equals("piglin_brute")) {
			// O Piglin Bruto só conversa com quem já é Renegado (Vilania 3) ou mais.
			if (playerData.villainRank < Professions.BRUTE_MIN_VILLAIN_RANK && !QuestService.isVillain(playerData)) {
				List<String> refusals = QuestRegistry.dialogue(Professions.PIGLIN_BRUTE).extra("too_weak");
				String refusal = refusals.isEmpty()
						? "<n>HRRNGH!</n> Fraco. Volte quando for pelo menos <r>Renegado</r>."
						: refusals.get(player.getRandom().nextInt(refusals.size()));
				player.sendOverlayMessage(br.com.cronicasdavila.util.Text.parse(refusal.replace("{player}", player.getScoreboardName())
						+ " <d>(Vilania mínima: " + VillainRank.ofTier(Professions.BRUTE_MIN_VILLAIN_RANK).displayName() + ")</d>"));
				return InteractionResult.PASS;
			}

			profession = Professions.PIGLIN_BRUTE;
			kind = "brute";
		} else if (kind.equals("enderman")) {
			if (npc.getTarget() == player && !br.com.cronicasdavila.item.ModItems.hasTranslator(player)) {
				return InteractionResult.PASS;
			}

			profession = Professions.ENDERMAN;
		} else if (kind.equals("wandering_trader")) {
			if (npc instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader trader && trader.isTrading()) {
				return InteractionResult.PASS;
			}

			profession = Professions.WANDERING_TRADER;
			kind = "trader";
		} else if (npc.isBaby()) {
			kind = "villager";
			biome = npc instanceof Villager villager ? VanillaCompat.villagerType(villager) : null;
			profession = Professions.CHILD;
		} else if (npc instanceof Villager villager) {
			if (villager.isTrading()) {
				return InteractionResult.PASS;
			}

			profession = VanillaCompat.professionKey(villager);
			biome = VanillaCompat.villagerType(villager);
			kind = "villager";
		} else {
			return InteractionResult.PASS;
		}

		NpcDialogue lines = QuestRegistry.dialogue(profession, biome);
		DialogueSession session = new DialogueSession(player, npc, profession, npcName(npc, lines), kind, lines);
		session.elebin = isElebin(npc);
		session.biome = biome;
		session.translated = !kind.equals("enderman") || br.com.cronicasdavila.item.ModItems.hasTranslator(player);
		SESSIONS.put(player.getUUID(), session);
		freeze(session);
		onConversationStart(session);
		openingNode(session);
		return InteractionResult.SUCCESS;
	}

	/** Chance de um clérigo ser Elebin, o evento raro. */
	private static final int ELEBIN_CHANCE = 15;

	/** Esse clérigo é Elebin? (15% dos clérigos, sempre os mesmos.) */
	public static boolean isElebin(Mob npc) {
		if (!(npc instanceof Villager villager) || villager.isBaby() || (npc.hasCustomName() && npc.getCustomName() != null)
				|| !VanillaCompat.professionKey(villager).equals("cleric")) {
			return false;
		}

		long bits = npc.getUUID().getLeastSignificantBits() ^ 0x5DEECE66DL;
		return Math.floorMod(Long.hashCode(bits * 31 + 17), 100) < ELEBIN_CHANCE;
	}

	public static String npcName(Mob npc, NpcDialogue lines) {
		if (npc.hasCustomName() && npc.getCustomName() != null) {
			return npc.getCustomName().getString();
		}

		if (isElebin(npc)) {
			return "Elebin";
		}

		if (lines.names.isEmpty()) {
			return "Aldeão";
		}

		return lines.names.get(Math.floorMod(npc.getUUID().hashCode(), lines.names.size()));
	}

	private static void freeze(DialogueSession session) {
		Mob npc = session.npc;
		npc.getNavigation().stop();
		npc.setAttached(FROZEN, session.previousNoAi);
		npc.setNoAi(true);
		faceEachOther(session);
	}

	private static void unfreeze(DialogueSession session) {
		Mob npc = session.npc;

		if (npc.hasAttached(FROZEN)) {
			npc.removeAttached(FROZEN);
			npc.setNoAi(session.previousNoAi);
		}
	}

	/** Se o servidor caiu no meio de uma conversa, o NPC volta ao normal quando carregar. */
	public static void onEntityLoad(Entity entity) {
		if (entity instanceof Mob mob && mob.hasAttached(FROZEN)) {
			boolean inSession = false;

			for (DialogueSession session : SESSIONS.values()) {
				if (session.npc == mob) {
					inSession = true;
					break;
				}
			}

			if (!inSession) {
				Boolean previous = mob.removeAttached(FROZEN);
				mob.setNoAi(previous != null && previous);
			}
		}
	}

	private static void faceEachOther(DialogueSession session) {
		Mob npc = session.npc;
		ServerPlayer player = session.player;
		double dx = player.getX() - npc.getX();
		double dz = player.getZ() - npc.getZ();
		double dy = player.getEyeY() - npc.getEyeY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
		npc.setYRot(yaw);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		npc.setXRot(pitch);
	}

	public static void close(ServerPlayer player, boolean notifyClient) {
		DialogueSession session = SESSIONS.remove(player.getUUID());

		if (session != null) {
			unfreeze(session);
		}

		if (notifyClient) {
			JsonObject json = new JsonObject();
			json.addProperty("type", "close");
			ServerPlayNetworking.send(player, new ModPayloads.DialoguePayload(json.toString()));
		}
	}

	public static void closeAll() {
		for (DialogueSession session : new ArrayList<>(SESSIONS.values())) {
			unfreeze(session);
		}

		SESSIONS.clear();
	}

	public static boolean inDialogue(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	public static void tick(MinecraftServer server) {
		for (DialogueSession session : new ArrayList<>(SESSIONS.values())) {
			session.ticks++;
			boolean invalid = session.player.isRemoved() || !session.npc.isAlive() || session.npc.isRemoved()
					|| session.npc.level() != session.player.level()
					|| session.npc.distanceTo(session.player) > MAX_DISTANCE;

			if (invalid) {
				close(session.player, true);
				continue;
			}

			faceEachOther(session);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Ações vindas do cliente
	// ---------------------------------------------------------------------------------------------

	public static void onAction(ServerPlayer player, String action) {
		if (br.com.cronicasdavila.trade.BargainSystem.onAction(player, action)
				|| br.com.cronicasdavila.config.SettingsService.onAction(player, action)) {
			return;
		}

		if (action.equals("close")) {
			close(player, false);
			return;
		}

		if (action.startsWith("choice:")) {
			DialogueSession session = SESSIONS.get(player.getUUID());

			if (session == null) {
				close(player, true);
				return;
			}

			Runnable handler = session.handlers.get(action.substring("choice:".length()));

			if (handler != null) {
				handler.run();
			}

			return;
		}

		PlayerData data = PlayerDataManager.get(player);

		if (action.startsWith("abandon:")) {
			String profession = action.substring("abandon:".length());
			Quest quest = data.active.containsKey(profession) ? QuestRegistry.get(data.active.get(profession).questId) : null;
			QuestService.abandon(player, data, profession);

			if (quest != null) {
				int karma = QuestService.abandonKarma(profession);
				br.com.cronicasdavila.net.Notify.send(player, ("<r>Você desistiu da missão</r> <y>" + quest.title + "</y>. <d>("
						+ (karma > 0 ? "+" : "") + karma + " karma)</d>"));
			}
		} else if (action.startsWith("track:")) {
			String profession = action.substring("track:".length());

			if (data.active.containsKey(profession)) {
				data.tracked = profession;
			} else if (profession.equals("none")) {
				data.tracked = null;
			}

			PlayerDataManager.save(player);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Montagem dos "nós" do diálogo
	// ---------------------------------------------------------------------------------------------

	private record Choice(String id, String text, String style, Runnable action) {
	}

	private static String pick(DialogueSession session, List<String> options, String fallback) {
		if (options == null || options.isEmpty()) {
			return fallback;
		}

		return options.get(session.player.getRandom().nextInt(options.size()));
	}

	private static String fill(DialogueSession session, String text) {
		return text.replace("{player}", session.player.getScoreboardName())
				.replace("{npc}", session.npcName)
				.replace("{profissao}", Professions.displayName(session.profession));
	}

	private static void show(DialogueSession session, List<String> pages, List<Choice> choices, String tone) {
		session.handlers.clear();
		JsonObject json = new JsonObject();
		json.addProperty("type", "open");
		json.addProperty("entity", session.npc.getId());
		json.addProperty("speaker", session.npcName);
		json.addProperty("subtitle", subtitle(session));
		json.addProperty("tone", tone);
		json.addProperty("nether", session.piglin);
		json.addProperty("kind", session.kind);

		JsonArray pageArray = new JsonArray();

		for (String page : pages) {
			pageArray.add(session.translated || page.startsWith("<d>") ? fill(session, page) : enderGlyphs(fill(session, page)));
		}

		json.add("pages", pageArray);
		JsonArray choiceArray = new JsonArray();

		for (Choice choice : choices) {
			JsonObject object = new JsonObject();
			object.addProperty("id", choice.id());
			object.addProperty("text", fill(session, choice.text()));
			object.addProperty("style", choice.style());
			choiceArray.add(object);
			session.handlers.put(choice.id(), choice.action());
		}

		json.add("choices", choiceArray);
		ServerPlayNetworking.send(session.player, new ModPayloads.DialoguePayload(json.toString()));
	}

	/**
	 * Sem o Tradutor Ender, a fala do Enderman vira os símbolos da mesa de encantamento.
	 * Tira as cores e os acentos (a fonte antiga só tem as letras simples).
	 */
	private static String enderGlyphs(String text) {
		String plain = text.replaceAll("<[^>]*>", "");
		plain = java.text.Normalizer.normalize(plain, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return "<e>" + plain.toLowerCase(java.util.Locale.ROOT) + "</e>";
	}

	private static String subtitle(DialogueSession session) {
		String profession = Professions.displayName(session.profession);

		if (!Professions.givesQuests(session.profession)) {
			return profession;
		}

		PlayerData data = PlayerDataManager.get(session.player);
		PlayerData.ProfessionProgress progress = data.profession(session.profession);
		String affinity = progress.mastered
				? (Professions.faction(session.profession) == Professions.Faction.VILLAIN ? "Arquivilão ☠" : "Mestre ★")
				: Professions.levelName(session.profession, progress.level);
		return profession + "  ·  Sua afinidade: " + affinity;
	}

	private static void speak(DialogueSession session, SoundEvent villagerSound, SoundEvent piglinSound) {
		ServerLevel level = (ServerLevel) session.npc.level();
		boolean happy = villagerSound == SoundEvents.VILLAGER_YES || villagerSound == SoundEvents.VILLAGER_CELEBRATE;
		SoundEvent sound = switch (session.kind) {
			case "piglin" -> piglinSound;
			case "pillager" -> happy ? SoundEvents.PILLAGER_CELEBRATE : SoundEvents.PILLAGER_AMBIENT;
			case "shulker" -> happy ? SoundEvents.SHULKER_AMBIENT : SoundEvents.SHULKER_OPEN;
			case "witch" -> happy ? SoundEvents.WITCH_CELEBRATE : SoundEvents.WITCH_AMBIENT;
			case "brute" -> villagerSound == SoundEvents.VILLAGER_NO ? SoundEvents.PIGLIN_BRUTE_ANGRY : SoundEvents.PIGLIN_BRUTE_AMBIENT;
			case "enderman" -> villagerSound == SoundEvents.VILLAGER_NO ? SoundEvents.ENDERMAN_SCREAM : SoundEvents.ENDERMAN_AMBIENT;
			case "trader" -> villagerSound == SoundEvents.VILLAGER_NO ? SoundEvents.WANDERING_TRADER_NO
					: happy ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_AMBIENT;
			default -> villagerSound;
		};
		level.playSound(null, session.npc.getX(), session.npc.getY(), session.npc.getZ(), sound, SoundSource.NEUTRAL, 1.0F,
				session.npc.isBaby() ? 1.5F : 1.0F);
	}

	private static void onConversationStart(DialogueSession session) {
		speak(session, SoundEvents.VILLAGER_AMBIENT, SoundEvents.PIGLIN_AMBIENT);
		PlayerData data = PlayerDataManager.get(session.player);

		// O karma mexe nos preços através da "fofoca" do próprio jogo.
		if (session.npc instanceof Villager villager) {
			if (data.karma >= 100) {
				VanillaCompat.addGossip(villager, session.player.getUUID(), true, Math.min(10, data.karma / 100));
			} else if (data.karma <= -100) {
				VanillaCompat.addGossip(villager, session.player.getUUID(), false, Math.min(10, -data.karma / 100));
			}
		}
	}

	private static String toneFor(DialogueSession session) {
		return switch (session.kind) {
			case "piglin" -> "nether";
			case "pillager" -> "villain";
			case "shulker" -> "end";
			case "witch" -> "swamp";
			case "trader" -> "trader";
			case "brute" -> "nether";
			case "enderman" -> "end";
			default -> "neutral";
		};
	}

	private static void openingNode(DialogueSession session) {
		PlayerData data = PlayerDataManager.get(session.player);
		KarmaTier karma = KarmaSystem.tier(data);
		NpcDialogue lines = session.lines;

		if (session.npc.isSleeping()) {
			show(session, List.of(pick(session, lines.extra("sleeping"), "Zzz... só mais cinco minutinhos... Zzz...")),
					List.of(new Choice("bye", "Deixa ele dormir.", "neutral", () -> close(session.player, true))), "neutral");
			return;
		}

		if (session.piglin && !wearsGold(session.player)) {
			show(session, List.of(pick(session, lines.extra("no_gold"), "<n>HRRNF!</n> Cadê o <o>ouro</o>? Sem ouro, sem conversa!")),
					List.of(new Choice("bye", "Ops... já volto com ouro.", "neutral", () -> close(session.player, true))), "nether");
			return;
		}

		if (session.kind.equals("pillager")) {
			pillagerOpening(session, data, karma);
			return;
		}

		if (session.kind.equals("shulker")) {
			shulkerOpening(session, data);
			return;
		}

		if (session.kind.equals("witch")) {
			witchOpening(session, data, karma);
			return;
		}

		if (session.kind.equals("trader")) {
			traderOpening(session, karma);
			return;
		}

		if (session.kind.equals("brute")) {
			bruteOpening(session, data);
			return;
		}

		if (session.kind.equals("enderman")) {
			endermanOpening(session, data, karma);
			return;
		}

		if (session.kind.equals("villager") && QuestService.isVillain(data)) {
			speak(session, SoundEvents.VILLAGER_NO, SoundEvents.PIGLIN_ANGRY);
			show(session, List.of(pick(session, lines.extra("villain_seen"), "<r>É O VILÃO DA HISTÓRIA!</r> Socorro! Alguém chama o golem!")),
					List.of(new Choice("bye", "<r>Muahaha...</r>", "danger", () -> close(session.player, true))), "danger");
			return;
		}

		if (karma.atMost(KarmaTier.ODIADO)) {
			hostileNode(session);
			return;
		}

		if (session.elebin) {
			elebinOpening(session, data);
			return;
		}

		List<String> greetings;

		if (karma.atMost(KarmaTier.SUSPEITO)) {
			greetings = lines.greet_cold;
		} else if (situational(session) != null) {
			greetings = situational(session);
		} else if (karma.atLeast(KarmaTier.CONFIAVEL)) {
			greetings = lines.greet_friendly;
		} else {
			greetings = lines.greet_neutral;
		}

		mainMenu(session, pick(session, greetings, "Olá, {player}."));
	}

	/** Saqueadores: odeiam heróis, gostam de quem já fez mal às vilas. */
	private static void pillagerOpening(DialogueSession session, PlayerData data, KarmaTier karma) {
		NpcDialogue lines = session.lines;
		boolean ally = FactionSystem.isPillagerAlly(session.player, data);

		if (!ally && data.karma > -100) {
			if (karma.atLeast(KarmaTier.HONRADO)) {
				speak(session, SoundEvents.VILLAGER_NO, SoundEvents.PIGLIN_ANGRY);
				show(session, List.of(pick(session, lines.hostile, "<r>O queridinho dos narigudos?</r> Some antes que eu esqueça que estou de folga.")),
						List.of(new Choice("bye", "Tô indo...", "neutral", () -> close(session.player, true))), "villain");
				return;
			}

			show(session, List.of(pick(session, lines.extra("untrusted"),
					"Hmpf. Não confio em você. Mostre uma <p>garrafa ominosa</p> na mão ou prove que já fez mal às vilas.")),
					List.of(new Choice("bye", "Entendido.", "neutral", () -> close(session.player, true))), "villain");
			return;
		}

		List<String> greetings;

		if (data.karma >= 100) {
			greetings = lines.greet_cold;
		} else if (situational(session) != null) {
			greetings = situational(session);
		} else if (data.karma <= -100 || QuestService.isVillain(data)) {
			greetings = lines.greet_friendly;
		} else {
			greetings = lines.greet_neutral;
		}

		mainMenu(session, pick(session, greetings, "Hmpf. Fala logo."));
	}

	/** Shulkers: só conversam com quem traz fruta do coro (ou já trabalhou para eles). */
	private static void shulkerOpening(DialogueSession session, PlayerData data) {
		NpcDialogue lines = session.lines;

		if (!FactionSystem.isShulkerAlly(session.player, data)) {
			show(session, List.of(pick(session, lines.extra("no_chorus"),
					"<p>*clac*</p> ...Visitante sem <p>fruta do coro</p>. Volte com uma na mão. Ou volte flutuando, a escolha é sua.")),
					List.of(new Choice("bye", "Já volto com a fruta.", "neutral", () -> close(session.player, true))), "end");
			return;
		}

		PlayerData.ProfessionProgress progress = data.professions.get(Professions.SHULKER);
		boolean known = progress != null && progress.totalDone > 0;
		mainMenu(session, pick(session, known ? lines.greet_friendly : lines.greet_neutral, "<p>*clac*</p> ...Fale, criatura de fora."));
	}

	/** Bruxas: desconfiam de todo mundo, mas conversam com quem chega com um frasco vazio (ou já as ajudou). */
	private static void witchOpening(DialogueSession session, PlayerData data, KarmaTier karma) {
		NpcDialogue lines = session.lines;

		if (!FactionSystem.isWitchAlly(session.player, data)) {
			if (karma.atLeast(KarmaTier.HONRADO)) {
				speak(session, SoundEvents.VILLAGER_NO, SoundEvents.PIGLIN_ANGRY);
				show(session, List.of(pick(session, lines.hostile,
						"<r>O queridinho da vila...</r> Foram eles que me mandaram para este brejo. Some daqui antes que eu te transforme em sapo.")),
						List.of(new Choice("bye", "Tô indo...", "neutral", () -> close(session.player, true))), "swamp");
				return;
			}

			show(session, List.of(pick(session, lines.extra("no_bottle"),
					"Hihihi... Ninguém vem ao pântano sem motivo. Traga um <p>frasco de vidro</p> na mão e eu sei que você veio aprender, não me queimar.")),
					List.of(new Choice("bye", "Já volto com um frasco.", "neutral", () -> close(session.player, true))), "swamp");
			return;
		}

		PlayerData.ProfessionProgress progress = data.professions.get(Professions.WITCH);
		boolean known = progress != null && progress.totalDone > 0;
		List<String> greetings;

		if (situational(session) != null) {
			greetings = situational(session);
		} else if (known || data.karma <= -100 || QuestService.isVillain(data)) {
			greetings = lines.greet_friendly;
		} else if (data.karma >= 100) {
			greetings = lines.greet_cold;
		} else {
			greetings = lines.greet_neutral;
		}

		mainMenu(session, pick(session, greetings, "Hihihi... Visita! Faz tanto tempo que ninguém aparece."));
	}

	/** Vendedor Ambulante: misterioso, brincalhão e às vezes de mau humor. */
	private static void traderOpening(DialogueSession session, KarmaTier karma) {
		NpcDialogue lines = session.lines;

		if (karma.atMost(KarmaTier.ODIADO)) {
			hostileNode(session);
			return;
		}

		if (karma.atMost(KarmaTier.SUSPEITO) || session.player.getRandom().nextInt(100) < 20) {
			speak(session, SoundEvents.VILLAGER_NO, SoundEvents.PIGLIN_ANGRY);
			mainMenu(session, pick(session, lines.extra("random_hostile"), "Hmpf. Olha, mas não encosta em nada."));
			return;
		}

		List<String> greetings = situational(session);

		if (greetings == null) {
			greetings = karma.atLeast(KarmaTier.CONFIAVEL) ? lines.greet_friendly : lines.greet_neutral;
		}

		mainMenu(session, pick(session, greetings, "O que vai levar, forasteiro?"));
	}

	/** Piglin Bruto: só chega até aqui quem já é Renegado. Respeita força e despreza heróis. */
	private static void bruteOpening(DialogueSession session, PlayerData data) {
		NpcDialogue lines = session.lines;
		PlayerData.ProfessionProgress progress = data.professions.get(Professions.PIGLIN_BRUTE);
		boolean known = progress != null && progress.totalDone > 0;
		List<String> greetings;

		if (data.karma >= 100) {
			greetings = lines.greet_cold;
		} else if (known || QuestService.isVillain(data)) {
			greetings = lines.greet_friendly;
		} else {
			greetings = lines.greet_neutral;
		}

		mainMenu(session, pick(session, greetings, "<n>HRRNGH.</n> Renegado... Fala logo."));
	}

	/**
	 * Enderman: fala uma língua antiga (os símbolos da mesa de encantamento). Sem o Tradutor Ender,
	 * o jogador não entende nada; com ele, dá para conversar e ouvir as histórias do Fim.
	 */
	private static void endermanOpening(DialogueSession session, PlayerData data, KarmaTier karma) {
		NpcDialogue lines = session.lines;
		List<String> greetings = karma.atMost(KarmaTier.ODIADO) ? lines.greet_cold
				: isNight(session) && !lines.greet_night.isEmpty() ? lines.greet_night
				: karma.atLeast(KarmaTier.CONFIAVEL) ? lines.greet_friendly : lines.greet_neutral;
		String greeting = pick(session, greetings, "Voce... olha para mim. Poucos olham e continuam inteiros.");

		if (!session.translated) {
			List<Choice> choices = new ArrayList<>();
			choices.add(new Choice("huh", pick(session, lines.extra("untranslated_reply"), "Hã? Não entendi nada..."), "info", () -> {
				speak(session, SoundEvents.VILLAGER_AMBIENT, SoundEvents.PIGLIN_AMBIENT);
				show(session, List.of(pick(session, lines.followup, "Voce nao entende. Ninguem de fora entende."),
								pick(session, lines.extra("translator_hint"),
										"<d>(Esses símbolos... são iguais aos da mesa de encantamento. Talvez exista algo nas Cidades do Fim que traduza essa língua.)</d>")),
						List.of(new Choice("bye", "<d>(Ir embora devagar, sem encarar...)</d>", "neutral", () -> bye(session))), "end");
			}));
			choices.add(new Choice("bye", "<d>(Ir embora devagar, sem encarar...)</d>", "neutral", () -> bye(session)));
			show(session, List.of(greeting), choices, "end");
			return;
		}

		if (!data.heardEnderman) {
			data.heardEnderman = true;
			PlayerDataManager.save(session.player);
			br.com.cronicasdavila.net.Notify.celebrate(session.player, "Língua do Fim!", "O Tradutor Ender revela as palavras dos Endermen", 0xC77DFF);
			List<Choice> choices = List.of(new Choice("next", "<p>Eu... entendo você!</p>", "magic", () -> mainMenu(session, greeting)));
			show(session, List.of("<d>(O Tradutor Ender vibra no seu bolso, e os símbolos viram palavras.)</d>",
					pick(session, lines.extra("translator_first"), "...Voce entende. Como? Ah. Uma pedra das cidades. Roubada dos <r>shulkers</r>, espero.")),
					choices, "end");
			return;
		}

		mainMenu(session, greeting);
	}

	/** Elebin, o clérigo raro: um encontro especial. */
	private static void elebinOpening(DialogueSession session, PlayerData data) {
		List<String> pages = new ArrayList<>();
		pages.add(pick(session, session.lines.extra("elebin_greet"), "Ah... você me encontrou, {player}. Pouca gente me encontra. Eu sou <p>Elebin</p>."));

		if (!data.metElebin) {
			data.metElebin = true;
			PlayerDataManager.save(session.player);
			br.com.cronicasdavila.net.Notify.celebrate(session.player, "Encontro raro!", "Você conheceu Elebin, o clérigo lendário", 0xC77DFF);
			ServerLevel level = (ServerLevel) session.npc.level();
			level.sendParticles(ParticleTypes.ENCHANT, session.npc.getX(), session.npc.getEyeY(), session.npc.getZ(), 40, 0.5, 0.6, 0.5, 0.6);
		}

		speak(session, SoundEvents.VILLAGER_CELEBRATE, SoundEvents.PIGLIN_CELEBRATE);
		mainMenu(session, pages.get(0));
	}

	/**
	 * Saudação do momento: tempestade, chuva, noite ou a região da vila.
	 * Null quando não há nada especial (ou o NPC não tem falas próprias para isso).
	 */
	private static List<String> situational(DialogueSession session) {
		if (session.situational != null) {
			return session.situational.isEmpty() ? null : session.situational;
		}

		NpcDialogue lines = session.lines;
		List<String> result = null;

		if (VanillaCompat.isThundering(session.npc) && own(session, lines.greet_storm, QuestRegistry.common().greet_storm)) {
			result = lines.greet_storm;
		} else if (VanillaCompat.isRainingAt(session.npc) && own(session, lines.greet_rain, QuestRegistry.common().greet_rain)) {
			result = lines.greet_rain;
		} else if (isNight(session) && !lines.greet_night.isEmpty()) {
			result = lines.greet_night;
		} else if (session.biome != null && !lines.extra("greet_" + session.biome).isEmpty() && session.player.getRandom().nextBoolean()) {
			// Saudação própria da profissão na região da vila (deserto, neve, savana...).
			result = lines.extra("greet_" + session.biome);
		}

		session.situational = result == null ? List.of() : result;
		return result;
	}

	/** Falas que servem para esse NPC: as dos aldeões valem para todos os aldeões; os outros só usam as próprias. */
	private static boolean own(DialogueSession session, List<String> lines, List<String> common) {
		return !lines.isEmpty() && (session.kind.equals("villager") || lines != common);
	}

	private static boolean wearsGold(ServerPlayer player) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			String id = BuiltInRegistries.ITEM.getKey(player.getItemBySlot(slot).getItem()).getPath();

			if (id.startsWith("golden_")) {
				return true;
			}
		}

		return false;
	}

	private static void hostileNode(DialogueSession session) {
		List<Choice> choices = new ArrayList<>();
		addApologyChoice(session, choices);
		choices.add(new Choice("bye", "Tá bom, tô indo...", "neutral", () -> close(session.player, true)));
		speak(session, SoundEvents.VILLAGER_NO, SoundEvents.PIGLIN_ANGRY);
		show(session, List.of(pick(session, session.lines.hostile, "<r>Sai daqui!</r> Não quero conversa com gente como você.")), choices, "danger");
	}

	private static void mainMenu(DialogueSession session, String line) {
		PlayerData data = PlayerDataManager.get(session.player);
		List<Choice> choices = new ArrayList<>();
		boolean questGiver = Professions.givesQuests(session.profession);

		if (questGiver) {
			PlayerData.ActiveQuest active = data.active.get(session.profession);
			Quest quest = active != null ? QuestRegistry.get(active.questId) : null;

			if (quest != null) {
				if (QuestService.isComplete(session.player, active, quest)) {
					choices.add(new Choice("turnin", "<g>Missão cumprida!</g> \"" + quest.title + "\"", "accept", () -> turnIn(session)));
				}

				choices.add(new Choice("info", "Sobre a missão \"" + quest.title + "\"...", "info", () -> questInfo(session)));
			} else {
				choices.add(new Choice("work", pick(session, session.lines.player_work, "Tem algum trabalho pra mim?"), "quest", () -> offerQuest(session)));
			}
		} else if (Professions.UNEMPLOYED.equals(session.profession) || Professions.NITWIT.equals(session.profession)
				|| Professions.CHILD.equals(session.profession) || session.kind.equals("trader")) {
			choices.add(new Choice("work", pick(session, session.lines.player_work, "Tem algum trabalho pra mim?"), "quest", () -> noWork(session)));
		}

		if (!session.lines.lore.isEmpty()) {
			choices.add(new Choice("lore", pick(session, session.lines.player_lore, "Me conta alguma história."), "info", () -> lore(session)));
		}

		if (session.kind.equals("enderman")) {
			addTopic(session, choices, "shulker", "Por que vocês odeiam os shulkers?", "shulker");
			addTopic(session, choices, "endermite", "E os endermites?", "endermite");
		} else if (session.kind.equals("shulker")) {
			addTopic(session, choices, "enderman", "O que você acha dos endermen?", "enderman");
			addTopic(session, choices, "translator", "Como eu entendo a língua dos endermen?", "translator_hint");
		}

		if (questGiver) {
			choices.add(new Choice("rep", "Como eu tô com vocês?", "info", () -> reputation(session)));
		}

		if ((session.npc instanceof Villager && questGiver || session.kind.equals("trader")) && !session.npc.isBaby()) {
			choices.add(new Choice("trade", "Bora negociar.", "trade", () -> trade(session)));
		}

		KarmaTier karma = KarmaSystem.tier(data);

		boolean friendlyKind = session.kind.equals("villager") || session.kind.equals("piglin");

		if (data.karma < 0 && friendlyKind) {
			addApologyChoice(session, choices);
		}

		if (friendlyKind && karma.atLeast(KarmaTier.HONRADO) && !session.npc.isBaby() && !Professions.NITWIT.equals(session.profession)) {
			choices.add(new Choice("bless", "Pode abençoar minha ferramenta?", "magic", () -> bless(session)));
		}

		choices.add(new Choice("bye", pick(session, session.lines.player_bye, "Até mais!"), "neutral", () -> bye(session)));
		show(session, List.of(line), choices, toneFor(session));
	}

	/** Pergunta extra no menu, respondida com uma fala de {@code extra.<key>}. */
	private static void addTopic(DialogueSession session, List<Choice> choices, String id, String question, String key) {
		List<String> answers = session.lines.extra(key);

		if (!answers.isEmpty()) {
			choices.add(new Choice(id, question, "info", () -> show(session, List.of(pick(session, answers, "...")), backOrBye(session), toneFor(session))));
		}
	}

	private static void backToMenu(DialogueSession session) {
		mainMenu(session, pick(session, session.lines.followup, "Mais alguma coisa?"));
	}

	private static List<Choice> backOrBye(DialogueSession session) {
		List<Choice> choices = new ArrayList<>();
		choices.add(new Choice("back", "Mais uma coisa...", "neutral", () -> backToMenu(session)));
		choices.add(new Choice("bye", pick(session, session.lines.player_bye, "Até mais!"), "neutral", () -> bye(session)));
		return choices;
	}

	private static void bye(DialogueSession session) {
		speak(session, SoundEvents.VILLAGER_AMBIENT, SoundEvents.PIGLIN_AMBIENT);
		List<String> byes = isNight(session) && !session.lines.bye_night.isEmpty() ? session.lines.bye_night : session.lines.bye;
		show(session, List.of(pick(session, byes, "Até mais, {player}!")), List.of(), toneFor(session));
		SESSIONS.remove(session.player.getUUID());
		unfreeze(session);
	}

	private static void noWork(DialogueSession session) {
		if (session.kind.equals("trader")) {
			show(session, List.of(pick(session, session.lines.extra("no_work"),
					"Trabalho? Hehehe... Eu vendo, forasteiro. Não contrato.")), backOrBye(session), toneFor(session));
			return;
		}

		show(session, List.of(pick(session, session.lines.extra("no_profession"),
				"Trabalho? Eu? Nem eu tenho trabalho, como é que vou te dar um?")), backOrBye(session), toneFor(session));
	}

	// ---------------------------------------------------------------------------------------------
	// Missões
	// ---------------------------------------------------------------------------------------------

	private static void offerQuest(DialogueSession session) {
		ServerPlayer player = session.player;
		PlayerData data = PlayerDataManager.get(player);

		if (data.active.containsKey(session.profession)) {
			questInfo(session);
			return;
		}

		if (onDayOff(session)) {
			speak(session, SoundEvents.VILLAGER_NO, SoundEvents.PIGLIN_AMBIENT);
			show(session, List.of(pick(session, session.lines.extra("day_off"),
					"Hoje é minha folga, {player}! Nada de serviço. Volte outro dia, ou procure outro colega da profissão.")),
					backOrBye(session), toneFor(session));
			return;
		}

		QuestService.Offer offer = QuestService.pickQuest(player, data, session.profession);

		if (offer == null) {
			show(session, List.of(pick(session, session.lines.locked, "Agora não tenho nada pra você. Volte outra hora.")), backOrBye(session), toneFor(session));
			return;
		}

		session.pendingOffer = offer;
		Quest quest = offer.quest();
		List<String> pages = new ArrayList<>();

		if (offer.repeat()) {
			if (QuestService.blockedByRank(player, data, session.profession)) {
				int nextTier = QuestService.offeredTier(player, data, session.profession) + 1;

				if (Professions.faction(session.profession) == Professions.Faction.VILLAIN) {
					pages.add("Eu teria um serviço mais sujo pra você... mas só confio isso a quem já é <r>"
							+ VillainRank.ofTier(nextTier).displayName() + "</r>. Ganhe fama fazendo serviços pros saqueadores, pros piglins e pras bruxas.");
				} else {
					pages.add("Olha, eu até teria serviço maior... mas pra isso você precisa ser pelo menos <o>" + Rank.ofTier(nextTier).displayName()
							+ "</o>. Ganhe experiência ou complete missões com outros aldeões.");
				}
			}

			pages.add(pick(session, session.lines.repeat, "Lembra daquele serviço? Pois é, precisei de novo. Paga menos, mas ajuda."));
		}

		for (String line : quest.offer) {
			pages.add(QuestService.withTarget(line, quest, offer.target()));
		}

		pages.add(objectiveLine(quest, QuestService.withTarget(quest.objective.desc, quest, offer.target())));
		speak(session, SoundEvents.VILLAGER_AMBIENT, SoundEvents.PIGLIN_AMBIENT);
		show(session, pages, offerChoices(session, quest, true), quest.isDanger() ? "danger" : "calm");
	}

	private static String tierLabel(Quest quest) {
		if (Professions.faction(quest.profession) == Professions.Faction.VILLAIN) {
			return "<r>[" + VillainRank.ofTier(quest.tier).displayName() + "]</r>";
		}

		return "<o>[" + Rank.ofTier(quest.tier).displayName() + "]</o>";
	}

	private static String objectiveLine(Quest quest, String desc) {
		return tierLabel(quest) + " <b>" + quest.title + "</b> — " + desc + ".";
	}

	private static List<Choice> offerChoices(DialogueSession session, Quest quest, boolean allowWhy) {
		List<Choice> choices = new ArrayList<>();
		choices.add(new Choice("accept", pick(session, session.lines.player_accept, "Pode contar comigo!"), "accept", () -> acceptQuest(session)));

		if (allowWhy) {
			choices.add(new Choice("why", pick(session, session.lines.player_why, "E por que eu deveria ajudar?"), "info", () -> whyQuest(session)));
		}

		choices.add(new Choice("decline", pick(session, session.lines.player_decline, "Agora não, valeu."), "decline", () -> declineQuest(session)));
		return choices;
	}

	private static void whyQuest(DialogueSession session) {
		Quest quest = session.pendingOffer.quest();
		List<String> pages = new ArrayList<>();

		for (String line : quest.why) {
			pages.add(QuestService.withTarget(line, quest, session.pendingOffer.target()));
		}

		pages.add(rewardLine(session, session.pendingOffer.rewards()));
		show(session, pages, offerChoices(session, quest, false), quest.isDanger() ? "danger" : "calm");
	}

	/** "Se der conta do recado, te pago 3x Diamante e 1x Maçã. <frase do NPC>" — com o jeito de cada um. */
	private static String rewardLine(DialogueSession session, List<QuestReward> rewards) {
		String intro = pick(session, session.lines.reward_intro, "Se der conta do recado, te pago");
		String outro = pick(session, session.lines.reward_outro, "Combinado é combinado.");
		return intro + " " + QuestService.rewardList(rewards) + ". " + outro;
	}

	private static void acceptQuest(DialogueSession session) {
		if (session.pendingOffer == null) {
			backToMenu(session);
			return;
		}

		QuestService.Offer offer = session.pendingOffer;
		Quest quest = offer.quest();
		QuestService.accept(session.player, PlayerDataManager.get(session.player), offer, session.npc, session.npcName);
		br.com.cronicasdavila.net.Notify.sound(session.player, "quest.accept");
		session.pendingOffer = null;
		speak(session, SoundEvents.VILLAGER_YES, SoundEvents.PIGLIN_ADMIRING_ITEM);

		List<String> pages = new ArrayList<>();

		for (String line : quest.accept) {
			pages.add(QuestService.withTarget(line, quest, offer.target()));
		}

		pages.add("<a>Missão adicionada ao seu Diário</a> <d>(tecla J ou o botão \"Missões\" no inventário)</d>.");
		show(session, pages, backOrBye(session), quest.isDanger() ? "danger" : "calm");
	}

	private static void declineQuest(DialogueSession session) {
		session.pendingOffer = null;
		speak(session, SoundEvents.VILLAGER_NO, SoundEvents.PIGLIN_AMBIENT);
		show(session, List.of(pick(session, session.lines.decline, "Tudo bem. Se mudar de ideia, sabe onde me achar.")), backOrBye(session), toneFor(session));
	}

	private static void questInfo(DialogueSession session) {
		ServerPlayer player = session.player;
		PlayerData data = PlayerDataManager.get(player);
		PlayerData.ActiveQuest active = data.active.get(session.profession);
		Quest quest = active != null ? QuestRegistry.get(active.questId) : null;

		if (quest == null) {
			backToMenu(session);
			return;
		}

		int progress = QuestService.progress(player, active, quest);
		int target = QuestService.target(active, quest);
		int unit = quest.objective.displayUnit();
		boolean complete = progress >= target;
		List<String> pages = new ArrayList<>();

		if (!active.giverUuid.equals(session.npc.getUUID().toString())) {
			pages.add("Ah, quem te pediu isso foi <a>" + active.giverName + "</a>, né? Fiquei sabendo. Pode resolver comigo mesmo.");
		}

		pages.addAll(quest.progress);
		String verb = quest.objective.isDeliver() ? "Você tem" : "Progresso:";
		pages.add(objectiveLine(quest, QuestService.objectiveDesc(active, quest)) + " " + verb + " <y>" + progress / unit + "/" + target / unit + "</y>."
				+ (complete ? " <g>Já dá pra entregar!</g>" : ""));

		List<Choice> choices = new ArrayList<>();

		if (complete) {
			choices.add(new Choice("turnin", "<g>Aqui está! Missão cumprida.</g>", "accept", () -> turnIn(session)));
		}

		choices.add(new Choice("reward", "E a recompensa mesmo, qual era?", "info", () -> {
			show(session, List.of(rewardLine(session, QuestService.rewards(active, quest))), backOrBye(session), toneFor(session));
		}));
		choices.add(new Choice("abandon", "<r>Quero desistir dessa missão.</r>", "danger", () -> confirmAbandon(session, quest)));
		choices.add(new Choice("back", "Beleza, vou continuar.", "neutral", () -> backToMenu(session)));
		show(session, pages, choices, quest.isDanger() ? "danger" : "calm");
	}

	private static void confirmAbandon(DialogueSession session, Quest quest) {
		int karma = QuestService.abandonKarma(session.profession);
		String karmaText = karma > 0 ? "<g>(+" + karma + " karma)</g>" : "(" + karma + " karma)";
		List<Choice> choices = List.of(
				new Choice("yes", "<r>Sim, desisto.</r> " + karmaText, "danger", () -> {
					QuestService.abandon(session.player, PlayerDataManager.get(session.player), session.profession);
					speak(session, SoundEvents.VILLAGER_NO, SoundEvents.PIGLIN_ANGRY);
					show(session, List.of(pick(session, session.lines.abandon, "Que pena... achei que podia confiar em você.")), backOrBye(session), toneFor(session));
				}),
				new Choice("no", "Não, pensei melhor.", "neutral", () -> questInfo(session))
		);
		show(session, List.of("Tem certeza que vai largar <y>" + quest.title + "</y> pela metade? Vou ter que procurar outra pessoa..."), choices, "danger");
	}

	private static void turnIn(DialogueSession session) {
		ServerPlayer player = session.player;
		PlayerData data = PlayerDataManager.get(player);
		PlayerData.ActiveQuest active = data.active.get(session.profession);
		Quest quest = active != null ? QuestRegistry.get(active.questId) : null;
		List<String> extra = QuestService.turnIn(player, data, session.profession);

		if (quest == null || extra == null) {
			show(session, List.of("Hmm... ainda não está completo. Dá uma olhada no seu Diário."), backOrBye(session), toneFor(session));
			return;
		}

		if (session.npc instanceof Villager villager) {
			VanillaCompat.addVillagerXp(villager, quest.tier * 8);
			VanillaCompat.addGossip(villager, player.getUUID(), true, quest.tier * 2);
		}

		ServerLevel level = (ServerLevel) session.npc.level();
		level.sendParticles(switch (session.kind) {
					case "piglin" -> ParticleTypes.FLAME;
					case "pillager" -> ParticleTypes.ANGRY_VILLAGER;
					case "shulker" -> ParticleTypes.END_ROD;
					case "witch" -> ParticleTypes.WITCH;
					case "brute" -> ParticleTypes.FLAME;
					default -> ParticleTypes.HAPPY_VILLAGER;
				},
				session.npc.getX(), session.npc.getEyeY() + 0.3, session.npc.getZ(), 12, 0.4, 0.3, 0.4, 0.02);
		speak(session, SoundEvents.VILLAGER_CELEBRATE, SoundEvents.PIGLIN_CELEBRATE);

		List<String> pages = new ArrayList<>(quest.complete);
		pages.addAll(extra);
		show(session, pages, backOrBye(session), "calm");
	}

	// ---------------------------------------------------------------------------------------------
	// Conversa, reputação, trocas, desculpas, bênçãos
	// ---------------------------------------------------------------------------------------------

	/** Cada NPC conta só uma curiosidade por dia. Para saber mais, converse com outros ou volte amanhã. */
	private static void lore(DialogueSession session) {
		ServerPlayer player = session.player;
		PlayerData data = PlayerDataManager.get(player);
		long day = VanillaCompat.day(player.level());
		String npc = session.npc.getUUID().toString();
		Long told = data.loreDay.get(npc);

		if (told != null && told == day && br.com.cronicasdavila.config.ModSettings.get().loreOncePerDay) {
			show(session, List.of(pick(session, session.lines.lore_done,
					"Já te contei a minha de hoje, {player}. Pergunta pros outros, ou volta amanhã que eu lembro de mais alguma.")),
					backOrBye(session), toneFor(session));
			return;
		}

		List<List<String>> stories = session.lines.lore;
		List<String> story = stories.get(Math.floorMod(session.loreIndex, stories.size()));
		List<String> elebinLore = session.lines.extra("elebin_lore");

		if (session.elebin && !elebinLore.isEmpty() && player.getRandom().nextBoolean()) {
			story = List.of(pick(session, elebinLore, ""));
		}
		data.loreDay.put(npc, day);
		data.loreDay.values().removeIf(value -> value < day);
		PlayerDataManager.save(player);
		show(session, story, backOrBye(session), toneFor(session));
	}

	private static boolean isNight(DialogueSession session) {
		return VanillaCompat.isNight(session.npc.level());
	}

	/** O aldeão está de folga hoje? (Cada vila tem o seu ciclo; veja {@link br.com.cronicasdavila.village.OffDutySystem}.) */
	public static boolean onDayOff(DialogueSession session) {
		return session.npc instanceof Villager villager && br.com.cronicasdavila.village.OffDutySystem.isOff(villager);
	}

	private static void reputation(DialogueSession session) {
		ServerPlayer player = session.player;
		PlayerData data = PlayerDataManager.get(player);
		KarmaTier karma = KarmaSystem.tier(data);
		PlayerData.ProfessionProgress progress = data.profession(session.profession);
		Rank rank = QuestService.effectiveRank(player, data);
		List<String> pages = new ArrayList<>();
		String color = karma.atLeast(KarmaTier.CONFIAVEL) ? "g" : (karma.atMost(KarmaTier.SUSPEITO) ? "r" : "y");

		pages.add("Pelo que dizem por aí, você é <" + color + "><b>" + karma.displayName() + "</b></" + color + "> (karma "
				+ data.karma + "). <d>" + karma.description() + "</d>");

		boolean villainSide = Professions.faction(session.profession) == Professions.Faction.VILLAIN;
		String affinity = progress.mastered
				? (villainSide ? "<r>Arquivilão ☠</r>" : "<o>Mestre ★</o>")
				: "<o>" + Professions.levelName(session.profession, progress.level) + "</o>";
		String next = progress.mastered || progress.level >= 5
				? (progress.mastered ? "Não tenho mais nada pra te ensinar." : "Falta só completar uma missão de Mestre.")
				: "Mais <y>" + (QuestService.QUESTS_PER_PROFESSION_LEVEL - progress.doneAtLevel) + " missão(ões)</y> e confio coisas maiores a você.";
		pages.add("Entre nós, " + QuestService.plural(session.profession) + ", sua afinidade é " + affinity + ". " + next);

		String rankLine = "No geral você é <o>" + rank.displayName() + "</o>";

		if (rank != Rank.MESTRE) {
			Rank questRank = data.questRank();
			rankLine += ". Pra virar <o>" + rank.next().displayName() + "</o>: chegue ao nível <y>" + rank.next().minXpLevel()
					+ "</y> de XP, ou complete mais <y>" + Math.max(0, questRank.questsToPromote() - data.tierCompletions[questRank.tier()])
					+ "</y> missão(ões) de " + questRank.displayName() + " (esse nível fica pra sempre).";
		} else {
			rankLine += ". Não há mais nada acima de você.";
		}

		if (villainSide) {
			VillainRank villainRank = VillainRank.ofTier(data.villainRank);
			rankLine = "Sua <r>Vilania</r> é <r><b>" + villainRank.displayName() + "</b></r>";

			if (villainRank != VillainRank.ARQUIVILAO) {
				rankLine += ". Pra virar <r>" + villainRank.next().displayName() + "</r>: complete mais <y>"
						+ Math.max(0, villainRank.questsToPromote() - data.villainTierCompletions[villainRank.tier()])
						+ "</y> serviço(s) de " + villainRank.displayName() + " para saqueadores, piglins ou bruxas. <d>(XP não conta aqui.)</d>";
			} else {
				rankLine += ". Ninguém é mais temido do que você.";
			}

			pages.add(rankLine);
			pages.add("Quem vira <r>Arquivilão</r>, domina <y>saqueadores, piglins, bruxas e piglins brutos</y> e faz <y>"
					+ Professions.LEGEND_QUESTS + " serviços sujos</y> diferentes recebe a marca do <p>Vilão da História</p>. Você já dominou <y>"
					+ QuestService.villainMasteredCount(data) + "/" + Professions.VILLAIN_FACTIONS.size() + "</y> e fez <y>"
					+ QuestService.villainQuestsDone(data) + "/" + Professions.LEGEND_QUESTS + "</y> serviços.");
			show(session, pages, backOrBye(session), toneFor(session));
			return;
		}

		pages.add(rankLine);

		if (session.kind.equals("villager")) {
			int mastered = QuestService.masteredCount(data);
			pages.add("Dizem que quem se torna Mestre em todas as <y>13 profissões</y> vira <o>Herói da Vila</o> pra sempre (são pelo menos <y>"
					+ Professions.LEGEND_QUESTS + " missões</y>). Você já dominou <y>" + mastered + "/13</y>.");
		}

		show(session, pages, backOrBye(session), toneFor(session));
	}

	private static void trade(DialogueSession session) {
		ServerPlayer player = session.player;

		if (session.npc instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader trader) {
			String line = pick(session, session.lines.extra("trade_open"), "");
			close(player, true);

			if (!line.isEmpty()) {
				player.sendOverlayMessage(br.com.cronicasdavila.util.Text.parse("<a>" + session.npcName + ":</a> " + fill(session, line)));
			}

			VanillaCompat.openTrade(trader, player);
			return;
		}

		if (!(session.npc instanceof Villager villager)) {
			backToMenu(session);
			return;
		}

		if (onDayOff(session)) {
			show(session, List.of(pick(session, session.lines.extra("closed"),
					"Hoje a banca tá fechada, {player}. É minha folga! Volta amanhã que eu faço preço bom.")), backOrBye(session), toneFor(session));
			return;
		}

		close(player, true);
		VanillaCompat.openTrade(villager, player);
	}

	private static void addApologyChoice(DialogueSession session, List<Choice> choices) {
		PlayerData data = PlayerDataManager.get(session.player);
		long day = session.player.level().getGameTime() / 24000L;

		if (data.lastApologyDay == day) {
			return;
		}

		String cost = session.piglin ? "3 lingotes de ouro" : "3 esmeraldas";
		choices.add(new Choice("apology", "Quero me redimir. <o>(" + cost + ")</o>", "magic", () -> apology(session)));
	}

	private static void apology(DialogueSession session) {
		ServerPlayer player = session.player;
		PlayerData data = PlayerDataManager.get(player);
		String item = session.piglin ? "minecraft:gold_ingot" : "minecraft:emerald";

		if (QuestService.countInInventory(player, List.of(item)) < 3) {
			show(session, List.of(session.piglin
					? "<n>HRRNF!</n> Desculpa sem <o>ouro</o> é só barulho. Volta com <o>3 lingotes</o>."
					: "Palavras são bonitas, mas não enchem barriga. Volte com <o>3 esmeraldas</o>."), backOrBye(session), "danger");
			return;
		}

		int removed = 0;

		for (int i = 0; i < player.getInventory().getContainerSize() && removed < 3; i++) {
			ItemStack stack = player.getInventory().getItem(i);

			if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item)) {
				int take = Math.min(3 - removed, stack.getCount());
				stack.shrink(take);
				removed += take;
			}
		}

		data.lastApologyDay = player.level().getGameTime() / 24000L;
		data.addKarma(15);
		PlayerDataManager.save(player);
		speak(session, SoundEvents.VILLAGER_YES, SoundEvents.PIGLIN_ADMIRING_ITEM);
		List<String> pages = new ArrayList<>();
		pages.add(pick(session, session.lines.apology_thanks, "Tá bom... vou tentar esquecer. Mas estou de olho."));
		pages.add("<g>+15 karma.</g> <d>(Uma vez por dia.)</d>");

		if (KarmaSystem.tier(data).atMost(KarmaTier.ODIADO)) {
			show(session, pages, List.of(new Choice("bye", "Obrigado...", "neutral", () -> close(player, true))), "neutral");
		} else {
			show(session, pages, backOrBye(session), toneFor(session));
		}
	}

	private static void bless(DialogueSession session) {
		ServerPlayer player = session.player;
		PlayerData data = PlayerDataManager.get(player);
		long day = player.level().getGameTime() / 24000L;

		if (data.lastBlessDay == day) {
			show(session, List.of("Uma bênção por dia, {player}. Até a magia precisa descansar."), backOrBye(session), toneFor(session));
			return;
		}

		ItemStack stack = player.getMainHandItem();

		if (stack.isEmpty() || !stack.isDamageableItem()) {
			show(session, List.of("Segure na mão a ferramenta, arma ou armadura que você quer que eu abençoe."), backOrBye(session), toneFor(session));
			return;
		}

		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		String primary;
		int primaryMax;

		if (path.endsWith("_sword")) {
			primary = "minecraft:sharpness";
			primaryMax = 3;
		} else if (path.endsWith("_pickaxe") || path.endsWith("_axe") || path.endsWith("_shovel") || path.endsWith("_hoe")) {
			primary = "minecraft:efficiency";
			primaryMax = 3;
		} else if (path.equals("bow")) {
			primary = "minecraft:power";
			primaryMax = 3;
		} else if (path.endsWith("_helmet") || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots")) {
			primary = "minecraft:protection";
			primaryMax = 2;
		} else {
			primary = "minecraft:unbreaking";
			primaryMax = 3;
		}

		ServerLevel level = (ServerLevel) player.level();
		String applied = tryUpgrade(level, stack, primary, primaryMax);

		if (applied == null) {
			applied = tryUpgrade(level, stack, "minecraft:unbreaking", 3);
		}

		if (applied == null) {
			show(session, List.of("Essa aí já está abençoada até o talo. Mais que isso seria abuso!"), backOrBye(session), toneFor(session));
			return;
		}

		data.lastBlessDay = day;
		PlayerDataManager.save(player);
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.5);
		level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		show(session, List.of(pick(session, session.lines.bless, "Que essa ferramenta te sirva bem."),
				"<p>Bênção recebida:</p> " + applied + "."), backOrBye(session), "calm");
	}

	private static String tryUpgrade(ServerLevel level, ItemStack stack, String enchantmentId, int max) {
		Holder<Enchantment> enchantment = VanillaCompat.enchantment(level, enchantmentId);

		if (enchantment == null || !enchantment.value().canEnchant(stack)) {
			return null;
		}

		int current = EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);

		if (current >= max) {
			return null;
		}

		stack.enchant(enchantment, current + 1);
		return "<p>{ench:" + enchantmentId + "} " + Rank.ofTier(current + 1).roman() + "</p>";
	}
}

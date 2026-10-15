package br.com.cronicasdavila.dialogue;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import br.com.cronicasdavila.quest.NpcDialogue;
import br.com.cronicasdavila.quest.QuestService;

/**
 * Uma conversa em andamento entre um jogador e um NPC.
 */
public class DialogueSession {
	public final ServerPlayer player;
	public final Mob npc;
	/** Chave da profissão (farmer, piglin, nitwit, child...). */
	public final String profession;
	public final String npcName;
	public final boolean piglin;
	/** Tipo de NPC: "villager", "piglin", "pillager" ou "shulker". */
	public final String kind;
	public final NpcDialogue lines;
	public final boolean previousNoAi;

	/** Ações das escolhas mostradas agora. */
	public final Map<String, Runnable> handlers = new HashMap<>();
	public QuestService.Offer pendingOffer;
	public int loreIndex;
	public int ticks;

	public DialogueSession(ServerPlayer player, Mob npc, String profession, String npcName, String kind, NpcDialogue lines) {
		this.player = player;
		this.npc = npc;
		this.profession = profession;
		this.npcName = npcName;
		this.kind = kind;
		this.piglin = "piglin".equals(kind);
		this.lines = lines;
		this.previousNoAi = npc.isNoAi();
		this.loreIndex = player.getRandom().nextInt(Math.max(1, lines.lore.size()));
	}
}

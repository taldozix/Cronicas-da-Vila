package br.com.cronicasdavila.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;

import br.com.cronicasdavila.CronicasDaVila;
import br.com.cronicasdavila.data.PlayerData;
import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.karma.KarmaSystem;
import br.com.cronicasdavila.net.Notify;
import br.com.cronicasdavila.quest.Quest;
import br.com.cronicasdavila.quest.QuestRegistry;
import br.com.cronicasdavila.quest.QuestService;
import br.com.cronicasdavila.rank.Professions;
import br.com.cronicasdavila.rank.VillainRank;

/**
 * /cronicas — abre o menu do mod (nada vai para o chat).
 * <ul>
 *     <li>/cronicas, /cronicas missoes, /cronicas reputacao, /cronicas guia, /cronicas opcoes: abrem a aba.</li>
 *     <li>/cronicas hud completo|compacto|oculto: muda o rastreador de missões.</li>
 *     <li>/cronicas admin ...: comandos de teste (operador). O resultado aparece no Registro do menu.</li>
 * </ul>
 */
public final class CronicasCommand {
	private CronicasCommand() {
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tab(String name, String tab) {
		return literal(name).executes(ctx -> open(ctx, tab));
	}

	private static int open(CommandContext<CommandSourceStack> ctx, String tab) throws CommandSyntaxException {
		Notify.openMenu(ctx.getSource().getPlayerOrException(), tab);
		return 1;
	}

	/** Resultado de um comando de teste: vai para o Registro e abre o menu na aba Início. */
	private static int done(ServerPlayer player, String markup) {
		Notify.send(player, markup);
		Notify.openMenu(player, "inicio");
		return 1;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("cronicas")
				.executes(ctx -> open(ctx, "inicio"))
				.then(tab("inicio", "inicio"))
				.then(tab("missoes", "missoes"))
				.then(tab("reputacao", "reputacao"))
				.then(tab("guia", "guia"))
				.then(tab("comandos", "guia"))
				.then(tab("opcoes", "opcoes"))
				.then(literal("hud")
						.then(literal("completo").executes(ctx -> hud(ctx, "completo")))
						.then(literal("compacto").executes(ctx -> hud(ctx, "compacto")))
						.then(literal("oculto").executes(ctx -> hud(ctx, "oculto"))))
				.then(literal("admin")
						.requires(PermissionPredicates.require(CronicasDaVila.id("admin"), PermissionLevel.GAMEMASTERS))
						.then(literal("karma").then(argument("valor", IntegerArgumentType.integer(-1000, 1000)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerDataManager.get(player);
							data.karma = IntegerArgumentType.getInteger(ctx, "valor");
							PlayerDataManager.save(player);
							return done(player, "<g>Karma definido para " + data.karma + " (" + KarmaSystem.tier(data).displayName() + ").</g>");
						})))
						.then(literal("nivel").then(argument("nivel", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerDataManager.get(player);
							data.questRank = IntegerArgumentType.getInteger(ctx, "nivel");
							PlayerDataManager.save(player);
							return done(player, "<g>Nível por missões definido para " + data.questRank().displayName() + ".</g>");
						})))
						.then(literal("vilania").then(argument("nivel", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerDataManager.get(player);
							data.villainRank = IntegerArgumentType.getInteger(ctx, "nivel");
							PlayerDataManager.save(player);
							return done(player, "<r>Vilania definida para " + VillainRank.ofTier(data.villainRank).displayName() + ".</r>");
						})))
						.then(literal("afinidade")
								.then(argument("profissao", StringArgumentType.word())
										.then(argument("nivel", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
											ServerPlayer player = ctx.getSource().getPlayerOrException();
											String profession = StringArgumentType.getString(ctx, "profissao");

											if (!Professions.givesQuests(profession)) {
												return done(player, "<r>Profissão desconhecida.</r> Use: " + String.join(", ", Professions.QUEST_GIVERS) + ".");
											}

											PlayerData data = PlayerDataManager.get(player);
											PlayerData.ProfessionProgress progress = data.profession(profession);
											progress.level = IntegerArgumentType.getInteger(ctx, "nivel");
											progress.doneAtLevel = 0;
											PlayerDataManager.save(player);
											return done(player, "<g>Afinidade com " + Professions.displayName(profession) + ": "
													+ Professions.levelName(profession, progress.level) + ".</g>");
										}))))
						.then(literal("completar").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerDataManager.get(player);

							for (PlayerData.ActiveQuest active : data.active.values()) {
								Quest quest = QuestRegistry.get(active.questId);

								if (quest != null && (quest.objective.isKill() || quest.objective.isMine())) {
									active.progress = QuestService.target(active, quest);
								} else if (quest != null && (quest.objective.isStat() || quest.objective.isCraft())) {
									active.baseline -= QuestService.target(active, quest);
								}
							}

							PlayerDataManager.save(player);
							return done(player, "<g>Objetivos de abate, mineração e estatística completados</g> <d>(entregas ainda pedem os itens).</d>");
						}))
						.then(literal("resetar").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerDataManager.reset(player);
							return done(player, "<y>Seu progresso no Crônicas da Vila foi zerado.</y>");
						}))));
	}

	private static int hud(CommandContext<CommandSourceStack> ctx, String mode) throws CommandSyntaxException {
		Notify.hudMode(ctx.getSource().getPlayerOrException(), mode);
		return 1;
	}
}

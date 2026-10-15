package br.com.cronicasdavila.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import br.com.cronicasdavila.quest.Quest;
import br.com.cronicasdavila.quest.QuestRegistry;
import br.com.cronicasdavila.quest.QuestService;
import br.com.cronicasdavila.rank.Professions;
import br.com.cronicasdavila.rank.Rank;
import br.com.cronicasdavila.util.Text;

/**
 * /cronicas — mostra seu status.
 * /cronicas admin ... — comandos de teste (precisa ser operador).
 */
public final class CronicasCommand {
	private CronicasCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("cronicas")
				.executes(CronicasCommand::status)
				.then(literal("admin")
						.requires(PermissionPredicates.require(CronicasDaVila.id("admin"), PermissionLevel.GAMEMASTERS))
						.then(literal("karma").then(argument("valor", IntegerArgumentType.integer(-1000, 1000)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerDataManager.get(player);
							data.karma = IntegerArgumentType.getInteger(ctx, "valor");
							PlayerDataManager.save(player);
							player.sendSystemMessage(Text.parse("<g>Karma definido para " + data.karma + " (" + KarmaSystem.tier(data).displayName() + ").</g>"));
							return 1;
						})))
						.then(literal("nivel").then(argument("nivel", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerDataManager.get(player);
							data.questRank = IntegerArgumentType.getInteger(ctx, "nivel");
							PlayerDataManager.save(player);
							player.sendSystemMessage(Text.parse("<g>Nível por missões definido para " + data.questRank().displayName() + ".</g>"));
							return 1;
						})))
						.then(literal("afinidade")
								.then(argument("profissao", StringArgumentType.word())
										.then(argument("nivel", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
											ServerPlayer player = ctx.getSource().getPlayerOrException();
											String profession = StringArgumentType.getString(ctx, "profissao");

											if (!Professions.givesQuests(profession)) {
												player.sendSystemMessage(Text.parse("<r>Profissão desconhecida. Use: farmer, fisherman, shepherd, butcher, "
														+ "leatherworker, fletcher, mason, toolsmith, weaponsmith, armorer, cartographer, librarian, cleric, piglin.</r>"));
												return 0;
											}

											PlayerData data = PlayerDataManager.get(player);
											PlayerData.ProfessionProgress progress = data.profession(profession);
											progress.level = IntegerArgumentType.getInteger(ctx, "nivel");
											progress.doneAtLevel = 0;
											PlayerDataManager.save(player);
											player.sendSystemMessage(Text.parse("<g>Afinidade com " + Professions.displayName(profession) + ": "
													+ Rank.ofTier(progress.level).displayName() + ".</g>"));
											return 1;
										}))))
						.then(literal("completar").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerDataManager.get(player);

							for (PlayerData.ActiveQuest active : data.active.values()) {
								Quest quest = QuestRegistry.get(active.questId);

								if (quest != null && (quest.objective.isKill() || quest.objective.isMine())) {
									active.progress = quest.objective.count;
								} else if (quest != null && (quest.objective.isStat() || quest.objective.isCraft())) {
									active.baseline -= quest.objective.count;
								}
							}

							PlayerDataManager.save(player);
							player.sendSystemMessage(Text.parse("<g>Objetivos de abate/mineração/estatística completados (entregas ainda pedem os itens).</g>"));
							return 1;
						}))
						.then(literal("resetar").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerDataManager.reset(player);
							player.sendSystemMessage(Text.parse("<y>Seu progresso no Crônicas da Vila foi zerado.</y>"));
							return 1;
						}))));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		PlayerData data = PlayerDataManager.get(player);
		Rank rank = QuestService.effectiveRank(player, data);
		player.sendSystemMessage(Text.parse("<o><b>— Crônicas da Vila —</b></o>"));
		player.sendSystemMessage(Text.parse("Nível: <o>" + rank.displayName() + "</o> <d>(por missões: " + data.questRank().displayName()
				+ ", por XP: " + Rank.fromXpLevel(player.experienceLevel).displayName() + ")</d>"));
		player.sendSystemMessage(Text.parse("Karma: <y>" + data.karma + "</y> — " + KarmaSystem.tier(data).displayName()));
		player.sendSystemMessage(Text.parse("Missões ativas: <y>" + data.active.size() + "</y> · concluídas: <y>" + data.totalCompleted
				+ "</y> · profissões dominadas: <y>" + QuestService.masteredCount(data) + "/13</y>"));
		player.sendSystemMessage(Text.parse("<d>Abra o Diário de Missões com a tecla J.</d>"));
		return 1;
	}
}

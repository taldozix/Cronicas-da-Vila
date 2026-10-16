package br.com.cronicasdavila.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma missão carregada dos arquivos JSON em {@code /cronicasdavila/quests/}.
 *
 * <p>Os textos aceitam marcação de cor, por exemplo {@code <r>Elimine</r>} (vermelho),
 * {@code <g>Colha</g>} (verde), {@code <y>32 trigos</y>} (amarelo), {@code <o>ouro</o>} (dourado),
 * {@code <a>lugar</a>} (ciano) e {@code <p>magia</p>} (roxo).
 */
public class Quest {
	public String id;
	public String profession;
	public int tier;
	public String title;
	/** "calm" (destaques verdes/amarelos) ou "danger" (destaques vermelhos). */
	public String tone = "calm";

	/** A história que o NPC conta ao oferecer a missão. */
	public List<String> offer = new ArrayList<>();
	/** "Por que eu deveria fazer isso?" — como a missão ajuda o NPC. */
	public List<String> why = new ArrayList<>();
	/** O que o NPC diz quando o jogador aceita. */
	public List<String> accept = new ArrayList<>();
	/** Dicas/informações quando o jogador volta a falar com a missão ativa. */
	public List<String> progress = new ArrayList<>();
	/** O que o NPC diz ao receber a missão concluída. */
	public List<String> complete = new ArrayList<>();

	public QuestObjective objective;
	public List<QuestReward> rewards = new ArrayList<>();

	/** Karma ganho ao concluir (padrão: 10 por nível). */
	public Integer karma;
	/** Pontos de experiência vanilla dados ao concluir (padrão: 15 por nível). */
	public Integer xp;

	/**
	 * Karma da missão. Padrão: aldeões +10 por nível; saqueadores -20 por nível (missões de vilania);
	 * piglins, bruxas e shulkers 0 (neutros para a vila).
	 */
	public int karmaReward() {
		if (karma != null) {
			return karma;
		}

		return switch (profession == null ? "" : profession) {
			case br.com.cronicasdavila.rank.Professions.PILLAGER -> -tier * 20;
			case br.com.cronicasdavila.rank.Professions.PIGLIN, br.com.cronicasdavila.rank.Professions.SHULKER,
					br.com.cronicasdavila.rank.Professions.WITCH -> 0;
			default -> tier * 10;
		};
	}

	/** Karma numa repetição (metade, mantendo o sinal). */
	public int karmaReward(boolean repeat) {
		int value = karmaReward();
		return repeat ? value / 2 : value;
	}

	public int xpReward() {
		if (xp != null) {
			return xp;
		}

		// Os shulkers pagam bem melhor: as missões deles são as mais difíceis do mod.
		return br.com.cronicasdavila.rank.Professions.SHULKER.equals(profession) ? tier * 40 : tier * 15;
	}

	public boolean isDanger() {
		return "danger".equals(tone);
	}
}

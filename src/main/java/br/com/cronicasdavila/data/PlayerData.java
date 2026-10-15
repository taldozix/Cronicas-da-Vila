package br.com.cronicasdavila.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import br.com.cronicasdavila.rank.Rank;

/**
 * Tudo o que o mod guarda sobre um jogador. Serializado em JSON dentro de um
 * "attachment" do Fabric, então fica salvo junto com o jogador no mundo.
 */
public class PlayerData {
	public static final int KARMA_MIN = -1000;
	public static final int KARMA_MAX = 1000;

	public int karma = 0;

	/** Nível conquistado por missões (1..5). Nunca diminui. */
	public int questRank = 1;
	/** Missões concluídas por nível (índice 1..5) — conta para subir o nível pelas missões. */
	public int[] tierCompletions = new int[6];

	/** Progresso com cada profissão. */
	public Map<String, ProfessionProgress> professions = new HashMap<>();
	/** Missões ativas, no máximo uma por profissão. */
	public Map<String, ActiveQuest> active = new LinkedHashMap<>();
	/** Ids das missões já concluídas pelo menos uma vez. */
	public Set<String> completed = new HashSet<>();
	public int totalCompleted = 0;

	/** Missão que aparece no rastreador do HUD (id da profissão). */
	public String tracked;

	// Controle diário (dia = tempo de jogo / 24000)
	public long dayKey = -1;
	public int dailyTradeKarma = 0;
	public int dailyDefendKarma = 0;
	public long lastApologyDay = -1;
	public long lastBlessDay = -1;

	// Últimos valores de estatísticas vanilla (para calcular ganhos de karma)
	public int lastTradeStat = -1;
	public int lastRaidStat = -1;

	// Vilania (só sobe com missões de saqueadores e piglins; nunca com XP)
	public int villainRank = 1;
	public int[] villainTierCompletions = new int[6];
	public int villainQuests = 0;
	public boolean villainAnnounced = false;
	/** Tempo seguido no mundo normal sendo Vilão da História (zera ao voltar ao Nether). */
	public long overworldTicks = 0;

	public boolean heroAnnounced = false;
	public int lastAnnouncedRank = 1;

	public ProfessionProgress profession(String key) {
		return professions.computeIfAbsent(key, k -> new ProfessionProgress());
	}

	public Rank questRank() {
		return Rank.ofTier(questRank);
	}

	public void addKarma(int amount) {
		karma = Math.max(KARMA_MIN, Math.min(KARMA_MAX, karma + amount));
	}

	public void resetDailyIfNeeded(long day) {
		if (dayKey != day) {
			dayKey = day;
			dailyTradeKarma = 0;
			dailyDefendKarma = 0;
		}
	}

	public static class ProfessionProgress {
		/** Nível de afinidade com essa profissão (1..5). */
		public int level = 1;
		/** Missões concluídas no nível atual. */
		public int doneAtLevel = 0;
		/** Concluiu pelo menos uma missão de Mestre dessa profissão. */
		public boolean mastered = false;
		public int totalDone = 0;
	}

	public static class ActiveQuest {
		public String questId;
		public String giverName;
		public String giverUuid;
		public int giverX;
		public int giverY;
		public int giverZ;
		public String giverDimension;
		/** Progresso de missões kill/mine. */
		public int progress = 0;
		/** Valor da estatística quando a missão foi aceita (missões stat/craft). */
		public int baseline = 0;
		/** É uma repetição (recompensas pela metade). */
		public boolean repeat = false;
		/** Já avisou o jogador que terminou. */
		public boolean notified = false;
	}
}

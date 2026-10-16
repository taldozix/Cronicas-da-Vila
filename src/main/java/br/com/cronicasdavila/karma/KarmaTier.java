package br.com.cronicasdavila.karma;

/**
 * Faixas de karma, inspiradas na "honra" de Red Dead Redemption 2.
 * Vai de -1000 a +1000.
 */
public enum KarmaTier {
	AMALDICOADO(-1000, "Amaldiçoado", 0x8B0000,
			"Raios te procuram nas tempestades, golens, endermen e até as vacas querem sua cabeça. A fome te persegue. No caminho da vilania: Sorte I, Velocidade I e Força I."),
	ODIADO(-749, "Odiado", 0xFF3333,
			"Golens e endermen te atacam, animais te dão cabeçadas e os aldeões não te dão mais missões. No caminho da vilania: Sorte I e Velocidade I."),
	MAL_VISTO(-499, "Mal-visto", 0xFF7733,
			"Os golens de ferro te atacam assim que te veem. As trocas estão bem mais caras. No caminho da vilania: Sorte I."),
	SUSPEITO(-299, "Suspeito", 0xFFFF55,
			"Os aldeões cochicham sobre você. Trocas mais caras e um pouco de azar. No caminho da vilania: sem azar e, a partir de -250, Sorte I."),
	NEUTRO(-99, "Neutro", 0xFFFFFF,
			"Ninguém te ama, ninguém te odeia. Por enquanto."),
	CONFIAVEL(100, "Confiável", 0x55FF55,
			"Os aldeões falam bem de você: trocas um pouco mais baratas."),
	RESPEITADO(250, "Respeitado", 0x55FFFF,
			"Sorte constante (Sorte I) e trocas ainda mais baratas."),
	HONRADO(500, "Honrado", 0x5599FF,
			"Velocidade I, sorte e os aldeões podem abençoar suas ferramentas uma vez por dia."),
	LENDARIO(750, "Lendário", 0xFFAA00,
			"Força I, Velocidade I, Sorte I, bênçãos diárias e os melhores preços da região.");

	private final int min;
	private final String displayName;
	private final int color;
	private final String description;

	KarmaTier(int min, String displayName, int color, String description) {
		this.min = min;
		this.displayName = displayName;
		this.color = color;
		this.description = description;
	}

	public static KarmaTier of(int karma) {
		KarmaTier result = AMALDICOADO;

		for (KarmaTier tier : values()) {
			if (karma >= tier.min) {
				result = tier;
			}
		}

		return result;
	}

	public String displayName() {
		return displayName;
	}

	public int color() {
		return color;
	}

	public String description() {
		return description;
	}

	public boolean atLeast(KarmaTier other) {
		return ordinal() >= other.ordinal();
	}

	public boolean atMost(KarmaTier other) {
		return ordinal() <= other.ordinal();
	}
}

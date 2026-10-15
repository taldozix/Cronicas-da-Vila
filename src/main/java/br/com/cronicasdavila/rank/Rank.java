package br.com.cronicasdavila.rank;

/**
 * Os cinco níveis do mod, do menor para o maior.
 *
 * <p>O nível efetivo do jogador é o MAIOR entre:
 * <ul>
 *     <li>o nível conquistado por missões (fica gravado para sempre);</li>
 *     <li>o nível pela experiência (XP) atual — se o jogador perder XP, perde esse nível.</li>
 * </ul>
 */
public enum Rank {
	AMADOR(1, "Amador", 0, 0xAAAAAA),
	APRENDIZ(2, "Aprendiz", 20, 0x55FF55),
	AVENTUREIRO(3, "Aventureiro", 40, 0x55FFFF),
	GUERREIRO(4, "Guerreiro", 60, 0xFF5555),
	MESTRE(5, "Mestre", 200, 0xFFAA00);

	private final int tier;
	private final String displayName;
	private final int minXpLevel;
	private final int color;

	Rank(int tier, String displayName, int minXpLevel, int color) {
		this.tier = tier;
		this.displayName = displayName;
		this.minXpLevel = minXpLevel;
		this.color = color;
	}

	public int tier() {
		return tier;
	}

	public String displayName() {
		return displayName;
	}

	public int minXpLevel() {
		return minXpLevel;
	}

	public int color() {
		return color;
	}

	public Rank next() {
		return this == MESTRE ? MESTRE : values()[ordinal() + 1];
	}

	public static Rank ofTier(int tier) {
		int clamped = Math.max(1, Math.min(5, tier));
		return values()[clamped - 1];
	}

	/** Nível pela experiência: 0-19 Amador, 20-39 Aprendiz, 40-59 Aventureiro, 60-199 Guerreiro, 200+ Mestre. */
	public static Rank fromXpLevel(int xpLevel) {
		Rank result = AMADOR;

		for (Rank rank : values()) {
			if (xpLevel >= rank.minXpLevel) {
				result = rank;
			}
		}

		return result;
	}

	/** Quantas missões do nível atual são necessárias para subir de nível pelas missões. */
	public int questsToPromote() {
		return switch (this) {
			case AMADOR -> 5;
			case APRENDIZ -> 6;
			case AVENTUREIRO -> 8;
			case GUERREIRO -> 10;
			case MESTRE -> 0;
		};
	}

	public String roman() {
		return switch (tier) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> "V";
		};
	}
}

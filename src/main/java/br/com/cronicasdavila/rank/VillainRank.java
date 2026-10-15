package br.com.cronicasdavila.rank;

/**
 * Os cinco níveis de Vilania — o lado oposto de Amador → Mestre.
 *
 * <p>Ao contrário do lado bom, a Vilania <b>não</b> sobe com a experiência do jogo:
 * só com missões dos saqueadores e dos piglins. E, uma vez conquistada, não se perde.
 */
public enum VillainRank {
	ARRUACEIRO(1, "Arruaceiro", 0xB0A0A0),
	BANDIDO(2, "Bandido", 0xD08060),
	RENEGADO(3, "Renegado", 0xD05050),
	CARRASCO(4, "Carrasco", 0xA02040),
	ARQUIVILAO(5, "Arquivilão", 0x9B30FF);

	private final int tier;
	private final String displayName;
	private final int color;

	VillainRank(int tier, String displayName, int color) {
		this.tier = tier;
		this.displayName = displayName;
		this.color = color;
	}

	public int tier() {
		return tier;
	}

	public String displayName() {
		return displayName;
	}

	public int color() {
		return color;
	}

	public VillainRank next() {
		return this == ARQUIVILAO ? ARQUIVILAO : values()[ordinal() + 1];
	}

	public static VillainRank ofTier(int tier) {
		return values()[Math.max(1, Math.min(5, tier)) - 1];
	}

	/** Missões de inimigos (saqueadores/piglins) do nível atual para subir de Vilania. */
	public int questsToPromote() {
		return switch (this) {
			case ARRUACEIRO, BANDIDO -> 4;
			case RENEGADO, CARRASCO -> 5;
			case ARQUIVILAO -> 0;
		};
	}
}

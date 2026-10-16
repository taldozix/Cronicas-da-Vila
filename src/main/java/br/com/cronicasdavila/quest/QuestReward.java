package br.com.cronicasdavila.quest;

/**
 * Uma recompensa em item. Para livros encantados ou ferramentas encantadas,
 * preencha {@code enchant} (ex.: "minecraft:mending") e {@code level}.
 */
public class QuestReward {
	public String item;
	public int count = 1;
	public String enchant;
	public int level = 1;
	/** Recompensa extra sorteada ao aceitar a missão ("bônus da sorte"). */
	public boolean bonus;

	public QuestReward() {
	}

	public QuestReward(String item, int count) {
		this.item = item;
		this.count = count;
	}

	public QuestReward copyWithCount(int newCount) {
		QuestReward copy = new QuestReward(item, newCount);
		copy.enchant = enchant;
		copy.level = level;
		copy.bonus = bonus;
		return copy;
	}
}

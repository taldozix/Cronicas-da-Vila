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
}

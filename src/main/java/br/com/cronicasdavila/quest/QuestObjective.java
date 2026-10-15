package br.com.cronicasdavila.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * O objetivo de uma missão.
 *
 * <ul>
 *     <li>{@code kill}: derrotar criaturas. {@code targets} = ids de entidade (ex.: "minecraft:zombie").</li>
 *     <li>{@code deliver}: entregar itens ao NPC. {@code targets} = ids de item ou tags com "#" (ex.: "#minecraft:logs").</li>
 *     <li>{@code mine}: quebrar blocos. {@code targets} = ids de bloco ou tags com "#".</li>
 *     <li>{@code stat}: estatística vanilla, contada a partir do momento em que a missão foi aceita
 *     (ex.: "minecraft:fish_caught", "minecraft:animals_bred", "minecraft:raid_win").</li>
 *     <li>{@code craft}: fabricar itens. {@code targets} = ids de item.</li>
 * </ul>
 */
public class QuestObjective {
	public String type;
	public List<String> targets = new ArrayList<>();
	public int count = 1;
	/** Descrição curta com marcação de cor, ex.: "<r>Elimine</r> <r>20 zumbis</r>". */
	public String desc;

	public boolean isKill() {
		return "kill".equals(type);
	}

	public boolean isDeliver() {
		return "deliver".equals(type);
	}

	public boolean isMine() {
		return "mine".equals(type);
	}

	public boolean isStat() {
		return "stat".equals(type);
	}

	public boolean isCraft() {
		return "craft".equals(type);
	}
}

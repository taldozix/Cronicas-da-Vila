package br.com.cronicasdavila.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * Falas "soltas" de um tipo de NPC (saudações, histórias do mundo, recusas...).
 * Carregado de {@code /cronicasdavila/npcs/<profissão>.json}. Campos vazios
 * são preenchidos com o arquivo {@code _common.json}.
 */
public class NpcDialogue {
	/** Saudação quando o karma do jogador é alto. */
	public List<String> greet_friendly = new ArrayList<>();
	/** Saudação com karma neutro. */
	public List<String> greet_neutral = new ArrayList<>();
	/** Saudação com karma baixo (desconfiado). */
	public List<String> greet_cold = new ArrayList<>();
	/** Karma muito baixo: o NPC se recusa a conversar direito. */
	public List<String> hostile = new ArrayList<>();
	/** "Mais alguma coisa?" ao voltar para o menu. */
	public List<String> followup = new ArrayList<>();
	/** Quando o jogador recusa uma missão. */
	public List<String> decline = new ArrayList<>();
	/** Quando não há missão disponível para o nível. */
	public List<String> locked = new ArrayList<>();
	/** Introdução de uma missão repetida (já feita antes). */
	public List<String> repeat = new ArrayList<>();
	/** Agradecimento por um pedido de desculpas (presente). */
	public List<String> apology_thanks = new ArrayList<>();
	/** Falas ao abençoar uma ferramenta. */
	public List<String> bless = new ArrayList<>();
	/** Despedidas. */
	public List<String> bye = new ArrayList<>();
	/** Saudação à noite (karma neutro ou melhor). */
	public List<String> greet_night = new ArrayList<>();
	/** Despedidas à noite. */
	public List<String> bye_night = new ArrayList<>();
	/** Saudação com chuva. */
	public List<String> greet_rain = new ArrayList<>();
	/** Saudação com tempestade (raios). */
	public List<String> greet_storm = new ArrayList<>();
	/** Saudações de sexta-feira, sábado e domingo (pelo calendário do jogo). */
	public List<String> greet_friday = new ArrayList<>();
	public List<String> greet_saturday = new ArrayList<>();
	public List<String> greet_sunday = new ArrayList<>();
	/** Começo da frase da recompensa, logo antes da lista de itens ("Se der conta do recado, te pago"). */
	public List<String> reward_intro = new ArrayList<>();
	/** Frase depois da lista de recompensas. */
	public List<String> reward_outro = new ArrayList<>();
	/** Já contou a curiosidade do dia para esse jogador. */
	public List<String> lore_done = new ArrayList<>();
	/** Quando o jogador desiste de uma missão. */
	public List<String> abandon = new ArrayList<>();
	/** Histórias sobre o mundo. Cada história é uma lista de páginas. */
	public List<List<String>> lore = new ArrayList<>();

	/** Respostas do jogador (sorteadas para dar variedade). */
	public List<String> player_work = new ArrayList<>();
	public List<String> player_accept = new ArrayList<>();
	public List<String> player_why = new ArrayList<>();
	public List<String> player_decline = new ArrayList<>();
	public List<String> player_lore = new ArrayList<>();
	public List<String> player_more_lore = new ArrayList<>();
	public List<String> player_bye = new ArrayList<>();

	/** Nomes possíveis para esse tipo de NPC. */
	public List<String> names = new ArrayList<>();

	/** Falas para situações especiais (ex.: "sleeping", "no_gold", "busy", "no_profession"). */
	public java.util.Map<String, List<String>> extra = new java.util.HashMap<>();

	public List<String> extra(String key) {
		return extra.getOrDefault(key, List.of());
	}

	/**
	 * Junta estas falas com as do bioma da vila (deserto, neve, pântano...). Saudações, histórias,
	 * "mais alguma coisa" e despedidas passam a misturar os dois; o resto continua igual.
	 */
	public NpcDialogue mergedWith(NpcDialogue biome) {
		NpcDialogue merged = new NpcDialogue();
		merged.fillFrom(this);
		merged.greet_friendly = concat(greet_friendly, biome.greet_friendly);
		merged.greet_neutral = concat(greet_neutral, biome.greet_neutral);
		merged.greet_cold = concat(greet_cold, biome.greet_cold);
		merged.followup = concat(followup, biome.followup);
		merged.bye = concat(bye, biome.bye);
		merged.greet_night = concat(greet_night, biome.greet_night);
		merged.lore = new ArrayList<>(biome.lore);
		merged.lore.addAll(lore);
		return merged;
	}

	private static List<String> concat(List<String> a, List<String> b) {
		List<String> result = new ArrayList<>(a);
		result.addAll(b);
		return result;
	}

	public void fillFrom(NpcDialogue common) {
		if (greet_friendly.isEmpty()) greet_friendly = common.greet_friendly;
		if (greet_neutral.isEmpty()) greet_neutral = common.greet_neutral;
		if (greet_cold.isEmpty()) greet_cold = common.greet_cold;
		if (hostile.isEmpty()) hostile = common.hostile;
		if (followup.isEmpty()) followup = common.followup;
		if (decline.isEmpty()) decline = common.decline;
		if (locked.isEmpty()) locked = common.locked;
		if (repeat.isEmpty()) repeat = common.repeat;
		if (apology_thanks.isEmpty()) apology_thanks = common.apology_thanks;
		if (bless.isEmpty()) bless = common.bless;
		if (bye.isEmpty()) bye = common.bye;
		if (greet_night.isEmpty()) greet_night = common.greet_night;
		if (bye_night.isEmpty()) bye_night = common.bye_night;
		if (greet_rain.isEmpty()) greet_rain = common.greet_rain;
		if (greet_storm.isEmpty()) greet_storm = common.greet_storm;
		if (greet_friday.isEmpty()) greet_friday = common.greet_friday;
		if (greet_saturday.isEmpty()) greet_saturday = common.greet_saturday;
		if (greet_sunday.isEmpty()) greet_sunday = common.greet_sunday;
		if (reward_intro.isEmpty()) reward_intro = common.reward_intro;
		if (reward_outro.isEmpty()) reward_outro = common.reward_outro;
		if (lore_done.isEmpty()) lore_done = common.lore_done;
		if (abandon.isEmpty()) abandon = common.abandon;
		if (lore.isEmpty()) lore = common.lore;
		if (player_work.isEmpty()) player_work = common.player_work;
		if (player_accept.isEmpty()) player_accept = common.player_accept;
		if (player_why.isEmpty()) player_why = common.player_why;
		if (player_decline.isEmpty()) player_decline = common.player_decline;
		if (player_lore.isEmpty()) player_lore = common.player_lore;
		if (player_more_lore.isEmpty()) player_more_lore = common.player_more_lore;
		if (player_bye.isEmpty()) player_bye = common.player_bye;
		if (names.isEmpty()) names = common.names;

		for (java.util.Map.Entry<String, List<String>> entry : common.extra.entrySet()) {
			extra.putIfAbsent(entry.getKey(), entry.getValue());
		}
	}
}

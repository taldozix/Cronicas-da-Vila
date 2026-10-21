package br.com.cronicasdavila.client;

/**
 * Textos da aba Guia: como o mod funciona, os comandos e as teclas.
 * Cada tópico é {título, parágrafo, parágrafo...}, com as marcações de cor do mod.
 */
final class Guide {
	static final String[][] TOPICS = {
			{
					"Como funciona",
					"Aldeões, piglins, saqueadores, bruxas, shulkers e o <a>Vendedor Ambulante</a> contam histórias e passam <y>missões</y>. Cada um tem o seu jeito de falar, e as falas mudam com o bioma da vila e com a hora do dia.",
					"Cada missão é sorteada para você: a quantidade pedida e as recompensas variam um pouco, e às vezes vem um <a>bônus da sorte</a>.",
					"Só dá para ter <y>uma missão ativa por profissão</y>. Volte a quem pediu (ou a qualquer NPC da mesma profissão) para entregar.",
					"Cada NPC conta <y>uma curiosidade por dia</y>. Quer saber mais? Converse com outros, ou volte amanhã."
			},
			{
					"Níveis e afinidade",
					"<o>Amador → Aprendiz → Aventureiro → Guerreiro → Mestre</o>. O nível sobe com a XP (0, 20, 40, 60 e 200) ou com missões. O que você conquista com missões fica para sempre.",
					"Cada profissão tem a própria <y>afinidade</y> com você: 2 missões do nível atual sobem um nível e liberam missões maiores.",
					"Uma missão de Mestre em cada uma das 13 profissões de aldeão dá o <o>Herói da Vila</o> permanente."
			},
			{
					"Karma",
					"O karma vai de <r>-1000</r> a <g>+1000</g>. Ajudar as vilas, trocar e proteger os aldeões sobe; machucar aldeões, quebrar o sino, as camas ou os blocos de trabalho e roubar baús desce.",
					"Herói: <g>Sorte I</g> em +250, <g>Velocidade I</g> em +500 e <g>Força I</g> em +750, além de bênçãos e preços melhores.",
					"Quem está no caminho da vilania ganha os mesmos efeitos, espelhados: -250, -500 e -750.",
					"Karma muito baixo traz castigos: golens, endermen e animais contra você, e raios nas tempestades."
			},
			{
					"Vilania",
					"Missões de <r>saqueadores</r>, <n>piglins</n> e <a>bruxas</a> sobem a Vilania: <r>Arruaceiro → Bandido → Renegado → Carrasco → Arquivilão</r>. A XP não conta e a Vilania nunca cai.",
					"Arquivilão + a missão máxima de cada inimigo + karma negativo = <p>Vilão da História</p>: todo ser vivo foge de você, mas o mundo normal te cansa e, longe do Nether por um dia, você queima.",
					"Com <r>100% de Vilania</r> (todas as missões de vilão), karma -1000 e o nível máximo, o efeito fica para sempre.",
					"Desistir de um serviço de vilão é um ato de bondade: <g>+5 de karma</g>."
			},
			{
					"Barganha e trocas",
					"Com afinidade <y>Aventureiro</y> ou mais com a profissão, aparece o botão <a>⚖ Barganhar</a> na tela de trocas.",
					"Escolha a troca, ofereça outro item do inventário no lugar do preço e veja a chance antes de tentar. Se o aldeão recusar, você fica com o item, mas ele não barganha mais com você até amanhã.",
					"Para conversar: aldeões com o botão direito; piglins usando ouro; saqueadores com uma <p>garrafa ominosa</p> na mão; bruxas com um <p>frasco de vidro</p>; shulkers com uma <p>fruta do coro</p>. O Vendedor Ambulante conversa com qualquer um (agachado, você abre direto as trocas)."
			},
			{
					"Vida nas vilas",
					"Cada aldeão tem o seu <y>dia de folga</y>, e cada vila tem o seu costume: a folga se repete a cada 3 a 7 dias, conforme a vila. Na folga o aldeão tira o uniforme, não trabalha, não passa missões e não abre a banca, mas fica com algo da profissão na mão. Os trabalhos pesados folgam dois dias por ciclo.",
					"Os NPCs falam diferente com <a>chuva</a>, com <r>tempestade</r>, à noite e na <y>sexta, no sábado e no domingo</y>. O dia da semana aparece na aba Início.",
					"Dizem que alguns clérigos se chamam <p>Elebin</p>... mas é raro encontrar um.",
					"Se um aldeão te vir pegando coisas de um baú da vila que não é seu, você perde karma e os <r>golens</r> vão atrás de você. Baús que você colocou ou onde guardou coisas são seus.",
					"À noite os NPCs falam diferente. E se estiverem dormindo... deixe-os dormir."
			},
			{
					"Comandos",
					"<y>/cronicas</y> — abre este menu na aba Início.",
					"<y>/cronicas missoes</y>, <y>reputacao</y>, <y>guia</y>, <y>opcoes</y> — abrem a aba.",
					"<y>/cronicas hud completo</y> | <y>compacto</y> | <y>oculto</y> — muda o rastreador de missões.",
					"<d>Para testes (operador):</d>",
					"<y>/cronicas admin karma <valor></y> — define o karma (-1000 a 1000).",
					"<y>/cronicas admin nivel <1-5></y> — define o nível por missões.",
					"<y>/cronicas admin vilania <1-5></y> — define o nível de Vilania.",
					"<y>/cronicas admin afinidade <profissão> <1-5></y> — define a afinidade (farmer, piglin, witch...).",
					"<y>/cronicas admin completar</y> — completa os objetivos das missões ativas (menos entregas).",
					"<y>/cronicas admin resetar</y> — zera o seu progresso.",
					"<d>O resultado dos comandos aparece num aviso no canto da tela, não no chat.</d>"
			},
			{
					"Teclas",
					"<y>J</y> — abre e fecha este menu. No inventário também há um botão para cada aba (Início, Missões, Reputação, Guia e Opções).",
					"<y>H</y> — alterna o rastreador: completo, compacto (só os ícones e a porcentagem, na lateral) ou oculto.",
					"No menu: <y>1</y> a <y>5</y> abrem cada aba direto; <y>Tab</y> ou <y>← →</y> trocam de aba; na aba Missões, <y>↑ ↓</y> escolhem e <y>T</y> rastreia no HUD.",
					"Na conversa: clique, Enter, Espaço ou E avançam o texto; mouse, setas, W/S ou 1 a 9 escolhem a resposta; Esc sai.",
					"<d>As teclas podem ser trocadas em Opções → Controles.</d>"
			}
	};

	private Guide() {
	}
}

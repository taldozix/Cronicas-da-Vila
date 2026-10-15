# Crônicas da Vila

Mod de RPG para **Minecraft Java Edition 26.3** (Fabric). Os aldeões e os piglins passam a contar histórias, dar missões e lembrar de como você os trata.

- **170 missões** com história própria:
  - 13 profissões de aldeão × 10 missões;
  - 15 dos **piglins** no Nether;
  - 15 dos **saqueadores**, as missões de vilania;
  - 10 dos **shulkers** no Fim, mais difíceis e mais bem pagas.
- **Caixa de diálogo no estilo Zelda: Breath of the Wild.** O texto aparece letra por letra, as respostas ficam à direita e dá para escolher com o mouse ou com as setas do teclado.
- **Conversa em modo cinema.** O NPC para e fica olhando para você, a câmera trava no rosto dele e o HUD some enquanto vocês conversam.
- **Cinco níveis:** Amador → Aprendiz → Aventureiro → Guerreiro → Mestre.
- **Karma** no estilo da honra de Red Dead Redemption 2, com bênçãos e castigos.
- **Diário de Missões** como uma nova aba do inventário, além de um rastreador da missão atual no HUD.
- **Herói da Vila permanente** para quem chega a Mestre em todas as 13 profissões.
- **O lado do mal:** a **Vilania**, com 5 níveis (Arruaceiro → Bandido → Renegado → Carrasco → Arquivilão), e o efeito novo **Vilão da História**.
- **Falas por bioma:** cada tipo de vila (planície, deserto, savana, neve, taiga, selva e pântano) tem histórias e problemas próprios.
- Tudo em **português do Brasil**, com sotaques e personalidades diferentes para cada profissão.

---

## Requisitos

| O quê | Versão |
|---|---|
| Minecraft Java Edition | 26.3 |
| Fabric Loader | 0.19.5 ou mais novo |
| Fabric API | 0.162.0+26.3 ou mais nova |
| Java (só para compilar) | 25 |

## Como compilar e instalar

1. Instale o **JDK 25** (por exemplo, o Temurin 25).
2. Dentro desta pasta, rode:
   ```bash
   ./gradlew build        # no Windows: gradlew.bat build
   ```
3. O mod fica em `build/libs/cronicas-da-vila-1.1.0.jar`.
4. Instale o [Fabric Loader](https://fabricmc.net/use/) para a versão 26.3 e coloque na pasta `.minecraft/mods` o `.jar` do mod e o da [Fabric API](https://modrinth.com/mod/fabric-api).

Para testar direto do código, use `./gradlew runClient`.

> **Se a compilação acusar algum nome que não existe:** quase toda chamada ao Minecraft que costuma mudar entre versões está isolada em `src/main/java/br/com/cronicasdavila/compat/VanillaCompat.java`. Ajustar esse arquivo costuma resolver.

---

## Como jogar

| Ação | Controle |
|---|---|
| Conversar com um aldeão | Botão direito |
| Conversar com um piglin | Botão direito **usando pelo menos uma peça de armadura de ouro** e **sem** um lingote de ouro na mão (com o lingote na mão, o piglin faz o escambo normal) |
| Conversar com um saqueador | Botão direito segurando uma **garrafa ominosa**, ou já tendo karma negativo |
| Conversar com um shulker | Botão direito segurando uma **fruta do coro** |
| Trocar com o aldeão sem conversar | Shift + botão direito, ou a opção "Bora negociar" no diálogo |
| Avançar o texto | Clique, Enter, Espaço ou E |
| Escolher uma resposta | Mouse, ↑ ↓, W/S, a rodinha do mouse ou as teclas 1 a 9 |
| Sair da conversa | Esc |
| Abrir o Diário de Missões | **J**, ou o botão **✦ Missões** no inventário |

Durante a conversa:

- Você não anda nem mexe a câmera, e o NPC fica parado olhando para você.
- O HUD (barra de itens, vida, mira) é escondido e surgem faixas pretas no topo e na base da tela, como num filme.
- A conversa termina sozinha se você se afastar, se o NPC morrer ou se você levar dano.

### Cores no diálogo

| Cor | Uso |
|---|---|
| 🔴 Vermelho | Ações perigosas e importantes: "**Elimine** 25 esqueletos" |
| 🟢 Verde | Ações tranquilas: "**Colha**", "**Entregue**" |
| 🟡 Amarelo | Quantidades e itens pedidos |
| 🟠 Dourado | Recompensas e níveis |
| 🔵 Ciano | Lugares e pessoas |
| 🟣 Roxo | Magia e encantamentos |

---

## Os sistemas

### Níveis

| Nível | Pela XP do jogo | Pelas missões |
|---|---|---|
| 1. Amador | 0–19 | começo |
| 2. Aprendiz | 20–39 | 5 missões de Amador |
| 3. Aventureiro | 40–59 | 6 missões de Aprendiz |
| 4. Guerreiro | 60–199 | 8 missões de Aventureiro |
| 5. Mestre | 200+ | 10 missões de Guerreiro |

- O seu nível é sempre o **maior** entre o nível pela XP e o nível pelas missões.
- O nível conquistado pelas **missões é seu para sempre**.
- O nível que veio **só da XP se perde** se você perder XP. Por exemplo: chegou a Guerreiro só pela XP, morreu e voltou ao nível 10 de XP → volta a ser o que tinha conquistado pelas missões.
- Missões de um nível só aparecem depois que você chega a esse nível.

### Afinidade com cada profissão

Cada profissão tem a sua própria afinidade com você, que também vai de Amador a Mestre:

- Completar **2 missões** do nível atual de uma profissão sobe a afinidade com ela.
- O aldeão oferece missões do nível da afinidade, limitado ao seu nível geral.
- Só dá para ter **uma missão ativa por profissão**. Para pegar outra, termine (ou abandone) a atual.
- Ao falar de novo com um aldeão daquela profissão, ele relembra a missão, dá dicas, mostra o seu progresso e recebe a entrega.
- Quando as missões inéditas de um nível acabam, o NPC repete uma missão antiga pagando **metade** da recompensa.
- Ao entregar uma missão, o aldeão ganha experiência de troca: na próxima troca ele sobe de nível e libera trocas melhores.

### Herói da Vila

Complete **uma missão de Mestre de cada uma das 13 profissões** de aldeão e ganhe o efeito **Herói da Vila II permanente**.

### Vilania: o lado do mal

| Nível | Como subir |
|---|---|
| 1. Arruaceiro | começo |
| 2. Bandido | 4 missões de Arruaceiro |
| 3. Renegado | 4 missões de Bandido |
| 4. Carrasco | 5 missões de Renegado |
| 5. Arquivilão | 5 missões de Carrasco |

- A Vilania **só sobe com missões** dos saqueadores e dos piglins. A XP do jogo **não conta**.
- Uma vez conquistada, a Vilania **não se perde**.
- As missões dos **saqueadores** dão **karma negativo** (-20 por nível da missão). São o ódio dos Exilados pelos aldeões: arrancar sinos, roubar colheitas, quebrar camas e oficinas, destruir golens de ferro, eliminar mercadores e entregar o tributo de esmeraldas.
- As missões dos **piglins** contam para a Vilania e não mexem no karma.
- Como os saqueadores reagem a você:
  - Com uma missão deles ativa, ou já sendo cúmplice (karma negativo), eles **não te atacam**.
  - Com uma garrafa ominosa na mão, eles também te deixam em paz.
  - Se você bater em um, ele revida.
  - Eles **odeiam heróis**: com karma Honrado ou mais, eles te expulsam.

### Vilão da História

Para ganhar o efeito, você precisa:
- ser **Arquivilão**;
- completar **uma missão máxima dos saqueadores e uma dos piglins**;
- ter **karma negativo**.

Com o efeito **Vilão da História**:
- **Todas as criaturas fogem de você**, hostis e pacíficas. Os aldeões entram em pânico, igual fogem de um zumbi.
- Ficam de fora os chefes (Wither, Dragão do Ender, Warden e guardiã anciã) e os saqueadores, que respeitam o seu líder.
- Quem você atacar revida durante alguns segundos.
- No **mundo normal** você fica com **Fadiga I** o tempo todo.
- Depois de **1 dia do jogo seguido** fora do Nether (20 minutos), **você começa a pegar fogo**. Voltar ao Nether zera o tempo; no Fim o tempo fica parado.
- Os aldeões fogem de você, então não dá mais para trocar com eles.
- Os castigos do karma muito baixo, como os raios nas tempestades, continuam valendo.

O Herói da Vila e o Vilão da História são opostos: o Herói exige karma não negativo, e o Vilão exige karma negativo.

### Shulkers (o Fim)

- São 10 missões, de Amador a Mestre, mais difíceis e com recompensas melhores que as dos aldeões do mesmo nível.
- Os shulkers são neutros: as missões deles não mexem no karma e não contam para nenhum dos dois lados.
- O nível das missões oferecidas acompanha o maior dos seus níveis, entre o do herói e o da Vilania.
- Com uma missão deles ativa, ou depois de completar uma, os shulkers param de te atacar.

### Karma (de -1000 a +1000)

| Faixa | Karma | Efeito |
|---|---|---|
| Lendário | ≥ 750 | Força I, Velocidade I, Sorte I, bênção diária de ferramenta, melhores preços |
| Honrado | ≥ 500 | Velocidade I, Sorte I, bênção diária de ferramenta |
| Respeitado | ≥ 250 | Sorte I, trocas mais baratas |
| Confiável | ≥ 100 | Trocas um pouco mais baratas |
| Neutro | -99 a 99 | — |
| Suspeito | ≤ -100 | Azar, trocas mais caras |
| Mal-visto | ≤ -300 | Golens de ferro te atacam quando te veem |
| Odiado | ≤ -500 | Endermen te atacam, animais pacíficos vêm dar cabeçadas e os aldeões param de dar missões |
| Amaldiçoado | ≤ -750 | Raios caem em você durante tempestades e a fome te persegue |

- A **bênção** melhora a ferramenta, arma ou armadura que está na sua mão, sem quebrar o equilíbrio: Afiação até III, Eficiência até III, Força até III, Proteção até II ou Inquebrável até III. Uma vez por dia do jogo.
- Os descontos usam o sistema de "fofoca" do próprio Minecraft, então se somam aos descontos normais.

**Ganha karma:**

| Ação | Karma |
|---|---|
| Completar uma missão | +10 por nível da missão |
| Trocar com aldeões | +2 por troca, até 30 por dia |
| Matar monstros dentro da vila | +1 (+3 se for servo dos Exilados), até 25 por dia |
| Vencer uma invasão | +100 |
| Pedir desculpas no diálogo (3 esmeraldas; 3 lingotes de ouro para piglins) | +15, uma vez por dia |

**Perde karma:**

| Ação | Karma |
|---|---|
| Machucar um aldeão | -10 (criança: -25) |
| Matar um aldeão | -150 (criança: -250) |
| Atacar um golem de ferro | -3 |
| Destruir um golem de ferro | -30 |
| Atacar um mercador ambulante | -5 |
| Matar um mercador ambulante | -40 |
| Matar um piglin | -10 |
| Quebrar o sino da vila | -40 |
| Quebrar cama ou bloco de trabalho dentro da vila | -8 |
| Desistir de uma missão | -5 |

### Diário de Missões (tecla J)

- **Aba Missões:** o seu nível, uma barra de progresso pela XP e pelas missões, a barra de karma e a lista de missões ativas. Cada missão mostra quem pediu e onde essa pessoa está, o objetivo com barra de progresso, as recompensas, a história e dicas.
- **Rastrear no HUD (tecla T):** a missão escolhida aparece no canto da tela.
- **Desistir:** clique duas vezes para confirmar.
- **Aba Reputação:** karma, progresso até o Herói da Vila e afinidade com as 13 profissões de aldeão e com os shulkers.
- **Aba Vilania:** nível de Vilania, progresso, afinidade com saqueadores e piglins, status de Vilão da História e o tempo que falta para você queimar no mundo normal.

---

## As profissões e as personalidades

| Profissão | Personalidade | Exemplos de missões |
|---|---|---|
| Fazendeiro | Caipira mineiro ("uai, sô") | Zumbis na plantação, Inverno Longo, **derrotar o Wither** pela Terra Morta |
| Pescador | Baiano ("oxe, meu rei") | Afogados na praia, guardiã anciã, **as Três Anciãs** |
| Pastor | Poeta sereno do sertão | Phantoms, saqueadores, **o Warden** que levou o irmão gêmeo |
| Açougueiro | Gaúcho ("bah, tchê") | Churrasco, hoglins, **5 devastadores** |
| Coureiro | Nordestino ("vixe, arretado") | Zumbis do deserto, tatu, **élitro** do Fim |
| Flecheiro | Paulistano ("orra, meu") | Tiro ao alvo, ghasts, **Dragão do Ender** |
| Pedreiro | Carioca ("caraca, mermão") | Traças, Câmaras de Provação, **montar um sinalizador** |
| Ferramenteiro | Engenheiro nerd | Redstone, detritos antigos, **núcleo pesado** |
| Ferreiro de Armas | Veterano esquentado ("POR MIL BIGORNAS!") | Evocadores, vencer uma invasão, **200 servos dos Exilados** |
| Armeiro | Guardião estoico | Moldes de ornamento, shulkers, **Wither** |
| Cartógrafo | Navegador português ("ó pá, fixe!") | Pântano, mansão, **cabeça de dragão** |
| Bibliotecário | Erudito pomposo | Grimório roubado, fragmentos de eco, **o Warden e a Grande Maldição** |
| Clérigo | Místico solene | Lágrimas de ghast, crânios profanos, **dois Withers** |
| **Piglin** (Nether) | Obcecado por ouro ("HRRNF!") | Cubos de magma, Ossudos, **vingar o Grande Porcão** |
| **Saqueador** (vilania) | Exilado ressentido ("os mansos narigudos") | Arrancar sinos, roubar colheitas, **o Grande Silêncio** |
| **Shulker** (Fim) | Ancião misterioso ("*clac*") | Fruta do coro, endermites, **derrotar o dragão 3 vezes** |

Também têm falas próprias o **bobão da vila**, os **desempregados** e as **crianças**.

### A história do mundo

As histórias de cada NPC se ligam umas às outras:

- A **Grande Maldição**, que roubou a fala dos aldeões e só deixou o "hrmm".
- Os **Exilados** (illagers), aldeões expulsos por praticar magia proibida.
- A **Noite Sem Fim** e o **Primeiro Golem**.
- As **Cidades Antigas** e o despertar do **Warden**.
- O **Reino Dourado** dos piglins, destruído pela Besta de Três Cabeças (o Wither).

---

## Comandos

| Comando | Para que serve |
|---|---|
| `/cronicas` | Mostra o seu status |
| `/cronicas admin karma <valor>` | Define o karma (só operadores) |
| `/cronicas admin nivel <1-5>` | Define o nível conquistado pelas missões |
| `/cronicas admin vilania <1-5>` | Define o nível de Vilania |
| `/cronicas admin afinidade <profissão> <1-5>` | Define a afinidade com uma profissão (`farmer`, `piglin`...) |
| `/cronicas admin completar` | Completa os objetivos de abate, mineração e estatística das missões ativas (para testes) |
| `/cronicas admin resetar` | Zera todo o seu progresso |

---

## Como editar ou criar missões

Tudo fica em JSON, fácil de editar:

- `src/main/resources/cronicasdavila/quests/<profissão>.json`: as missões.
- `src/main/resources/cronicasdavila/npcs/<profissão>.json`: saudações, histórias, nomes e respostas do jogador.
- `src/main/resources/cronicasdavila/npcs/_common.json`: as falas padrão.
- `src/main/resources/cronicasdavila/npcs/biomes/<bioma>.json`: falas extras de cada tipo de vila (`plains`, `desert`, `savanna`, `snow`, `taiga`, `jungle`, `swamp`).

Tipos de objetivo:

| Tipo | O que conta |
|---|---|
| `kill` | Criaturas derrotadas |
| `deliver` | Itens entregues ao NPC (aceita tags com `#`) |
| `mine` | Blocos quebrados |
| `stat` | Estatísticas do jogo, como `minecraft:fish_caught`, `minecraft:animals_bred`, `minecraft:raid_win` ou `minecraft:enchant_item` |
| `craft` | Itens fabricados |

Marcação de cor nos textos:

- Cores: `<r>vermelho</r>`, `<g>verde</g>`, `<y>amarelo</y>`, `<o>dourado</o>`, `<a>ciano</a>`, `<p>roxo</p>`, `<n>laranja-Nether</n>` e `<d>cinza</d>`.
- Estilos: `<b>negrito</b>` e `<i>itálico</i>`.
- Nomes traduzidos automaticamente: `{item:minecraft:diamond}`, `{entity:minecraft:zombie}` e `{ench:minecraft:mending}`.
- Substituições: `{player}` vira o nome do jogador e `{npc}` vira o nome do NPC.

Ao iniciar o servidor, o mod confere todos os ids de itens, criaturas e blocos das missões e avisa no log se algum não existir.

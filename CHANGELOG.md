# Histórico de versões

A versão do mod fica em `gradle.properties`. Toda atualização com melhorias sobe o número do meio
(1.1.0 → 1.2.0 → 1.3.0...). Ao enviar para o branch `main`, o GitHub compila e publica a release
com o `.jar` automaticamente, usando as notas desta página.

## [1.3.0] - 2026-10-16 — Menu novo, HUD compacto e opções do mod

### Novidades
- **Menu novo (tecla J)** no estilo das telas de servidores modded: título grande, botões à esquerda com destaque ciano e painel escuro à direita.
  - **Início:** o seu boneco e o seu resumo (nível, karma, Vilania, missões, Herói e Vilão).
  - **Missões:** lista com ícone e porcentagem; detalhes com objetivo, recompensas em ícones, dicas e história.
  - **Reputação:** lado herói e lado vilão lado a lado, mais a afinidade com cada profissão e inimigo.
  - **Guia:** como o mod funciona, todos os comandos e as teclas.
  - **Opções:** o "mod menu" do Crônicas da Vila.
- **Opções do mundo** (dono do mundo ou operador): XP e recompensas das missões, recompensas variadas, chance de bônus da sorte, missões sorteadas, intensidade do karma, chance e afinidade da barganha, folga dos trabalhos pesados, roubo de baús, curiosidade por dia e maldição do Vilão.
- **Rastreador compacto:** só os ícones das missões e a porcentagem na lateral da tela, como no Pixelmon. Tecla **H** alterna entre completo, compacto e oculto.
- **Nada no chat:** os avisos do mod e o resultado dos comandos aparecem por alguns segundos no canto da tela.
- **Animações:** o menu abre com os botões deslizando da esquerda e o painel da direita; o destaque dos botões desliza ao passar o mouse; o conteúdo desliza ao trocar de aba; o rastreador de missões e os avisos entram e saem deslizando pela lateral.
- **Sons próprios do mod:** a "voz" das letras quando os NPCs falam (cada tipo com um tom), cliques do menu, deslize ao abrir o menu e ao trocar o HUD, missão aceita, objetivo cumprido, missão concluída, missão abandonada e a fanfarra de subir de nível. Dá para desligar em Opções.
- **Comemoração na tela** ao subir de nível, de afinidade ou de Vilania e ao virar Mestre/Arquivilão: uma faixa animada com o título, estrelas e faíscas.
- **Comandos abrem o menu:** `/cronicas`, `/cronicas missoes`, `reputacao`, `guia`, `opcoes` e `/cronicas hud completo|compacto|oculto`.

### Mudanças
- **Cada aba com o seu atalho:** teclas **1 a 5** no menu e um botão para cada aba ao lado do inventário. O menu e a conversa com os NPCs soltam o mouse ao abrir, para dar para clicar.
- O antigo Diário de Missões foi substituído pelo menu novo, com menos texto espalhado e sem o tutorial de como pegar missões.

## [1.2.0] - 2026-10-16 — Bruxas, barganha e equilíbrio entre herói e vilão

### Novidades
- **Bruxas dão missões (30):** a vida solitária no pântano, o gato Breu, as rãs, a irmã que mora em outro brejo e a mágoa de ter sido expulsa da vila. Para conversar, segure um **frasco de vidro**.
  - As missões das bruxas contam para a **Vilania** e não mexem no karma.
  - Com uma missão delas ativa, ou depois de completar uma, as bruxas param de te atacar.
- **Mais missões para os inimigos das vilas:** piglins, saqueadores e shulkers agora têm **30 missões cada** (6 por nível).
  - Piglins: passeios de lavagante, basalto, blazes, ghasts, sucata de netherita e o golpe contra os brutos.
  - Saqueadores: bestas, portas, tochas, estantes, plantações, oficinas e **invasões** começadas por você.
  - Shulkers: pedra do Fim, foguetes, voos de élitro, cristais do Fim, sopro do dragão e cabeças de dragão.
- **Missões únicas para cada jogador:** a quantidade pedida varia (de 75% a 135%), as recompensas acompanham e, às vezes, vem um **bônus da sorte**.
- **Barganha na tela de trocas:** com afinidade **Aventureiro** ou mais na profissão, aparece o botão **⚖ Barganhar**.
  - Você escolhe uma troca, oferece outro item do inventário no lugar do preço e vê a **chance de dar certo**.
  - Se o aldeão recusar, você fica com o item, mas ele não barganha mais com você até o dia seguinte.
- **Roubo de baús:** se um aldeão te vir pegando coisas de um baú da vila que não é seu, você perde karma e os **golens de ferro** vão atrás de você.
- **Folga dos trabalhos pesados:** fazendeiros, pescadores, açougueiros, pedreiros, ferramenteiros, ferreiros de armas e armeiros só trabalham **um dia a cada 3**. Nos outros dias, dizem que estão de folga.
- **Falas de noite:** os aldeões, saqueadores e bruxas cumprimentam e se despedem de outro jeito à noite.
- **Uma curiosidade por dia:** cada NPC conta só uma história por dia. Para saber mais, converse com outros ou volte amanhã.
- **Recompensas com a cara de cada NPC:** no lugar do "Palavra de quem honra o que diz", cada personagem tem o próprio jeito de falar do pagamento.
- **Vilão da História para sempre:** com 100% de Vilania (todas as missões de vilão feitas), karma -1000 e o nível máximo, o efeito fica permanente.
- O Diário mostra distâncias em **blocos** nas missões de viagem.

### Mudanças
- **Herói e vilão equilibrados:** o lado vilão ganha os mesmos efeitos do herói, espelhados (Sorte I em -250, Velocidade I em -500 e Força I em -750 de karma) e não sofre mais com o Azar.
- **Desistir de uma missão de vilão dá karma** (+5) em vez de tirar.
- **Shulkers pagam melhor:** quase o dobro de diamantes, garrafas de experiência, pérolas e mais XP em todas as missões.
- A aba **Vilania** do Diário agora fica dentro da aba **Reputação**, logo abaixo do lado herói (role com o mouse). As missões de vilão aparecem em vermelho e as dos shulkers em roxo.
- O NPC agora **sorteia** a próxima missão entre as que você ainda não fez. Quando acabam as novas do seu nível, ele oferece alguma nova de um nível abaixo antes de repetir.
- O **Vilão da História** agora exige dominar saqueadores, piglins **e bruxas**.

## [1.1.0] - 2026-10-16

### Novidades
- **Saqueadores dão missões (15):** missões de vilania, com o ódio dos Exilados pelos aldeões: arrancar sinos, roubar colheitas, destruir camas, oficinas e golens, eliminar mercadores e pagar o tributo de esmeraldas. Elas dão **karma negativo**.
- **Vilania:** 5 níveis (Arruaceiro → Bandido → Renegado → Carrasco → Arquivilão), que sobem **só com missões** de saqueadores e piglins. A XP não conta e o nível nunca cai.
- **Efeito novo "Vilão da História":** para quem vira Arquivilão e domina saqueadores e piglins.
  - Todas as criaturas fogem de você, e os aldeões entram em pânico.
  - No mundo normal você fica com Fadiga I e, depois de 1 dia longe do Nether, pega fogo.
- **Shulkers dão missões (10):** no Fim, mais difíceis e com recompensas melhores, com falas próprias sobre o vazio e as cidades do Fim.
- **Falas por bioma:** aldeões de planície, deserto, savana, neve, taiga, selva e pântano têm histórias e problemas próprios.
- **Diário de Missões:** nova aba **Vilania**.
- Comando de teste `/cronicas admin vilania <1-5>`.

### Mudanças
- As missões dos piglins agora contam para a Vilania e não mexem mais no karma.
- O Herói da Vila exige karma não negativo, e o Vilão da História exige karma negativo.

## [1.0.0] - 2026-10-15

### Primeira versão
- 145 missões com história, de aldeões (13 profissões) e piglins, de Amador a Mestre.
- Caixa de diálogo no estilo Zelda: Breath of the Wild, com mouse e setas.
- NPC parado olhando para você e câmera travada durante a conversa.
- Karma com bênçãos e castigos, afinidade por profissão e Herói da Vila permanente.
- Diário de Missões (tecla J ou botão no inventário) e rastreador no HUD.

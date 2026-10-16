# Histórico de versões

A versão do mod fica em `gradle.properties`. Toda atualização com melhorias sobe o número do meio
(1.1.0 → 1.2.0 → 1.3.0...). Ao enviar para o branch `main`, o GitHub compila e publica a release
com o `.jar` automaticamente, usando as notas desta página.

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

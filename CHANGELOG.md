# Histórico de versões

A versão do mod fica em `gradle.properties`. Toda atualização com melhorias sobe o número do meio
(1.1.0 → 1.2.0 → 1.3.0...). Ao enviar para o branch `main`, o GitHub compila e publica a release
com o `.jar` automaticamente, usando as notas desta página.

## [1.2.0] - 2026-10-16

### Novidades
- **Bruxas dão missões (30):** a vida solitária no pântano, o gato Breu, as rãs, a irmã que mora em outro brejo e a mágoa de ter sido expulsa da vila. Para conversar, segure um **frasco de vidro**.
  - As missões das bruxas contam para a **Vilania** e não mexem no karma.
  - Com uma missão delas ativa, ou depois de completar uma, as bruxas param de te atacar.
- **Mais missões para os inimigos das vilas:** piglins, saqueadores e shulkers agora têm **30 missões cada** (6 por nível).
  - Piglins: passeios de lavagante, basalto, blazes, ghasts, sucata de netherita e o golpe contra os brutos.
  - Saqueadores: bestas, portas, tochas, estantes, plantações, oficinas e **invasões** começadas por você.
  - Shulkers: pedra do Fim, foguetes, voos de élitro, cristais do Fim, sopro do dragão e cabeças de dragão.
- O Diário mostra distâncias em **blocos** nas missões de viagem.

### Mudanças
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

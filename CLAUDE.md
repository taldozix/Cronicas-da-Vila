# Crônicas da Vila — regras do projeto

- Mod Fabric para Minecraft Java 26.3. Todos os textos do jogo ficam em português do Brasil.
- **Versão:** toda atualização com melhorias sobe a versão "do meio" em `gradle.properties`
  (1.1.0 → 1.2.0 → 1.3.0...). Ao mesmo tempo, adicione a seção `## [X.Y.0] - data` no topo do `CHANGELOG.md`
  e atualize o nome do `.jar` no README.
- **Release:** ao enviar para o `main`, o workflow `.github/workflows/build.yml` compila e, se a versão
  ainda não tiver tag, publica a release `vX.Y.Z` com o `.jar` e as notas do CHANGELOG. Não crie tags à mão.
- Chamadas ao Minecraft que mudam entre versões ficam em `src/main/java/br/com/cronicasdavila/compat/VanillaCompat.java`.

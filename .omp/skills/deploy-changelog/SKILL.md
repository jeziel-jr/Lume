---
name: deploy-changelog
description: Antes de qualquer deploy/release do Lume (push para main), gere o changelog da nova versão em português do Brasil e salve em changelogs/<versão>.md para ser usado como release notes do GitHub e exibido no app.
---

# Changelog de release (pt-BR)

## Quando usar

- Sempre que for realizar um deploy novo: preparar um push para `main` que deve virar release (o workflow `auto-beta-release.yml` publica uma beta em **todo** push para `main`).
- NÃO usar para pushes que não geram release (mensagem com `[skip release]`), nem executar depois do push: o changelog precisa entrar no **mesmo** push do deploy.

## Contexto da pipeline

1. Push para `main` → GitHub Actions `auto-beta-release.yml` roda `scripts/release_beta.py <próxima-versão> --publish`.
2. O script dá bump em `versionCode`/`versionName` no `app/build.gradle.kts`, builda o APK assinado, cria a tag e publica a GitHub Release.
3. **As release notes do GitHub = corpo da release**, e o dialog de atualização no app renderiza esse corpo (markdown) na TV.
4. Se existir `changelogs/<próxima-versão>.md` no checkout, o workflow passa `--custom-notes-file` ao script e o **arquivo vira o corpo inteiro** da release. Sem o arquivo, o script autogera bullets em inglês a partir dos assuntos dos commits.

## O que produzir

Arquivo `changelogs/<próxima-versão>.md` na raiz do repositório, em **português do Brasil**, claro para o usuário final da TV.

### Regras do texto

- Foco no que o usuário nota: novidades, correções de comportamento, melhorias de desempenho/navegação/visual.
- Usar apenas as seções que se aplicam, nesta ordem: `## Novidades`, `## Correções`, `## Melhorias`. Bullets curtos (uma linha), mais importante primeiro.
- Nunca inventar: cada bullet precisa ser suportado pelos commits do intervalo. Commit interno (refactor, CI, bump) → descreva o efeito perceptível ou omita.
- Excluir commits de bump/release (`release: …`), infra de CI e ajustes de changelog.
- Nomes próprios (TMDB, Xtream, Trakt, Fire TV) permanecem como estão.
- Abrir com `## Novidades da versão <versão>`.

### Template

```markdown
## Novidades da versão 0.7.36-beta

## Novidades
- …

## Correções
- …

## Melhorias
- …
```

## Passos

1. Leia a versão atual e a tag anterior:
   - versão atual: `versionName`/`versionCode` em `app/build.gradle.kts`
   - tag anterior: `git describe --tags --abbrev=0`
2. Calcule a próxima versão com a **mesma regra do workflow**: patch + 1 mantendo o sufixo (`0.7.35-beta` → `0.7.36-beta`). Se o usuário pretende outro esquema de versão, confirme antes.
3. Levante os commits do intervalo (mesma fonte do `release_beta.py`):
   `git log --reverse --pretty=format:%s <tag-anterior>..HEAD`
4. Entenda cada mudança antes de redigir: em commits ambíguos, leia a mensagem completa (`git log -1 --format=%B <sha>`) e, se ainda não estiver claro, `git show --stat <sha>`. Não navegue o projeto inteiro.
5. Redija o changelog conforme as regras e salve em `changelogs/<próxima-versão>.md` (crie o diretório se necessário).
6. Valide:
   - todo commit com efeito perceptível tem um bullet — confira com `git diff --stat <tag-anterior>..HEAD` para nada grande passar batido;
   - referência: `python3 scripts/release_beta.py <próxima-versão> --dry-run` imprime o que o autogerado (EN) conteria — use como checklist, não como tradução;
   - texto 100% pt-BR, sem jargão interno (versionCode, bump, R8, workflow);
   - nenhum bullet sem suporte nos commits.
7. Informe o usuário: caminho do arquivo e que ele precisa entrar no **mesmo commit/push** do deploy (senão a release sai com notas autogeradas em inglês).

## Exemplo (intervalo enxuto)

Commits desde a última tag:

- `feat: add continue watching row` → "Nova fileira 'Continuar assistindo' na Home."
- `fix: keep focus on shimmer rows` → "Correção do foco ao carregar as fileiras da Home."
- `chore(deps): bump coil` → omitido (sem efeito perceptível).

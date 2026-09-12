---
name: dns-rotation
description: Use quando o usuário precisar trocar o DNS/endereço do provedor Xtream no Lume (domínio bloqueado, queimado ou migração de painel) — "preciso trocar o dns", "mudar o endereço do servidor", "o domínio caiu", "trocar o servidor". Pergunta o novo endereço caso ele não tenha sido informado e executa o processo completo: valida o endereço, sobe a revision, assina, publica e confere a propagação.
---

# Troca de DNS (endpoint do provedor Xtream)

O endereço do provedor não está no APK: vive em `remote-config/xtream.json` (documento assinado,
publicado no repositório e baixado pelo app). Trocar o DNS é editar esse arquivo, **assinar** e
publicar — sem release, sem rebuild e sem ação do usuário.

## Quando usar

- Pedidos de troca/migração de endereço: "preciso trocar o dns", "mudar o servidor do painel",
  "o domínio foi bloqueado/caiu", "colocar um domínio reserva".
- NÃO usar para: atualização do app (isso é release/updater), troca de usuário ou senha do
  Xtream (o usuário faz pelo setup), troca da `BOOTSTRAP_ENDPOINT` compilada (exige release).

## Passo 0 — coletar o endereço (obrigatório)

Se o novo endereço **não** veio na mensagem, pergunte antes de fazer qualquer coisa:

> Qual o novo endereço do provedor? (ex.: `http://novo-dominio.xyz` ou `https://novo-dominio.xyz`)

Se o usuário não informar o esquema (`http`/`https`), confirme — http só é aceito por decisão
consciente, porque expõe usuário e senha na rede. Assuma, sem perguntar, que o endereço atual
continua na lista como reserva (fallback); só remova com `--no-fallback` se ele pedir.

## Processo

1. **Rodar o script** (ele valida, sobe a revision, escreve o JSON, assina e confere):

   ```bash
   cd /Users/jeziel/www/Lume
   python3 scripts/rotate_xtream_dns.py http://novo-dominio.xyz          # https dispensa --allow-insecure
   python3 scripts/rotate_xtream_dns.py http://novo-dominio.xyz --allow-insecure   # http:// exige a flag
   ```

   O script testa o `player_api.php` do endereço novo; se não responder daqui e você tiver certeza
   de que responde para os usuários, repita com `--force`. Use `--dry-run` para mostrar o plano.

2. **Revisar o resultado**: `cat remote-config/xtream.json` — o novo endereço em primeiro,
   `revision` = anterior + 1, `issuedAt` atualizado, endereço atual como fallback. A assinatura
   `.sig` precisa ter sido regravada no mesmo passo (o script faz isso e imprime "assinatura válida").

3. **Publicar** (o `[skip release]` é obrigatório: config não deve gerar release beta):

   ```bash
   git add remote-config/xtream.json remote-config/xtream.json.sig
   git commit -m "chore: rotate xtream endpoint to novo-dominio.xyz [skip release]"
   git push
   ```

4. **Conferir a propagação** (raw atualiza em ~5 min; o espelho jsDelivr tem cache longo e só é
   usado quando o raw falha):

   ```bash
   curl -s "https://raw.githubusercontent.com/jeziel-jr/Lume/main/remote-config/xtream.json?cb=$(date +%s)"
   curl -s "https://cdn.jsdelivr.net/gh/jeziel-jr/Lume@main/remote-config/xtream.json"
   ```

   Os dois têm que mostrar a `revision` nova.

5. **Aplicar/validar em aparelho** (a TV aplica no próximo boot, no TTL de 6 h ou na primeira falha
   de conexão; para forçar agora: Ajustes → Conta → "Atualizar endereço do servidor"):

   ```bash
   adb logcat -s LumeEndpoint     # esperado: remote_config_applied revision=N e endpoint_switch from=… to=…
   ```

6. **Reportar**: endereço novo, `revision` publicada, fallback mantido, status da propagação e a
   receita de reversão (o script imprime: republicar a lista anterior com `revision` + 1).

## Regras de ouro

1. Editou o JSON → **assine de novo**. O `.sig` cobre os bytes exatos; qualquer espaço muda tudo.
2. Nunca suba o `.sig` sem o `.json` correspondente no mesmo commit.
3. `revision` só aumenta. Reverter é publicar a lista antiga com `revision` maior.
4. Nunca commite a chave privada (`~/.lume/config-signing/xtream-config.key`) nem a coloque no repo.
5. Commit de config leva `[skip release]`.
6. `http://` só com `--allow-insecure` e com o risco explicitado ao usuário (credenciais em claro).
7. Sempre que existir um `https://` válido, mantenha-o como fallback na lista.
8. Quem edita servidor é você (operador): o usuário só informa usuário e senha no setup.

## Casos especiais

- **Domínio novo não responde da máquina do operador** → `--force` (o app ainda valida antes de
  trocar: sem credenciais por vivacidade, com credenciais exigindo `auth = 1`).
- **Reverter** → rodar o script de novo apontando para o endereço antigo (a `revision` sobe; nunca
  publique uma revisão menor, o app ignora).
- **Testar um endereço só na sua TV** antes de publicar → gesto oculto no setup (5× OK < 1,2 s)
  revela `Servidor (manual)`; Ajustes → Conta mostra "Servidor manual" e permite voltar ao padrão.
- **Chave privada perdida** → não há rotação por config; ver §9 do runbook (exige release com nova
  chave embutida).
- **Endereço definitivo mudou** → além da config, considere atualizar a constante compilada
  `XtreamEndpointResolver.BOOTSTRAP_ENDPOINT` (usada só no primeiro boot sem config) — isso exige
  release e um changelog.

## Arquivos

| Arquivo | Papel |
| --- | --- |
| `remote-config/xtream.json` + `.sig` | documento publicado (versionado, sem segredos) |
| `scripts/rotate_xtream_dns.py` | executa a troca (valida, revision, JSON, assinatura, verificação) |
| `scripts/sign_remote_config.py` | assinatura/verificação/chave (`--genkey`, `--verify`, `--print-pubkey`) |
| `REMOTE_ENDPOINT_RUNBOOK.local.md` | runbook completo do operador (local, fora do git) |
| `app/src/main/java/com/nuvio/tv/data/xtream/XtreamEndpointResolver.kt` | aplica a config no app |

## Checklist final

- [ ] `revision` maior que a publicada e `endpoints` com o novo endereço em primeiro.
- [ ] `assinatura válida` impressa e `.sig` commitado junto do `.json`.
- [ ] Push feito com `[skip release]` e sem run de release disparado.
- [ ] Raw e espelho mostrando a `revision` nova.
- [ ] Usuário avisado de que a troca vale na próxima abertura (ou já pela ação em Ajustes → Conta).

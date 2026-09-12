# remote-config

Endereço do provedor Xtream que o Lume usa em todos os aparelhos, publicado fora do APK para
permitir troca de domínio sem release.

## Arquivos

- `xtream.json` — documento assinado, lido pelo app.
- `xtream.json.sig` — assinatura ECDSA P-256 (SHA-256, DER, base64) dos **bytes exatos** do JSON.

O app baixa os dois de `raw.githubusercontent.com/<owner>/<repo>/main/remote-config/...` (primário)
e de `cdn.jsdelivr.net/gh/<owner>/<repo>@main/remote-config/...` (espelho). Uma configuração só é
aceita se a assinatura conferir com uma chave pública embutida em
`RemoteConfigKeys.kt`; qualquer outra coisa é descartada e o aparelho mantém o endereço anterior.

## Formato

```json
{
  "schema": 1,
  "revision": 1,
  "keyId": "k1",
  "issuedAt": "2026-09-12T00:00:00Z",
  "endpoints": ["http://capone.icu", "https://capone.icu"]
}
```

- `revision`: inteiro **crescente**. O app ignora revisão menor ou igual à última aplicada, o que
  impede o espelho (cache maior) de reverter uma rotação. Para reverter de propósito, publique a
  lista antiga com `revision` maior.
- `endpoints`: ordem de prioridade. O primeiro é o principal; os demais entram como fallback quando
  o atual não responde. Apenas `http://` e `https://`. HTTP é aceito porque o documento é assinado —
  a assinatura impede que um host não aprovado seja injetado —, mas nesse caso usuário e senha
  trafegam em claro na rede; por isso o script exige `--allow-insecure` para assinar uma
  configuração com endpoint sem TLS.
- `keyId`: identificador da chave que assinou, entre as aceitas em `RemoteConfigKeys.kt`.

## Assinatura

```bash
python3 scripts/sign_remote_config.py --genkey          # uma vez: cria o par de chaves
python3 scripts/sign_remote_config.py                   # assina remote-config/xtream.json
python3 scripts/sign_remote_config.py --verify          # confere a assinatura publicada
python3 scripts/sign_remote_config.py --print-pubkey    # chave pública para embutir no APK
```

A chave privada fica fora do repositório (`~/.lume/config-signing/xtream-config.key`, chmod 600) e
precisa de backup: sem ela não há como publicar novas configurações para os aparelhos já instalados.

O passo a passo operacional completo (rotação, rollback, teste em aparelho próprio, diagnóstico)
está em `REMOTE_ENDPOINT_RUNBOOK.local.md`, na raiz do repositório, que é ignorado pelo git de
propósito por conter detalhes de operação.

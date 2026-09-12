#!/usr/bin/env python3
"""Rotaciona o endereço (DNS) do provedor Xtream no Lume, em um comando.

Faz o processo inteiro, menos o git (que fica impresso para revisão):

1. valida o endereço novo (esquema e, por padrão, se ele responde);
2. sobe a `revision` e monta a lista com o novo endereço em primeiro e o atual como fallback;
3. assina (`remote-config/xtream.json.sig`) e confere a assinatura.

Uso:
    python3 scripts/rotate_xtream_dns.py http://novo.dominio
    python3 scripts/rotate_xtream_dns.py https://novo.dominio --no-fallback
    python3 scripts/rotate_xtream_dns.py http://novo.dominio --dry-run
    python3 scripts/rotate_xtream_dns.py http://192.168.0.10:8080 --allow-insecure --force

Depois do script: commit das duas pontas (`xtream.json` + `.sig`) com `[skip release]` e push.
O passo a passo operacional completo está em `REMOTE_ENDPOINT_RUNBOOK.local.md`.
"""

from __future__ import annotations

import argparse
import json
import socket
import ssl
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CONFIG = REPO_ROOT / "remote-config" / "xtream.json"
DEFAULT_KEY = Path.home() / ".lume" / "config-signing" / "xtream-config.key"
SIGN_SCRIPT = REPO_ROOT / "scripts" / "sign_remote_config.py"
PROBE_TIMEOUT_SECONDS = 10
INVALID_CERTIFICATE_MARKER = "certificado não validado"


def normalize_url(value: str) -> str:
    return value.strip().rstrip("/")


def validate_scheme(endpoint: str, allow_insecure: bool) -> None:
    if not endpoint.startswith(("http://", "https://")):
        raise SystemExit(f"endereço inválido (use http:// ou https://): {endpoint!r}")
    if endpoint.startswith("http://") and not allow_insecure:
        raise SystemExit(
            f"endereço sem TLS: {endpoint!r}\n"
            "O app aceita http:// em configuração assinada, mas nesse caso usuário e senha "
            "trafegam em claro na rede. Repita com --allow-insecure para confirmar."
        )


def probe(endpoint: str) -> str:
    """Requer `player_api.php` e descreve o que o host respondeu."""
    url = f"{endpoint}/player_api.php"
    context = ssl._create_unverified_context()  # painéis XUI costumam usar certificado próprio
    request = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=PROBE_TIMEOUT_SECONDS, context=context) as response:
            return f"HTTP {response.status}"
    except urllib.error.HTTPError as error:
        return f"HTTP {error.code}"
    except ssl.SSLCertVerificationError:
        return INVALID_CERTIFICATE_MARKER
    except (urllib.error.URLError, socket.timeout, OSError) as error:
        return f"sem resposta ({error})"


def reachable(result: str) -> bool:
    return not result.startswith("sem resposta")


def build_endpoints(new_endpoint: str, current: list[str], keep_fallback: bool, maximum: int) -> list[str]:
    ordered = [new_endpoint]
    if keep_fallback:
        ordered += [endpoint for endpoint in current if endpoint != new_endpoint]
    return ordered[:maximum]


def sign(config_path: Path, key_path: Path, allow_insecure: bool) -> None:
    args = [
        sys.executable,
        str(SIGN_SCRIPT),
        "--config",
        str(config_path),
        "--sig",
        signature_path(config_path),
        "--key",
        str(key_path),
    ]
    if allow_insecure:
        args.append("--allow-insecure")
    subprocess.run(args, check=True, cwd=REPO_ROOT)


def signature_path(config_path: Path) -> str:
    return f"{config_path}.sig"


def verify(config_path: Path, key_path: Path) -> None:
    subprocess.run(
        [
            sys.executable,
            str(SIGN_SCRIPT),
            "--config",
            str(config_path),
            "--sig",
            signature_path(config_path),
            "--key",
            str(key_path),
            "--verify",
        ],
        check=True,
        cwd=REPO_ROOT,
    )


def git_instructions(config_path: Path, endpoints: list[str], revision: int, previous: list[str]) -> str:
    inside_repo = config_path.is_relative_to(REPO_ROOT)
    relative = config_path.relative_to(REPO_ROOT) if inside_repo else config_path
    revert_list = ", ".join(f'"{endpoint}"' for endpoint in previous)
    lines = [
        "",
        "Próximos passos (git):",
        f'  git add {relative} {relative}.sig',
        f'  git commit -m "chore: rotate xtream endpoint to {endpoints[0]} [skip release]"',
        "  git push",
        "",
    ]
    if inside_repo:
        lines += [
            "Conferir a propagação (≤5 min no raw; o espelho jsDelivr tem cache longo):",
            f'  curl -s "https://raw.githubusercontent.com/jeziel-jr/Lume/main/{relative}?cb=$(date +%s)" | head -20',
            "",
        ]
    lines += [
        f"Reverter (revision {revision + 1} com a lista anterior):",
        f'  editar {relative} para "endpoints": [{revert_list}] e "revision": {revision + 1}, reassinar e publicar',
        "",
        "Aplicar na TV agora: Ajustes → Conta → \"Atualizar endereço do servidor\" "
        "e acompanhar com `adb logcat -s LumeEndpoint`.",
    ]
    return "\n".join(lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("endpoint", help="novo endereço do provedor (http:// ou https://)")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG, help=f"JSON (padrão: {DEFAULT_CONFIG})")
    parser.add_argument("--key", type=Path, default=DEFAULT_KEY, help=f"chave privada (padrão: {DEFAULT_KEY})")
    parser.add_argument("--no-fallback", action="store_true", help="não mantém o endereço atual como reserva")
    parser.add_argument("--max-endpoints", type=int, default=3, help="máximo de endereços na lista (padrão: 3)")
    parser.add_argument("--allow-insecure", action="store_true", help="confirma um endereço http:// (sem TLS)")
    parser.add_argument("--no-probe", action="store_true", help="não testa se o endereço responde")
    parser.add_argument("--force", action="store_true", help="publica mesmo se o endereço não responder daqui")
    parser.add_argument("--dry-run", action="store_true", help="mostra o que seria feito e não escreve nada")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    endpoint = normalize_url(args.endpoint)
    validate_scheme(endpoint, args.allow_insecure)

    if not args.config.is_file():
        raise SystemExit(f"config não encontrada: {args.config}")
    document = json.loads(args.config.read_text(encoding="utf-8"))
    current = [normalize_url(item) for item in document.get("endpoints") or []]
    revision = int(document.get("revision") or 0)
    key_id = document.get("keyId") or "k1"

    if endpoint in current and current and current[0] == endpoint:
        print(f"{endpoint} já é o endereço principal (revision {revision}); nada a fazer")
        return

    result = "não testado" if args.no_probe else probe(endpoint)
    print(f"endereço novo: {endpoint}")
    print(f"resposta do painel: {result}")
    if not args.no_probe and not reachable(result) and not args.force:
        raise SystemExit(
            "o endereço não respondeu daqui. Se ele responde para os usuários, repita com --force."
        )

    endpoints = build_endpoints(endpoint, current, keep_fallback=not args.no_fallback, maximum=args.max_endpoints)
    next_revision = revision + 1
    print(f"revision: {revision} -> {next_revision}")
    print(f"endpoints: {current} -> {endpoints}")

    if args.dry_run:
        print("dry-run: nada foi escrito")
        return

    document["revision"] = next_revision
    document["issuedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    document["keyId"] = key_id
    document["endpoints"] = endpoints
    args.config.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    sign(args.config, args.key, allow_insecure=args.allow_insecure)
    verify(args.config, args.key)
    print(git_instructions(args.config, endpoints, next_revision, current))


if __name__ == "__main__":
    main()

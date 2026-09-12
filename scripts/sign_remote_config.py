#!/usr/bin/env python3
"""Assina o arquivo de configuração remota do Lume (endpoints do Xtream).

Uso:
    python3 scripts/sign_remote_config.py --genkey        # cria o par de chaves (uma vez)
    python3 scripts/sign_remote_config.py                 # assina remote-config/xtream.json
    python3 scripts/sign_remote_config.py --allow-insecure # assina aceitando endpoints http://
    python3 scripts/sign_remote_config.py --print-pubkey  # imprime a chave pública (para embutir no APK)
    python3 scripts/sign_remote_config.py --verify        # confere a assinatura publicada

A assinatura é ECDSA P-256 sobre SHA-256 no formato DER (o mesmo que
`Signature.getInstance("SHA256withECDSA")` espera no app), codificada em base64
em um arquivo `.sig` ao lado do JSON. A verificação no app é feita sobre os bytes
crus do JSON — nunca reformate o arquivo sem re-assinar.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CONFIG = REPO_ROOT / "remote-config" / "xtream.json"
DEFAULT_KEY = Path.home() / ".lume" / "config-signing" / "xtream-config.key"
DEFAULT_SIG = REPO_ROOT / "remote-config" / "xtream.json.sig"
PUBLIC_PEM = "xtream-config.pub.pem"


def openssl(*args: str, stdin: bytes | None = None) -> bytes:
    result = subprocess.run(
        ["openssl", *args],
        input=stdin,
        capture_output=True,
    )
    if result.returncode != 0:
        sys.stderr.write(result.stderr.decode("utf-8", "replace"))
        raise SystemExit(f"openssl falhou: {' '.join(args)}")
    return result.stdout


def generate_key(key_path: Path) -> None:
    if key_path.exists():
        raise SystemExit(f"chave já existe em {key_path} (não vou sobrescrever)")
    key_path.parent.mkdir(parents=True, exist_ok=True)
    openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(key_path))
    os.chmod(key_path, 0o600)
    public_pem = key_path.parent / PUBLIC_PEM
    openssl("ec", "-in", str(key_path), "-pubout", "-out", str(public_pem))
    print(f"chave privada: {key_path} (chmod 600 — guarde backup, sem ela não há rotação)")
    print(f"chave pública: {public_pem}")
    print(print_public_key(key_path))


def public_key_der_base64(key_path: Path) -> str:
    der = openssl("ec", "-in", str(key_path), "-pubout", "-outform", "DER")
    return base64.b64encode(der).decode("ascii")


def print_public_key(key_path: Path) -> str:
    if not key_path.exists():
        raise SystemExit(f"chave não encontrada em {key_path} (rode --genkey)")
    return f"SPKI DER base64:\n{public_key_der_base64(key_path)}"


def signature_base64(key_path: Path, payload: bytes) -> str:
    der = openssl("dgst", "-sha256", "-sign", str(key_path), stdin=payload)
    return base64.b64encode(der).decode("ascii")


def describe(config_path: Path) -> None:
    data = json.loads(config_path.read_text(encoding="utf-8"))
    revision = data.get("revision")
    schema = data.get("schema")
    endpoints = data.get("endpoints") or []
    print(f"schema={schema} revision={revision}")
    for index, endpoint in enumerate(endpoints):
        marker = "primário" if index == 0 else f"fallback {index}"
        print(f"  [{marker}] {endpoint}")


def sign(key_path: Path, config_path: Path, sig_path: Path, allow_insecure: bool = False) -> None:
    if not config_path.exists():
        raise SystemExit(f"config não encontrada: {config_path}")
    payload = config_path.read_bytes()
    config = json.loads(payload.decode("utf-8"))
    if not isinstance(config.get("revision"), int) or config["revision"] < 1:
        raise SystemExit("revision precisa ser um inteiro >= 1 (a revisão é monotônica e anti-rollback)")
    endpoints = config.get("endpoints") or []
    if not isinstance(endpoints, list) or not endpoints:
        raise SystemExit("endpoints precisa ser uma lista não vazia")
    for endpoint in endpoints:
        if not isinstance(endpoint, str) or not endpoint.startswith(("http://", "https://")):
            raise SystemExit(f"endpoint inválido (use http:// ou https://): {endpoint!r}")
        if endpoint.startswith("http://") and not allow_insecure:
            raise SystemExit(
                f"endpoint sem TLS: {endpoint!r}\n"
                "O app aceita http:// em configuração assinada, mas nesse caso usuário e senha "
                "trafegam em claro na rede. Repita com --allow-insecure para confirmar."
            )

    signature = signature_base64(key_path, payload)
    sig_path.write_text(signature + "\n", encoding="utf-8")
    print(f"assinado: {config_path} -> {sig_path}")
    describe(config_path)


def verify(key_path: Path, config_path: Path, sig_path: Path) -> None:
    payload = config_path.read_bytes()
    signature_der = base64.b64decode(sig_path.read_text(encoding="utf-8").strip())
    tmp_signature = sig_path.with_suffix(".sig.tmp")
    tmp_signature.write_bytes(signature_der)
    public_pem = key_path.parent / PUBLIC_PEM
    try:
        openssl(
            "dgst",
            "-sha256",
            "-verify",
            str(public_pem),
            "-signature",
            str(tmp_signature),
            stdin=payload,
        )
    finally:
        tmp_signature.unlink(missing_ok=True)
    print("assinatura válida")
    describe(config_path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--key", type=Path, default=DEFAULT_KEY, help=f"chave privada (padrão: {DEFAULT_KEY})")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG, help=f"JSON (padrão: {DEFAULT_CONFIG})")
    parser.add_argument("--sig", type=Path, default=DEFAULT_SIG, help=f"assinatura (padrão: {DEFAULT_SIG})")
    parser.add_argument("--genkey", action="store_true", help="gera o par de chaves e sai")
    parser.add_argument("--print-pubkey", action="store_true", help="imprime a chave pública em SPKI DER base64")
    parser.add_argument("--verify", action="store_true", help="verifica a assinatura publicada")
    parser.add_argument(
        "--allow-insecure",
        action="store_true",
        help="confirma endpoints http:// (sem TLS) na configuração",
    )
    args = parser.parse_args()

    if args.genkey:
        generate_key(args.key)
        return
    if args.print_pubkey:
        print(print_public_key(args.key))
        return
    if args.verify:
        verify(args.key, args.config, args.sig)
        return
    sign(args.key, args.config, args.sig, allow_insecure=args.allow_insecure)


if __name__ == "__main__":
    main()

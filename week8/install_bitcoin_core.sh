#!/usr/bin/env bash
# week8/install_bitcoin_core.sh
# Installs Bitcoin Core daemons and CLI (bitcoind, bitcoin-cli) on Ubuntu/Debian.
# Requires sudo privileges for package installation.

set -euo pipefail

bold() { printf "\033[1m%s\033[0m\n" "$*"; }

need_sudo=true
if [ "${EUID:-$(id -u)}" -eq 0 ]; then need_sudo=false; fi
SUDO="sudo"
$need_sudo || SUDO=""

bold "[1/4] Checking existing installation..."
if command -v bitcoind >/dev/null 2>&1 && command -v bitcoin-cli >/dev/null 2>&1; then
  echo "✓ Bitcoin Core already installed: $(bitcoind -version | head -n1)"
  exit 0
fi

bold "[2/4] Preparing prerequisites (curl, tar, sha256sum)..."
# Ensure curl and tar exist; attempt install if missing
ensure_dep() { command -v "$1" >/dev/null 2>&1 || ($SUDO apt-get update -y && $SUDO apt-get install -y "$2"); }
ensure_dep curl curl
ensure_dep tar tar
ensure_dep sha256sum coreutils

bold "[3/4] Downloading official Bitcoin Core tarball..."
# Allow overrides
BITCOIN_VER="${BITCOIN_VER:-27.0}"
DOWNLOAD_DIR="${DOWNLOAD_DIR:-$HOME/downloads}"
mkdir -p "$DOWNLOAD_DIR"

ARCH=$(uname -m)
case "$ARCH" in
  x86_64|amd64)  PLATFORM="x86_64-linux-gnu" ;;
  aarch64|arm64) PLATFORM="aarch64-linux-gnu" ;;
  *) echo "❌ Unsupported architecture: $ARCH"; exit 1 ;;
esac

BASE_URL="https://bitcoincore.org/bin/bitcoin-core-${BITCOIN_VER}"
TARBALL="bitcoin-${BITCOIN_VER}-${PLATFORM}.tar.gz"

cd "$DOWNLOAD_DIR"
echo "Fetching: $BASE_URL/$TARBALL"
curl -fLO "$BASE_URL/$TARBALL"
echo "Fetching: $BASE_URL/SHA256SUMS{,.asc}"
curl -fLO "$BASE_URL/SHA256SUMS" || true
curl -fLO "$BASE_URL/SHA256SUMS.asc" || true

# Optional verification (SHA256); set VERIFY_SHA256=0 to skip
: "${VERIFY_SHA256:=1}"
if [ "$VERIFY_SHA256" = "1" ] && [ -f SHA256SUMS ]; then
  echo "Verifying SHA256..."
  grep "$TARBALL" SHA256SUMS | sha256sum -c -
fi

bold "[4/4] Installing binaries to /usr/local/bin ..."
tar -xzf "$TARBALL"
DIRNAME="bitcoin-${BITCOIN_VER}"
[ -d "$DIRNAME/bin" ] || DIRNAME=$(find . -maxdepth 1 -type d -name "bitcoin-*" | head -n1)
[ -d "$DIRNAME/bin" ] || { echo "❌ Unexpected archive layout"; exit 1; }

$SUDO install -m 0755 "$DIRNAME/bin/bitcoind" /usr/local/bin/bitcoind
$SUDO install -m 0755 "$DIRNAME/bin/bitcoin-cli" /usr/local/bin/bitcoin-cli
$SUDO install -m 0755 "$DIRNAME/bin/bitcoin-tx" /usr/local/bin/bitcoin-tx || true

bold "Verifying installation..."
command -v bitcoind >/dev/null 2>&1 || { echo "❌ bitcoind not found after installation"; exit 1; }
command -v bitcoin-cli >/dev/null 2>&1 || { echo "❌ bitcoin-cli not found after installation"; exit 1; }

bitcoind -version | head -n1
bitcoin-cli -version | head -n1

echo "\n✅ Bitcoin Core installed successfully."

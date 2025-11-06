#!/usr/bin/env bash
# week8/regtest_mining_two_nodes.sh
# Demonstrates Bitcoin mining and committing a transaction on regtest with two isolated nodes.
# - Node A (honest) on RPC 18443, P2P 18444
# - Node B (attacker/private) on RPC 18453, P2P 18454
#
# This script:
# 1) Starts both nodes (disconnected by default)
# 2) Creates wallets and addresses on each
# 3) Mines 101 blocks on each so coinbases are spendable
# 4) Sends 1 BTC from A -> B and mines 1 block on A to confirm
# 5) Prints the gettransaction output

set -euo pipefail

RPCUSER="user"
RPCPASS="pass"
DATA_A="$HOME/regtestA"
DATA_B="$HOME/regtestB"
RPC_A=18443
P2P_A=18444
RPC_B=18453
P2P_B=18454

bold() { printf "\033[1m%s\033[0m\n" "$*"; }

require() { command -v "$1" >/dev/null 2>&1 || { echo "❌ Missing dependency: $1"; echo "Run: week8/install_bitcoin_core.sh"; exit 1; }; }

require bitcoind
require bitcoin-cli

cleanup() {
  echo "\nStopping regtest nodes..."
  set +e
  bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" stop >/dev/null 2>&1
  bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" stop >/dev/null 2>&1
  sleep 2
}
trap cleanup EXIT

bold "[1/6] Starting Node A (regtest)"
mkdir -p "$DATA_A"
bitcoind -regtest -datadir="$DATA_A" -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
  -rpcport=$RPC_A -port=$P2P_A \
  -fallbackfee=0.0002 -paytxfee=0.0001 -daemon

bold "[2/6] Starting Node B (regtest)"
mkdir -p "$DATA_B"
bitcoind -regtest -datadir="$DATA_B" -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
  -rpcport=$RPC_B -port=$P2P_B \
  -fallbackfee=0.0002 -paytxfee=0.0001 -daemon

bold "[3/6] Waiting for RPC to be ready..."
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" -rpcwait getblockchaininfo >/dev/null
bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" -rpcwait getblockchaininfo >/dev/null

echo "RPC ready on both nodes."

bold "[4/6] Creating wallets and addresses..."

ensure_wallet() {
  local DATA_DIR="$1" RPC_PORT="$2" WALLET_NAME="$3"
  # If a wallet is already loaded, this succeeds
  if bitcoin-cli -regtest -datadir="$DATA_DIR" -rpcport="$RPC_PORT" -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
       getwalletinfo >/dev/null 2>&1; then
    return 0
  fi
  # Try loading an existing wallet by name
  if bitcoin-cli -regtest -datadir="$DATA_DIR" -rpcport="$RPC_PORT" -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
       loadwallet "$WALLET_NAME" >/dev/null 2>&1; then
    return 0
  fi
  # Create the wallet if it doesn't exist, it will auto-load
  bitcoin-cli -regtest -datadir="$DATA_DIR" -rpcport="$RPC_PORT" -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
    createwallet "$WALLET_NAME" >/dev/null
}

ensure_wallet "$DATA_A" "$RPC_A" "walletA"
ensure_wallet "$DATA_B" "$RPC_B" "walletB"

addrA=$(bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getnewaddress)
addrB=$(bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getnewaddress)

echo "Node A address: $addrA"
echo "Node B address: $addrB"

bold "[5/6] Mining 101 blocks on each node..."
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
  generatetoaddress 101 "$addrA" >/dev/null
bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
  generatetoaddress 101 "$addrB" >/dev/null

echo "Balances after coinbase maturity:"
echo -n " Node A: "
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getbalance

echo -n " Node B: "
bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getbalance

bold "[6/6] Sending 1 BTC from Node A → Node B and confirming..."
txid=$(bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
  sendtoaddress "$addrB" 1)

# Mine 1 block on Node A to confirm
echo "Mined block on Node A to confirm tx..."
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
  generatetoaddress 1 "$addrA" >/dev/null

bold "Transaction details (paste in report):"
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
  gettransaction "$txid"

echo "\nTip: You can re-run this script, it is idempotent: wallets are reused and daemons are restarted if needed."

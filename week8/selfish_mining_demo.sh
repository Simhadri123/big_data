#!/usr/bin/env bash
# week8/selfish_mining_demo.sh
# Simulate selfish mining behavior on regtest:
# - B withholds 2 private blocks
# - A mines 1 block
# - Connect nodes; B's chain (length 2) overtakes A's chain (length 1), orphaning A's block

set -euo pipefail

RPCUSER="user"
RPCPASS="pass"
DATA_A="$HOME/regtestASelfish"
DATA_B="$HOME/regtestBSelfish"
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

start_node() {
  local DATA_DIR="$1" RPC_PORT="$2" P2P_PORT="$3"
  mkdir -p "$DATA_DIR"
  bitcoind -regtest -datadir="$DATA_DIR" -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" \
    -rpcport="$RPC_PORT" -port="$P2P_PORT" \
    -fallbackfee=0.0002 -paytxfee=0.0001 -daemon
}

ensure_wallet() {
  local DATA_DIR="$1" RPC_PORT="$2" WALLET_NAME="$3"
  if bitcoin-cli -regtest -datadir="$DATA_DIR" -rpcport="$RPC_PORT" -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getwalletinfo >/dev/null 2>&1; then
    return 0
  fi
  bitcoin-cli -regtest -datadir="$DATA_DIR" -rpcport="$RPC_PORT" -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" loadwallet "$WALLET_NAME" >/dev/null 2>&1 || \
  bitcoin-cli -regtest -datadir="$DATA_DIR" -rpcport="$RPC_PORT" -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" createwallet "$WALLET_NAME" >/dev/null
}

bold "[1/6] Starting nodes (disconnected)"
start_node "$DATA_A" $RPC_A $P2P_A
start_node "$DATA_B" $RPC_B $P2P_B

bold "[2/6] Waiting for RPC..."
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" -rpcwait getblockchaininfo >/dev/null
bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" -rpcwait getblockchaininfo >/dev/null

echo "RPC ready on both nodes."

bold "[3/6] Wallets and addresses"
ensure_wallet "$DATA_A" $RPC_A "walletA"
ensure_wallet "$DATA_B" $RPC_B "walletB"
addrA=$(bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getnewaddress)
addrB=$(bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getnewaddress)

echo "Node A address: $addrA"
echo "Node B address: $addrB"

bold "[4/6] Mine maturity blocks (A and B)"
# Only A needs coins to show mining; mine 101 on both for symmetry
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" generatetoaddress 101 "$addrA" >/dev/null
bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" generatetoaddress 101 "$addrB" >/dev/null

bold "[5/6] Selfish sequence: B mines 2 (private), A mines 1"
bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" generatetoaddress 2 "$addrB" >/dev/null
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" generatetoaddress 1 "$addrA" >/dev/null

bold "[6/6] Connect A -> B; B's chain should win (A's last block orphaned)"
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" addnode "127.0.0.1:$P2P_B" add
sleep 2

echo "Chain tips on A after connection:"
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getchaintips

echo "\nDone. (Nodes will stop automatically)"

#!/usr/bin/env bash
# week8/attack_50_percent_reorg.sh
# Demonstrate a 50% attack scenario on regtest by mining a longer private chain and triggering a reorg.
# Sequence:
# 1) Start Node A and Node B (disconnected)
# 2) Create wallets, addresses; mine 101 blocks on each
# 3) Node A sends 1 BTC to Node B and mines 1 block (tx gets 1 confirmation on A's chain)
# 4) Node B privately mines 7 blocks (longer chain than A)
# 5) Connect nodes (A -> B), observe reorg; victim tx loses confirmations

set -euo pipefail

RPCUSER="user"
RPCPASS="pass"
DATA_A="$HOME/regtestA50"
DATA_B="$HOME/regtestB50"
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

bold "[4/6] Mining maturity blocks (101 each)"
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" generatetoaddress 101 "$addrA" >/dev/null
bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" generatetoaddress 101 "$addrB" >/dev/null

echo -n "Balances A/B: "
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getbalance | tr -d '\n'
echo -n " / "
bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getbalance

bold "[5/6] Victim tx on A, confirm with 1 block"
tx_victim=$(bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" sendtoaddress "$addrB" 1)
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" generatetoaddress 1 "$addrA" >/dev/null

echo "Victim tx: $tx_victim"
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" gettransaction "$tx_victim"

bold "Attacker mines 7 private blocks on B"
bitcoin-cli -regtest -datadir="$DATA_B" -rpcport=$RPC_B -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" generatetoaddress 7 "$addrB" >/dev/null

bold "[6/6] Connect A -> B to trigger reorg"
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" addnode "127.0.0.1:$P2P_B" add
sleep 2

echo "Chain tips on A after connection:"
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" getchaintips

echo "Victim tx status after reorg (should drop confirmations or be 0):"
bitcoin-cli -regtest -datadir="$DATA_A" -rpcport=$RPC_A -rpcuser="$RPCUSER" -rpcpassword="$RPCPASS" gettransaction "$tx_victim"

echo "\nDone. (Nodes will stop automatically)"

# Week 8 – Bitcoin RegTest Demo (Task 1)

This folder contains a minimal, copy‑pasteable setup to demonstrate Bitcoin mining on RegTest and committing a transaction (two‑node setup).

## Files

- `install_bitcoin_core.sh` — Installs Bitcoin Core (`bitcoind`, `bitcoin-cli`) on Ubuntu/Debian via apt.
- `regtest_mining_two_nodes.sh` — Starts two regtest nodes (A and B), creates wallets/addresses, mines 101 blocks on each, sends 1 BTC from A → B, mines 1 block to confirm, and prints `gettransaction` output.
- `attack_50_percent_reorg.sh` — Demonstrates a reorg/double-spend scenario: victim tx on A (1 conf), B privately mines longer chain, connect -> reorg, tx loses confirmations.
- `selfish_mining_demo.sh` — Simulates selfish mining: B withholds 2 blocks, A mines 1, connect -> A’s block gets orphaned.

## Prerequisites

- Linux (Ubuntu/Debian recommended)
- sudo privileges for package installation

## 1) Install required packages

```bash
cd week8
chmod +x install_bitcoin_core.sh
./install_bitcoin_core.sh
```

This installs `bitcoind` and `bitcoin-cli`. If your distro doesn’t have these packages, follow official instructions: https://bitcoincore.org/en/download/

## 2) Run the RegTest demo (two nodes)

```bash
cd week8
chmod +x regtest_mining_two_nodes.sh
./regtest_mining_two_nodes.sh
```

What it does:
- Starts Node A (RPC 18443, P2P 18444) and Node B (RPC 18453, P2P 18454)
- Creates `walletA` on A and `walletB` on B
- Mines 101 blocks on each so coinbases mature
- Sends 1 BTC from A → B, mines 1 block on A to confirm
- Prints `bitcoin-cli gettransaction <txid>` from Node A — paste this into your report

Nodes are stopped automatically when the script exits.

## Notes

- Nodes are intentionally disconnected (no `addnode`) for this task.
- You can change RPC creds/ports by editing variables at the top of the script.
- If you re‑run, the script is idempotent: it will reuse wallets and addresses.

---

## 3) 50% attack (reorg + double-spend demo)

```bash
cd week8
chmod +x attack_50_percent_reorg.sh
./attack_50_percent_reorg.sh
```

What to paste:
- Output of `getchaintips` after connection
- `gettransaction <txid>` showing confirmations drop to 0 (or removed)

---

## 4) Selfish mining demo

```bash
cd week8
chmod +x selfish_mining_demo.sh
./selfish_mining_demo.sh
```

What to paste:
- Output of `getchaintips` after connection showing attacker chain active

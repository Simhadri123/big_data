# ghost_sim.py -- toy simulation comparing longest-chain vs a simple GHOST choice
import random
import sys
from collections import defaultdict, Counter

random.seed(0)
sys.setrecursionlimit(10000)

def simulate_selfish_simple(rounds=20000, attacker_power=0.35):
    lead = 0
    attacker_blocks = 0
    total_blocks = 0
    for _ in range(rounds):
        if random.random() < attacker_power:
            lead += 1
        else:
            # honest finds
            if lead == 0:
                total_blocks += 1
            elif lead == 1:
                # publish race: 50% attacker wins
                if random.random() < 0.5:
                    attacker_blocks += 1
                total_blocks += 1
                lead = 0
            else:
                # lead >=2 attacker publishes one and keeps lead-1
                attacker_blocks += 1
                total_blocks += 1
                lead -= 1
    attacker_blocks += lead
    total_blocks += lead
    return attacker_blocks / total_blocks

def toy_chain_compare(attacker_power=0.35, steps=1500):
    # very small toy to compare fraction of attacker blocks on chosen main chain
    # We'll simulate blocks and choose tips by longest vs by a heuristic (heaviest subtree)
    class B:
        def __init__(self, id, parent, owner):
            self.id = id; self.parent = parent; self.children = []; self.owner = owner
    blocks = {0: B(0, None, 'G')}
    tips = [0]
    heights = {0: 0}
    next_id = 1
    for _ in range(steps):
        miner = 'A' if random.random() < attacker_power else 'H'
        # longest: pick highest tip
        max_h = max(heights[t] for t in tips)
        candidates = [t for t in tips if heights[t] == max_h]
        parent_long = random.choice(candidates)
        # add block for longest
        b = B(next_id, parent_long, miner)
        blocks[next_id] = b
        blocks[parent_long].children.append(b)
        heights[next_id] = heights[parent_long] + 1
        if parent_long in tips:
            tips.remove(parent_long)
        tips.append(next_id)
        next_id += 1
    # main chain for longest:
    # choose one of highest tips
    tip_long = max(heights, key=lambda k: heights[k])
    chain = set()
    cur = tip_long
    while cur is not None:
        chain.add(cur)
        cur = blocks[cur].parent
    attacker_on_main = sum(1 for b in chain if blocks[b].owner == 'A')
    frac_long = attacker_on_main / len(chain)
    # toy-GHOST pick child with most descendants greedily from genesis
    def subtree_size(n, cache):
        if n in cache:
            return cache[n]
        s = 1
        for c in blocks[n].children:
            s += subtree_size(c.id, cache)
        cache[n] = s
        return s
    node = 0
    while blocks[node].children:
        cache = {}
        children = blocks[node].children
        sizes = [subtree_size(c.id, cache) for c in children]
        chosen = children[sizes.index(max(sizes))].id
        node = chosen
    # form ghost chain back to genesis
    chain2 = set(); cur = node
    while cur is not None:
        chain2.add(cur); cur = blocks[cur].parent
    attacker_on_ghost = sum(1 for b in chain2 if blocks[b].owner == 'A')
    frac_ghost = attacker_on_ghost / len(chain2)
    return frac_long, frac_ghost

if __name__ == "__main__":
    print("Selfish-mining toy revenue for attacker_power=0.35 ->", simulate_selfish_simple(20000, 0.35))
    fl, fg = toy_chain_compare(0.35, 1500)
    print("Attacker fraction on longest main chain:", fl)
    print("Attacker fraction on toy-GHOST main chain:", fg)

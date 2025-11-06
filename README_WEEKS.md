# Weeks 2–10: Tasks, How They Work, Inputs & Outputs

This guide summarizes each week’s assignment (excluding `week1`), what it does, how it works, expected inputs/outputs, and how to run inside the Dockerized Hadoop/Spark workspace.

Notes
- Hadoop MapReduce jobs use HDFS paths. Scripts stage local input files to HDFS and then print results via `hdfs dfs -cat`.
- Spark jobs in this repo run in local mode and read local files. They print results to stdout.
- Paths below assume you run through Docker with the workspace mounted at `/workspace`.

---

## Week 2 — Greedy Materialization (Java)

- What it does
  - Picks a small set of views to materialize to minimize total evaluation cost across a dependency lattice.
- How it works
  - A pure-Java greedy algorithm evaluates the marginal benefit of materializing each candidate view and iteratively picks the best.

### Code explanation
- File: `week2/greedy/GreedyMaterialization.java`
  - getDependentViews(view, graph): BFS over the dependency graph to collect the view and all descendants whose costs can be improved by materializing the candidate.
  - calculateBenefit(candidate, currentEvaluationCost, materializationCost, graph): For every dependent view, compares its current evaluation cost vs the candidate’s materialization cost, sums the deltas as total benefit.
  - updateEvaluationCosts(selected, currentEvaluationCost, materializationCost, graph): After selecting a view, updates the evaluation cost of all dependents to reflect the best available cost.
  - main():
    - Defines the lattice nodes a–h and directed edges (parent → children), sets per-view materializationCost and initializes currentEvaluationCost (all 100 at start).
    - Pre-selects top view `a`, then runs a fixed number of selection rounds, each time choosing the max-benefit view and updating costs; prints picks and final total cost.

---

## Week 3 — SGPA/CGPA MapReduce Join

- What it does
  - Joins student grade records across CSV files and computes SGPA/CGPA aggregates.
- How it works
  - Java MapReduce job (`SGPACGPAJoin`) reading all `*.csv` in the folder.
  - Mapper parses records; reducer performs keyed join/aggregation.
- Inputs
  - Local: CSV files in `week3/map_reduce_join/` (e.g., `StudentGradesNew.csv`, `SubjectInfo.csv`, etc.)
  - Staged to HDFS: `/user/gpa_input/`
- Outputs
  - HDFS: `/user/gpa_output/` → `part-r-00000`
- Run (container)
  - `docker-compose exec hadoop-workspace /run.sh /workspace/week3/map_reduce_join/run.sh`

### Code explanation
- File: `week3/map_reduce_join/SGPACGPAJoin.java`
  - JoinMapper (map):
    - setup(): Loads `Registrations.csv` and `SubjectInfo.csv` via distributed cache (job.addCacheFile). Builds two maps: regId → (regEventId, studentId, subId) and (regEventId|subId) → credits. Initializes grade-point lookup for NGrade.
    - map(): Reads `StudentGradesNew.csv` rows, extracts `RegId` and `NGrade`, looks up the registration row and subject credits, computes weighted points = gradePoint × credits, and emits key=studentId, value=`C:regEventId:credits:gp:weighted`.
  - SGPAReducer (reduce): Groups by studentId, aggregates per regEventId the semester totals (∑weighted, ∑credits), computes SGPA = ∑weighted/∑credits and running CGPA across semesters sorted by regEventId. Writes a CSV with header once using NullWritable keys.
  - Driver (main): Sets mapper/reducer classes, one reducer, adds cache files, and wires inputDir/outputDir.

---

## Week 4 — Multi-way Join (MapReduce)

- What it does
  - Performs a three-way join R ⋈ S ⋈ T using MapReduce.
- How it works
  - Single MR job reading three relations from HDFS using explicit HDFS URIs.
- Inputs
  - Local: `multiway/inputR/R.txt`, `multiway/inputS/S.txt`, `multiway/inputT/T.txt`
  - Staged to HDFS: `/user/inputR/`, `/user/inputS/`, `/user/inputT/`
- Outputs
  - HDFS: `/user/output_multiway/` → `part-r-00000`
- Run (container)
  - `docker-compose exec hadoop-workspace /run.sh /workspace/week4/multi_way_join/run.sh`

### Code explanation
- File: `week4/multi_way_join/MultiwayJoin.java`
  - TaggedTuple: Writable wrapper tagging tuple origin `R`, `S`, or `T` and carrying its payload.
  - CompositeKey: WritableComparable containing hashed buckets for attributes B and C; used to partition data for reducers.
  - JoinMapper (map):
    - For `R(a,b)`: replicates across all C buckets for the bBucket.
    - For `S(b,c)`: routes to the single bucket (bBucket, cBucket).
    - For `T(c,d)`: replicates across all B buckets for the cBucket.
  - CompositePartitioner: Ensures tuples with the same (b,c) bucket go to the same reducer.
  - JoinReducer (reduce): Collects lists for R, S, T in each (b,c) bucket and outputs joined quadruples `(a,b,c,d)` by nesting loops S×R×T.
  - Driver: Uses MultipleInputs with the same mapper for all three relations and writes final tuples.

---

## Week 5 — Join without Sort (MapReduce)

- What it does
  - Implements a two-way join without a sort-based shuffle.
- How it works
  - Mapper tags records by relation; reducer joins by key.
- Inputs
  - Local: `inputR/R.txt`, `inputS/S.txt`
  - Staged to HDFS: `/user/inputR/`, `/user/inputS/`
- Outputs
  - HDFS: `/user/output_sortjoin/` → `part-r-00000`
- Run (container)
  - `docker-compose exec hadoop-workspace /run.sh /workspace/week5/join_no_sort/run.sh`

### Code explanation
- File: `week5/join_no_sort/SortJoin.java`
  - CompositeKey(joinKey, sourceTag): Orders map outputs so all `R` records for a key arrive before `S` records (lexicographic on sourceTag), enabling a single-pass reduce.
  - JoinPartitioner and JoinGroupingComparator: Partition and group solely by joinKey so `R` and `S` for the same key land together and are iterated in sourceTag order.
  - RMapper/SMapper: Tag rows as `R` or `S` and key by join attribute `b`.
  - JoinReducer: Keeps the last seen `R` tuple for the group, then for each `S` tuple emits `(a,b,c)`.

---

## Week 6 — Triangle (Motif) Counting

- A) No Partition
  - What it does: Counts triangles (motifs) in an undirected graph.
  - Inputs: Local `edges.txt` → HDFS `/input/edges`
  - Outputs: HDFS `/output/motifs/` → `part-r-00000`
  - Run: `docker-compose exec hadoop-workspace /run.sh /workspace/week6/counting_triangles_no_partition/run.sh`

- B) With Partition
  - What it does: Partitions the graph across `RHO` buckets to scale triangle counting.
  - Inputs: Local `edges.txt` → HDFS `/input/edges`
  - Outputs: HDFS `/output/partition/` → `part-r-00000`
  - Run: `docker-compose exec hadoop-workspace /run.sh /workspace/week6/counting_triangles_partition/run.sh`

### Code explanation
- File: `week6/counting_triangles_no_partition/MotifNoPartition.java`
  - Job 1 (AdjBuilder): Builds undirected adjacency lists by emitting both (u→v) and (v→u), then dedup/sort in reducer.
  - Job 2 (PairEmit/PairJoin): For each node’s adjacency, emits a keyed record per edge; reducer receives two adjacency lists for an edge (u,v), intersects them to find common neighbors W; each unordered pair (b,d) in W forms a 4-node motif with (u,v); counts and outputs canonicalized node sets and a running total.
- File: `week6/counting_triangles_partition/Partition.java`
  - PartMapper: Hashes vertices into ρ partitions; for an input edge (u,v), enumerates all 4-combinations of partitions containing the hashes of u and v and emits edge to those combos.
  - PartReducer: Rebuilds local graph per 4-partition key, finds common-neighbor pairs as motifs, and assigns each detected motif a weight = 1 / z where z is the number of 4-combos that would also see the same motif (to avoid overcount). Emits weighted motif breakdown lines.
  - SumMapper/SumReducer: Sums weights across all reducers to get the expected total motif count.

---

## Week 7 — Spatial Join (MapReduce, C-Rep)

- What it does
  - Two-phase C-Rep spatial join over rectangles, producing candidate pairs and final joins.
- How it works
  - Job 1: Filter/replicate rectangles into grid cells, writes intermediate parts and early joins.
  - Job 2: Explicitly consumes `part-r-*` outputs from the intermediate dir; final joined results under `final_output/`.
- Inputs
  - Local: `input.txt` (rectangles)
  - Staged to HDFS: `/user/week7/spatial_join/input/input.txt`
- Outputs
  - HDFS: `/user/week7/spatial_join/output/final_output/` → part files and `_SUCCESS`
- Run (container)
  - `docker-compose exec hadoop-workspace /run.sh /workspace/week7/spatial_join/run.sh`

### Code explanation
- Files: `week7/spatial_join/rect_cont_*.java`
  - Phase 1
    - rect_cont_Mapper1: For each rectangle P/Q/R/S with coords `(x1,y1,x2,y2,type)`, computes all overlapping grid cells (k×k with width/height from conf) and emits key=cellId, value="x1|y1|x2|y2|type".
    - rect_cont_Reducer1: Groups rectangles per cell, separates into lists P,Q,R,S, then:
      - If a Q and R overlap fully within one cell (don’t cross cell boundaries), directly enumerates joins with compatible P and S and writes early results via MultipleOutputs("finalJoins").
      - Otherwise marks boundary-crossing rectangles (and overlapping partners) for replication in Phase 2, and emits only those marked rectangles as plain text for the next job.
  - Phase 2
    - rect_cont_Mapper2: For each marked rectangle, finds its "home" cell from its bottom-left corner and replicates to all cells at or to the right/above of that home cell (duplicate-avoidance region).
    - rect_cont_Reducer2: Rejoins P×Q×R×S within each cell, outputting a join only if rectangle P’s bottom-left belongs to this cell to avoid duplicates.
  - Driver (rect_cont_Runner): Runs Phase 1 to `intermediate/`, then explicitly feeds only `part-r-*` files into Phase 2 and writes `final_output/`. Finally moves MultipleOutputs results from Phase 1 into `final_output/` and cleans up intermediates.

---

## Week 8 — Bitcoin RegTest Demos (Host)

- Task 1: Mining and commit tx (two nodes)
  - Script: `week8/regtest_mining_two_nodes.sh`
  - Starts two regtest nodes, creates wallets, mines 101 blocks each, sends 1 BTC A→B, mines 1 block to confirm, prints `gettransaction`.
- Task 2: 50% attack (reorg + double-spend)
  - Script: `week8/attack_50_percent_reorg.sh`
  - Node A commits tx (1 conf); Node B mines longer private chain; connect → reorg; confirmations drop.
- Task 3: Selfish mining
  - Script: `week8/selfish_mining_demo.sh`
  - B withholds 2 blocks; A mines 1; connect → A’s last block orphaned.
- Task 4: GHOST toy simulation
  - Script: `week8/ghost_sim.py` → `python3 ghost_sim.py`
  - Prints attacker revenue (toy) and attacker fraction on main chain under longest vs toy-GHOST.

Note: These are host-level scripts (not Hadoop/Spark) and depend on `bitcoind`/`bitcoin-cli` installed on your machine. See `week8/install_bitcoin_core.sh` for an installer.

### Code explanation
This week’s tasks are shell/Python demos; there are no Java files here, so code explanation (per Java) is intentionally omitted.

---

## Week 9 — Spark Joins (Local mode)

- Custom partitioning
  - What it does: Retail join with default vs custom partitioner; prints joined records.
  - Inputs: Local CSVs in the folder (read via Spark local FS)
  - Outputs: Printed to stdout
  - Run: `docker-compose exec hadoop-workspace /run.sh /workspace/week9/custom_partitioning/run.sh`

- Skew join with shares
  - What it does: Handles skewed keys via shares-based strategy.
  - Inputs: Local CSVs
  - Outputs: Printed to stdout
  - Run: `docker-compose exec hadoop-workspace /run.sh /workspace/week9/skew_join/run.sh`

### Code explanation
- Files: `week9/custom_partitioning/*.java`
  - RetailDataJoinDefault: Loads three CSVs to build (customers ⨝ orders) ⨝ products in Java Spark using JavaPairRDDs. The first join keys on cust_id; the second re-keys to prod_id. Prints a human-readable report with total cost per order line.
  - CustomPartitionerRetailDataJoin: Same join, but explicitly applies a hash partitioner on cust_id for the first stage and on prod_id for the second, reducing shuffle skew and improving data locality.
- File: `week9/skew_join/SharesSkewRetailJoin.java`
  - Detects heavy-hitter keys (e.g., popular customers) via countByKey with a simple threshold.
  - For heavy keys, replicates customer rows to multiple salted shares and expands orders with a salt [0..s-1], then joins on the salted key to spread a single hot key across multiple partitions. Normal keys are joined without salting. The results are unified and then joined with products similarly.

---

## Week 10 — Set Similarity Join (Spark)

- What it does
  - Computes Jaccard similarities between token sets using prefix filtering to prune candidates.
- Inputs
  - Local test records embedded/loaded by the job; no HDFS staging.
- Outputs
  - Printed to stdout (e.g., `r1 - r3 : 0.50`)
- Run
  - `docker-compose exec hadoop-workspace /run.sh /workspace/week10/set_similarity_join/run.sh`

### Code explanation
- File: `week10/set_similarity_join/SetSimilarityJoin.java`
  - Builds an inverted index using prefix filtering: for each record’s sorted tokens, emits up to prefixLength = n − ⌊n·τ⌋ + 1 tokens (τ = Jaccard threshold) as blocking keys.
  - Groups by token to generate candidate record pairs, then computes exact Jaccard on token sets and filters by τ. Prints qualifying pairs and their similarity.

---

## Troubleshooting, Tips

- HDFS UI: http://localhost:9870, YARN UI: http://localhost:8088
- If a MapReduce job fails with input path errors, ensure the script staged files to HDFS and that the job references HDFS URIs (not local paths).
- Spark scripts here use `--conf spark.hadoop.fs.defaultFS=file:///` to avoid accidental HDFS resolution for local CSVs.

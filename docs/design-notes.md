# Petri-net Design (AlienBlack Interlocking)

## 1) Scope & Goals
- Model section topology 1–11 with **places** for section occupancy and **transitions** for legal moves.
- Enforce: single-section occupancy, safe crossings, no collisions, progress (avoid deadlock), and passenger-first priority whenever freight and passenger traffic contend.
- Partition the network into passenger vs. freight subnets; no transition is allowed to hop between the two lines.

## 2) Track Topology (v2)
Derived from the corridor diagrams (Islington workshops). The graph is undirected (each neighbour implies the reverse move). `Line` indicates whether the section belongs to the passenger or freight subnet.

| Section | Line      | Neighbours       | Notes |
|---------|-----------|------------------|-------|
| 1       | Passenger | 5                | South entry for passenger line |
| 2       | Passenger | 6                | South entry for passenger line |
| 3       | Freight   | 4, 7             | South entry for freight line |
| 4       | Freight   | 3, 7             | Workshops spur; part of crossing |
| 5       | Passenger | 1, 8, 9          | Passenger trunk; part of crossing |
| 6       | Passenger | 2, 9, 10         | Passenger trunk; part of crossing |
| 7       | Freight   | 3, 4, 11         | Freight trunk |
| 8       | Passenger | 5                | Passenger exit (east) |
| 9       | Passenger | 5, 6             | Junction where passenger line merges |
| 10      | Passenger | 6                | Passenger exit (east) |
| 11      | Freight   | 7                | Freight exit (east) |

**Crossing constraint:** the freight spur move `3↔4` crosses the passenger moves `1↔5` and `2↔6`. These transitions share a capacity‑1 resource with passenger priority.

**Entry/Exit recap:**  
- Southbound trains enter via {1,3} and exit via {4,8,9,11}.  
- Northbound trains enter via {4,9,10,11} and exit via {2,3}.  
- Passenger trains must remain within sections {1,2,5,6,8,9,10}.  
- Freight trains must remain within sections {3,4,7,11}.

## 3) Elements (version v2)
**Places**
- `S_i_free` for each section i ∈ {1..11} (token present ⇒ section i available).
- `Train_T_at_i` for each active train T and its current section i (one token marking location).
- `Crossing_mutex` for the shared crossing resource (1 token ⇒ crossing free).
- `Merge_turn_i` per non-crossing merge to implement fairness (round-robin tokens).

**Transitions**
- `enter_south_i_T`: place T into entry section i ∈ {1,3}.
- `enter_north_i_T`: place T into entry section i ∈ {4,9,10,11}.
- `move_i_j_T`: move train T from i to j (legal adjacency per table).
  - **Input:** `Train_T_at_i` + `S_j_free` (+ `Crossing_mutex` if the move traverses the crossing) (+ `Merge_turn_i` if fairness token required).
  - **Output:** `Train_T_at_j` + `S_i_free` (+ release captured resources).
- `exit_south_k_T`: remove T at k ∈ {4,8,9,11}.
- `exit_north_k_T`: remove T at k ∈ {2,3}.
- `mark_exit_ready_T`: once T reaches its destination, arm a one-step transition so the next move consumes the exit place.

**Guards/Policies (encoded as transition enabling rules)**
- **Single occupancy:** `S_j_free` is required to fire `move_i_j_T`.
- **Passenger-first at crossing:** transitions `3→4`, `4→3`, `1→5`, `5→1`, `2→6`, `6→2` require `Crossing_mutex`. When both freight and passenger transitions request it, passenger wins that tick.
- **No line-hopping:** adjacency only connects sections within the same line; mixed transitions are absent.
- **Exit-on-next-move:** once in destination section, the only enabled transition is `exit_*`, ensuring the train leaves on the following cycle.
- **Fair merges:** junctions such as `5↔9` and `6↔9` use `Merge_turn_i` places to rotate access and avoid starvation.

## 4) Crossing modeling (v2)
- Represent the conflict as a **resource place** `Crossing_mutex` consumed/produced by any transition that enters the shared geometric crossing.
- Passenger-first implemented by enabling function: if both claims exist, only the passenger transition is enabled, leaving freight blocked until the mutex is released.

## 5) Deadlock Avoidance (v2 idea)
- **Pre-reservation**: before entering the crossing or converging junctions, require downstream availability (check `S_j_free` plus fairness token).
- **Fairness**: on non-crossing merges, rotate a token (`Merge_turn_i`) to the next waiting train, eliminating starvation.
- **Deadlock detection**: if no transitions are enabled yet trains remain in-corridor, classify as deadlock and trigger resolution policy (e.g., release fairness token to longest-waiting train).

## 6) Correctness arguments
- **Safety (no two in one section):** every move requires `S_j_free`; firing consumes it and releases `S_i_free` afterward.
- **Collision-free:** simultaneous i→j and j→i cannot both be enabled because both require `S_i_free/S_j_free` that change atomically; at least one precondition fails.
- **Crossing safety:** `Crossing_mutex` has capacity 1; transitions that need it are mutually exclusive.
- **Priority:** enabling function disables freight-at-crossing when a passenger also claims it.
- **Progress:** fairness tokens rotate, so every waiting train eventually receives the token unless the corridor is globally blocked; deadlock detection handles the latter case.

## 7) Mapping to code
- `S_i_free` ↔ `isSectionFree(i)`
- `Train_T_at_i` ↔ `trainSection.get(T) == i`
- `Crossing_mutex` ↔ “target in `CROSSING_SECTIONS` AND no higher-priority claimant”
- `Merge_turn_i` ↔ per-target queue/round-robin scheduler.
- Exit-on-next-move ↔ mark trains whose `trainSection == trainDest` and remove them on the next `moveTrains` call.
- Transition firing ↔ staging desired moves + atomic apply in `moveTrains`

## 8) Diagrams to include
- **Big map**: all sections 1–11 with places and move transitions.
- **Inset A**: crossing subnet (`Crossing_mutex` + passenger-first enable).
- **Inset B**: non-crossing merge subnet with round-robin buffer (v2).
- **Legend**: place (circle), transition (bar), token semantics, priorities.

## 9) Known limitations (to be addressed)
- Current code denies all trains on same target (non-crossing). v2 will add round-robin.
- No explicit “system deadlock” detector yet (only 0 moves). v2 will compute enabled-set emptiness for classification.

## 10) Next actions
1. Produce updated Petri-net diagram (Lucidchart) and export to `docs/petri-net-v2.pdf`.
2. Implement full adjacency map (sections 1–11) aligned to the table above.
3. Add fairness subnet sketch; align code (round-robin per merge target).
4. Add explicit deadlock classification in code and document the policy here.

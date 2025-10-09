# Petri-net Design (v2 — student notes)

## Snapshot summary
- The corridor has 11 sections. Passenger trains use {1,2,5,6,8,9,10}; freight trains use {3,4,7,11}. There is **no** legal move that switches between these sets.
- One geometric crossing exists: the freight spur `3↔4` intersects the passenger moves `1↔5` and `2↔6`. Passenger trains must win this contest every time.
- Non-crossing merges (e.g. both passenger tracks wanting section 9) should be handled fairly so that trains take turns. We can defer the full round-robin logic to the next phase; for now the Petri-net includes a spot for the fairness token.
- When a train reaches its destination it stays put until the next move request, then exits the system (consistent with the assignment wording).
- Deadlock handling plan: if a tick produces zero enabled transitions while trains are still in the corridor, classify it as a deadlock/block and trigger the resolution policy (implemented in code later).

## 1. Track map (undirected adjacency)

| Section | Line      | Neighbours       | Notes |
|---------|-----------|------------------|-------|
| 1       | Passenger | 5                | South passenger entry |
| 2       | Passenger | 6                | South passenger entry |
| 3       | Freight   | 4, 7             | South freight entry |
| 4       | Freight   | 3, 7             | Workshops spur; part of crossing |
| 5       | Passenger | 1, 8, 9          | Passenger trunk; part of crossing |
| 6       | Passenger | 2, 9, 10         | Passenger trunk; part of crossing |
| 7       | Freight   | 3, 4, 11         | Freight trunk |
| 8       | Passenger | 5                | Passenger exit (east) |
| 9       | Passenger | 5, 6             | Passenger merge/diverge |
| 10      | Passenger | 6                | Passenger exit (east) |
| 11      | Freight   | 7                | Freight exit (east) |

Entry/exit rules from the brief still apply:
- Southbound (left → right) enter {1,3}, exit {4,8,9,11}.
- Northbound (right → left) enter {4,9,10,11}, exit {2,3}.

## 2. Special resources and policies

**Crossing mutex**  
- Required for the six directed edges `{1→5, 5→1, 2→6, 6→2, 3→4, 4→3}` that make up the physical crossing.  
- If a passenger train and a freight train request the mutex in the same tick, the passenger move fires and the freight stays blocked.  
- If two trains of the same type clash at the crossing, both remain blocked for this tick (documented policy).

**Fair merges (planned for phase 2)**  
- Non-crossing contention, e.g. both passenger approaches (5 and 6) wanting section 9, uses a logical place `Merge9_turn` to rotate access and avoid starvation. The crossing itself continues to use the passenger-first rule.

**Exit-on-next-move**  
- After moving into the destination section, the train receives an `exit_ready` token. It remains in place until the next `moveTrains` call, which consumes `exit_ready`, frees its section place, and removes it from the corridor.

**Deadlock signalling**  
- If no transitions fire in a tick while trains remain in the corridor, the system reports a deadlock/block condition. Recovery logic (e.g., freeing a fairness token or forcing a priority move) will be added in the deadlock phase.

## 3. Petri-net ingredients

- **Places**
  - `S_i_free` for section availability (token present = section empty).
  - `Train_T_at_i` for each active train.
  - `Crossing_mutex` (initially 1).
  - `Merge9_turn` (initially 1, moves between contenders once fairness logic is added).
- `Exit_ready_T` for trains that have reached their destination.
- **Transitions**
  - `enter_{dir}_i_T` to inject trains at legal entry points.
  - `move_i_j_T` for each adjacency edge that stays on the correct line.
  - `exit_{dir}_k_T` to remove trains from legal exits.
  - `mark_exit_ready_T` (conceptual) sets up `Exit_ready_T` when `i == dest`.
  - Future fairness transitions for rotating `Merge9_turn`.

Each `move_i_j_T` transition consumes `Train_T_at_i`, `S_j_free`, and any shared resources it needs (crossing mutex, merge token). It produces `Train_T_at_j` and returns `S_i_free` (plus the shared resources).

## 4. Code mapping plan

| Petri-net idea | Java structure |
|----------------|----------------|
| `S_i_free` | `isSectionFree(sectionId)` + `sectionOwner` map |
| `Train_T_at_i` | `trainSection` map |
| `Crossing_mutex` | runtime check that only one move across the crossing edges is granted per tick, with passenger priority |
| `Merge9_turn` | (phase 2) per-target queue/round-robin scheduler |
| `Exit_ready_T` | `exitReady` set; trains removed on next `moveTrains` call |

Routing currently picks a legal next hop guided by precomputed BFS paths (to be implemented alongside the code). Passenger trains are limited to passenger sections; freight trains to freight sections. Train type is inferred from the name prefix (`P*` = passenger, `F*` = freight). Neutral names default to passenger unless we decide to reject them.

### Invariants cheat-sheet

| Invariant | Enforcement idea |
|-----------|------------------|
| ≤1 train per section | `S_i_free` token + atomic apply |
| Train stays on its line | guard `sameLine(train, next)` |
| Crossing capacity = 1 | mutex arbitration each tick |
| Passenger wins crossing vs freight | conflict resolver |
| Non-crossing merges progress | (phase 2) rotating fairness token |

## 5. Testing roadmap
1. **Phase 1 (graph + exits)**  
   - Passenger train from 1 reaches 8 along 5.  
   - Freight train from 3 reaches 11 along 7.  
   - Illegal entry when a train tries to use the wrong line.  
   - Train exits on the move after reaching its destination.
2. **Phase 2 (crossing + fairness)**  
   - Passenger vs freight at crossing → passenger moves, freight waits.  
   - Two freights at the crossing both denied (documented policy).  
   - Alternating access to section 9 when both passenger tracks compete.
3. **Phase 3 (deadlock)**  
   - Detect stalemate with opposing trains on 3↔7↔11 or 5↔9↔6.  
   - Apply resolution strategy and verify progress resumes.

## 6. Next actions
1. Draw the updated Petri-net (full map + inset A for crossing + inset B for merge fairness) in Lucidchart and export to `docs/petri-net-v2.pdf`.  
2. Implement Phase 1 code changes: adjacency map, routing guard, exit-on-next-move, and the matching JUnit tests.  
3. Commit each phase with a progress note so the development trail is visible for marking.

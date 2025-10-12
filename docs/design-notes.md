# Petri-net Design (v2 — student notes)

## Snapshot summary
- Corridor = 11 numbered sections. Passenger trains stay within {1,2,5,6,8,9,10}; freight trains remain in {3,4,7,11}. No legal move switches line sets.
- Crossing mutex covers the geometric clash between edges `1↔5`, `2↔6`, and `3↔4`; passenger trains win when contending with freight. Only one crossing move fires per tick.
- Non-crossing merges live at **6** (northbound funnel from entries 9 & 10) and **7** (freight funnel between southbound 3→11 and northbound 11→3). Each uses a round-robin token so rivals alternate access.
- When a train enters its destination section it stays there until the next `moveTrains` call, then exits (matching the assignment brief).
- Deadlock handling now classifies “no progress” ticks (`Outcome` ∈ {PROGRESS, BLOCKED, DEADLOCK}) so tests can verify when the network is stuck.

## 1. Track map (undirected adjacency)

| Section | Line      | Neighbours       | Notes |
|---------|-----------|------------------|-------|
| 1       | Passenger | 5                | South passenger entry |
| 2       | Passenger | 6                | South passenger entry / north exit |
| 3       | Freight   | 4, 7             | South freight entry / north exit |
| 4       | Freight   | 3                | Freight spur; part of crossing |
| 5       | Passenger | 8, 9, 1          | Passenger trunk; part of crossing |
| 6       | Passenger | 2                | Passenger trunk; part of crossing & merge |
| 7       | Freight   | 3, 11            | Freight trunk merge |
| 8       | Passenger | 5                | Passenger exit (east) |
| 9       | Passenger | 5, 6             | Passenger diverge (5→9) and northbound approach (9→6) |
| 10      | Passenger | 6                | Passenger entry (north) feeding merge at 6 |
| 11      | Freight   | 7                | Freight exit (east) |

Entry/exit rules from the brief:
- Southbound (left → right) enter {1,3}, exit {4,8,9,11}.
- Northbound (right → left) enter {4,9,10,11}, exit {2,3}.

## 2. Special resources and policies

**Crossing mutex**  
- Applied to the six directed edges `{1→5, 5→1, 2→6, 6→2, 3→4, 4→3}`.  
- If both passenger and freight request the mutex in the same tick, the passenger move fires. Same-type clashes release no more than one train per tick.  
- Crossing arbitration happens before regular target-based conflict handling.

- **Fair merges (implemented in code)**  
- Sections 6 and 7 each own a logical token (`Merge6_turn`, `Merge7_turn`) that rotates across contending trains (round robin). This prevents starvation when independent approaches want the same section.  
- Crossing still uses passenger-first rather than the fairness token.

**Exit-on-next-move**  
- Arrival at the destination section arms an `exit_ready` token. On the next `moveTrains` call the train consumes `exit_ready`, frees its section, and leaves the corridor (reporting `-1` via `getTrain`).

**Deadlock signalling**  
- Each `moveTrains` call reports an `Outcome` stored in `getLastOutcome()`. “Deadlock” is raised when at least one train requested movement but all were blocked (occupancy or policy). “Blocked” captures idle ticks (no requests). Tests use this to assert behaviours.

## 3. Petri-net ingredients

- **Places**
  - `S_i_free` for section availability.
  - `Train_T_at_i` for each active train.
  - `Crossing_mutex` (capacity 1).
  - `Merge6_turn`, `Merge9_turn` for fairness tokens.
  - `Exit_ready_T` when a train has reached its destination.
- **Transitions**
  - `enter_{dir}_i_T` for legal entries.
  - `move_i_j_T` for each adjacency edge that stays on the correct line, consuming `S_j_free` and any mutex/merge tokens needed.
  - `exit_{dir}_k_T` to remove trains from legal exits.
  - `mark_exit_ready_T` (conceptual) to place `Exit_ready_T` once `i == dest`.
  - Token-rotation transitions for the merge places (implemented as queue logic in code).

## 4. Code mapping plan

| Petri-net idea | Java structure |
|----------------|----------------|
| `S_i_free` | `sectionOwner` + `isSectionFree(sectionId)` |
| `Train_T_at_i` | `trainSection` map |
| `Crossing_mutex` | crossing edge arbitration (`chooseCrossingWinner`) |
| `Merge6/9_turn` | `mergeQueues` round-robin scheduler |
| `Exit_ready_T` | `exitReady` set processed in `moveTrains` |

Routing uses a precomputed BFS from each section to every reachable destination (passenger vs freight maps kept separate via `sameLine`). Train type is inferred from name prefix (`P*` passenger, `F*` freight); other names default to passenger unless additional validation is added later.

### Invariants cheat-sheet

| Invariant | Enforcement idea |
|-----------|------------------|
| ≤1 train per section | `S_i_free` token + atomic apply |
| Train stays on its line | BFS restricts to same line; entry/dest validation |
| Crossing capacity = 1 | crossing mutex arbitration |
| Passenger wins crossing vs freight | passenger-first selection |
| Non-crossing merges progress | rotating fairness token (sections 6 & 9) |

## 5. Testing roadmap
1. **Phase 1 (graph + exits)**  
   - Passenger reachability (1→5→8), freight reachability (3→7→11).  
   - Wrong-line entry/destination guarded.  
   - Exit on next move verified (`MovePhase1_Test`, `RoutingRules_Test`, etc.).
2. **Phase 2 (crossing + fairness)**  
   - Passenger beats freight at crossing; freight only moves when passenger absent/cleared (`PriorityAndCrossing_Test`, `CrossingConflict_Test`).  
   - Round-robin merge scheduling at sections 6 and 7 (`FairMerge_Test`).  
   - All prior tests kept green.
3. **Phase 3 (deadlock)**  
   - Detect stalemates (e.g., opposing freights on 3↔7↔11).  
   - Classification verified via `DeadlockHandling_Test` (`getLastOutcome()` returns DEADLOCK/BLOCKED/PROGRESS).

## 6. Next actions
1. Export updated Petri-net diagrams (full map, crossing inset, merge fairness inset) to `docs/petri-net-v2.pdf`.  
2. Phase 4: add deadlock-resolution notes to the PDF caption and ensure README/submission instructions are up to date.  
3. Final verification sweep (all tests + manual sanity) before submission.

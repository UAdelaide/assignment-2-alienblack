# Petri-net Design (AlienBlack Interlocking)

## 1) Scope & Goals
- Model the station track 1–11 with **places** for section occupancy and **transitions** for legal moves.
- Enforce: single-section occupancy, safe crossing, no collisions, progress (avoid deadlock), and passenger-first priority at the crossing.

## 2) Elements (version v1)
**Places**
- `S_i_free` for each section i ∈ {1..11} (token present ⇒ section i available).
- `Train_T_at_i` for each active train T and its current section i (one token marking location).
- `Crossing_mutex` for the shared crossing resource (1 token ⇒ free).

**Transitions**
- `move_i_j_T`: move train T from i to j (legal adjacency).
  - **Input:** `Train_T_at_i` + `S_j_free` (+ `Crossing_mutex` if i→j traverses the crossing).
  - **Output:** `Train_T_at_j` + `S_i_free` (+ return `Crossing_mutex`).
- `enter_south_i_T`: place T into entry section i ∈ {1,3}.
- `enter_north_i_T`: place T into entry section i ∈ {4,9,10,11}.
- `exit_south_k_T`: remove T at k ∈ {4,8,9,11}.
- `exit_north_k_T`: remove T at k ∈ {2,3}.

**Guards/Policies (encoded as transition enabling rules)**
- **Single occupancy:** `S_j_free` is required to fire `move_i_j_T`.
- **Passenger-first at crossing:** if both passenger and freight moves demand `Crossing_mutex` this tick, enable passenger transition; withhold freight.
- **No line-hopping:** transitions only along designated line branches (freight vs passenger) per map.

## 3) Crossing modeling (v1)
- Represent the conflict as a **resource place** `Crossing_mutex` consumed/produced by any transition that enters the crossing section.
- Passenger-first implemented by enabling function: if both claims exist, only passenger transition is enabled.

## 4) Deadlock Avoidance (v2 idea)
- **Pre-reservation**: before occupying a pre-crossing section, require `DownstreamBuffer_free` to avoid nose-to-nose blocks.
- **Fairness**: on non-crossing merges, use token rotation via a small “turnstile” subnet—token cycles through trains requesting the merge.

## 5) Correctness arguments
- **Safety (no two in one section):** every move requires `S_j_free`; firing consumes it and releases `S_i_free` afterward.
- **Collision-free:** simultaneous i→j and j→i cannot both be enabled because both require `S_i_free/S_j_free` that change atomically; at least one precondition fails.
- **Crossing safety:** `Crossing_mutex` has capacity 1; transitions that need it are mutually exclusive.
- **Priority:** enabling function disables freight-at-crossing when a passenger also claims it.

## 6) Mapping to code
- `S_i_free` ↔ `isSectionFree(i)`
- `Train_T_at_i` ↔ `trainSection.get(T) == i`
- `Crossing_mutex` ↔ “target in `CROSSING_SECTIONS` AND no higher-priority claimant”
- Transition firing ↔ staging desired moves + atomic apply in `moveTrains`

## 7) Diagrams to include
- **Big map**: all sections 1–11 with places and move transitions.
- **Inset A**: crossing subnet (`Crossing_mutex` + passenger-first enable).
- **Inset B**: non-crossing merge subnet with round-robin buffer (v2).
- **Legend**: place (circle), transition (bar), token semantics, priorities.

## 8) Known limitations (to be addressed)
- Current code denies all trains on same target (non-crossing). v2 will add round-robin.
- No explicit “system deadlock” detector yet (only 0 moves). v2 will compute enabled-set emptiness for classification.

## 9) Next actions
- Expand adjacency for all sections (1–11) and update Petri-net accordingly.
- Add fairness subnet sketch; align code (round-robin per merge target).
- Add explicit deadlock classification in code and a small proof sketch in this doc.

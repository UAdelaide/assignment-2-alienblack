import java.util.*;

/**
 * Minimal, safe implementation scaffold + phase-1 movement:
 * - Tracks which train is in which section (single-occupancy).
 * - Validates legal entry/exit sections.
 * - moveTrains: adjacency + single-occupancy, atomic apply, deny same-target conflicts.
 */
public class InterlockingImpl implements Interlocking {

    // ---- Entry/exit constraints
    private static final Set<Integer> SOUTH_ENTRY = Set.of(1, 3);
    private static final Set<Integer> SOUTH_EXIT  = Set.of(4, 8, 9, 11);
    private static final Set<Integer> NORTH_ENTRY = Set.of(4, 9, 10, 11);
    private static final Set<Integer> NORTH_EXIT  = Set.of(2, 3);

    // ---- Minimal track graph for phase-1 tests (extend later)
    private static final Map<Integer, List<Integer>> NEXT_HOPS = new HashMap<>();
    static {
        // Minimal yet useful graph for tests:
        // 1 <-> 2 -> 4 ; 3 -> 2 ; 4 terminal
        NEXT_HOPS.clear();
        NEXT_HOPS.put(1, List.of(2));     // 1 → 2
        NEXT_HOPS.put(2, List.of(1, 4));  // 2 → 1 or 4
        NEXT_HOPS.put(3, List.of(2));     // 3 → 2
        NEXT_HOPS.put(4, List.of());      // terminal/exit
    }

    // ---- State
    /** sectionId -> trainName */
    private final Map<Integer, String> sectionOwner = new HashMap<>();
    /** trainName -> sectionId */
    private final Map<String, Integer> trainSection = new HashMap<>();
    /** trainName -> destination sectionId */
    private final Map<String, Integer> trainDest = new HashMap<>();

    // ---- Helpers (direction + validation)
    private static boolean isSouthbound(int entry, int dest) {
        if (SOUTH_ENTRY.contains(entry)) return true;
        if (NORTH_ENTRY.contains(entry)) return false;
        if (SOUTH_EXIT.contains(dest))   return true;
        if (NORTH_EXIT.contains(dest))   return false;
        return true; // default; addTrain still validates
    }
    private static boolean isValidEntry(boolean southbound, int section) {
        return southbound ? SOUTH_ENTRY.contains(section) : NORTH_ENTRY.contains(section);
    }
    private static boolean isValidExit(boolean southbound, int section) {
        return southbound ? SOUTH_EXIT.contains(section) : NORTH_EXIT.contains(section);
    }

    // ---- Helpers (occupancy + movement)
    private boolean isSectionFree(int sectionId) {
        return !sectionOwner.containsKey(sectionId);
    }
    private static boolean isPassenger(String name) { return name != null && name.startsWith("P"); }
    private static boolean isFreight(String name)   { return name != null && name.startsWith("F"); }

    // Crossing targets where priority applies (adjust if your map changes)
    private static final Set<Integer> CROSSING_SECTIONS = Set.of(2);

    private void occupy(int sectionId, String trainName) {
        sectionOwner.put(sectionId, trainName);
        trainSection.put(trainName, sectionId);
    }
    private void vacate(int sectionId) {
        String t = sectionOwner.remove(sectionId);
        if (t != null) trainSection.remove(t);
    }
    private int pickNext(int from, int dest) {
        List<Integer> opts = NEXT_HOPS.getOrDefault(from, List.of());
        if (opts.isEmpty()) return from;             // nowhere to go
        if (opts.size() == 1) return opts.get(0);    // deterministic
        // naive “closer to destination id” heuristic
        int best = from, bestScore = Integer.MAX_VALUE;
        for (int n : opts) {
            int s = Math.abs(dest - n);
            if (s < bestScore) { bestScore = s; best = n; }
        }
        return best;
    }

    // ---- Interlocking API
    @Override
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection)
            throws IllegalArgumentException, IllegalStateException {

        if (trainName == null || trainName.isBlank())
            throw new IllegalArgumentException("trainName must be non-empty");
        if (entryTrackSection <= 0 || destinationTrackSection <= 0)
            throw new IllegalArgumentException("Sections must be positive IDs");
        if (trainSection.containsKey(trainName))
            throw new IllegalStateException("Train already exists: " + trainName);

        boolean southbound = isSouthbound(entryTrackSection, destinationTrackSection);
        if (!isValidEntry(southbound, entryTrackSection))
            throw new IllegalArgumentException("Illegal entry for direction: " + entryTrackSection);
        if (!isValidExit(southbound, destinationTrackSection))
            throw new IllegalArgumentException("Illegal destination for direction: " + destinationTrackSection);
        if (!isSectionFree(entryTrackSection))
            throw new IllegalStateException("Entry section occupied: " + entryTrackSection);

        trainDest.put(trainName, destinationTrackSection);
        occupy(entryTrackSection, trainName);
    }

    @Override
    public int moveTrains(String[] trainNames) throws IllegalArgumentException {
        if (trainNames == null) throw new IllegalArgumentException("trainNames must not be null");

        // validate inputs & duplicates
        Set<String> seen = new HashSet<>();
        for (String name : trainNames) {
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("train name in batch is empty");
            if (!trainSection.containsKey(name))
                throw new IllegalArgumentException("unknown train: " + name);
            if (!seen.add(name))
                throw new IllegalArgumentException("duplicate train in batch: " + name);
        }

        // stage claims
        Map<String,Integer> desired = new HashMap<>();
        Map<Integer,List<String>> targetClaims = new HashMap<>();

        for (String t : trainNames) {
            int from = trainSection.get(t);
            int dest = trainDest.getOrDefault(t, from);
            int next = pickNext(from, dest);
            if (next == from) continue;           // no progress
            if (!isSectionFree(next)) continue;   // blocked by occupancy
            desired.put(t, next);
            targetClaims.computeIfAbsent(next, k -> new ArrayList<>()).add(t);
        }

        // resolve conflicts: if multiple claim same target, deny all (phase-1 rule)
        // resolve conflicts: if multiple claim same target, deny all (phase-1 rule)
        int moved = 0;

// First, resolve conflicts per target
        Map<String,Integer> granted = new HashMap<>();
        for (Map.Entry<Integer,List<String>> entry : targetClaims.entrySet()) {
            int target = entry.getKey();
            List<String> claimants = entry.getValue();

            if (claimants.size() == 1) {
                // single claimant → grant
                String t = claimants.get(0);
                granted.put(t, target);
                continue;
            }

            // Multiple claimants
            if (CROSSING_SECTIONS.contains(target)) {
                // Passenger-first policy at crossing
                String passenger = null;
                for (String c : claimants) if (isPassenger(c)) { passenger = c; break; }
                if (passenger != null) {
                    granted.put(passenger, target); // passenger wins
                    // others denied
                    continue;
                }
            }
            // otherwise deny all for this target (no movement)
        }

// Apply granted moves atomically
        for (Map.Entry<String,Integer> g : granted.entrySet()) {
            String t = g.getKey();
            int to = g.getValue();
            int from = trainSection.get(t);
            // double-check still free (paranoia)
            if (!isSectionFree(to) && !Objects.equals(sectionOwner.get(to), t)) continue;
            vacate(from);
            occupy(to, t);
            moved++;
        }
        return moved;

    }

    @Override
    public String getSection(int trackSection) throws IllegalArgumentException {
        if (trackSection <= 0) throw new IllegalArgumentException("Invalid section id");
        return sectionOwner.getOrDefault(trackSection, null);
    }

    @Override
    public int getTrain(String trainName) throws IllegalArgumentException {
        if (trainName == null || trainName.isBlank())
            throw new IllegalArgumentException("trainName must be non-empty");
        Integer sec = trainSection.get(trainName);
        if (sec == null) throw new IllegalArgumentException("unknown train: " + trainName);
        return sec;
    }
}

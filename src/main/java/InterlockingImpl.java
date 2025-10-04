import java.util.*;

/**
 * Minimal, safe implementation scaffold + phase-1 movement (Java 8 compatible):
 * - Tracks which train is in which section (single-occupancy).
 * - Validates legal entry/exit sections.
 * - moveTrains: adjacency + single-occupancy, atomic apply, deny same-target conflicts.
 * - No Java 9+/11 APIs (uses Arrays.asList / Collections.emptyList and trim().isEmpty()).
 */
public class InterlockingImpl implements Interlocking {

    // ---- Entry/exit constraints
    private static final Set<Integer> SOUTH_ENTRY = new HashSet<Integer>(Arrays.asList(1, 3));
    private static final Set<Integer> SOUTH_EXIT  = new HashSet<Integer>(Arrays.asList(4, 8, 9, 11));
    private static final Set<Integer> NORTH_ENTRY = new HashSet<Integer>(Arrays.asList(4, 9, 10, 11));
    private static final Set<Integer> NORTH_EXIT  = new HashSet<Integer>(Arrays.asList(2, 3));

    // ---- Minimal track graph for phase-1 tests (extend later)
    private static final Map<Integer, List<Integer>> NEXT_HOPS = new HashMap<Integer, List<Integer>>();
    static {
        // Minimal yet useful graph for tests:
        // 1 <-> 2 -> 4 ; 3 -> 2 ; 4 terminal
        NEXT_HOPS.put(1, Arrays.asList(2));                // 1 → 2
        NEXT_HOPS.put(2, Arrays.asList(1, 4));             // 2 → 1 or 4
        NEXT_HOPS.put(3, Arrays.asList(2));                // 3 → 2
        NEXT_HOPS.put(4, Collections.<Integer>emptyList()); // terminal
    }

    // Crossing targets where priority applies (adjust if your map changes)
    private static final Set<Integer> CROSSING_SECTIONS = new HashSet<Integer>(Arrays.asList(2));

    // ---- State
    /** sectionId -> trainName */
    private final Map<Integer, String> sectionOwner = new HashMap<Integer, String>();
    /** trainName -> sectionId */
    private final Map<String, Integer> trainSection = new HashMap<String, Integer>();
    /** trainName -> destination sectionId */
    private final Map<String, Integer> trainDest = new HashMap<String, Integer>();

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

    private static boolean isPassenger(String name) { return name != null && name.startsWith("P"); }
    @SuppressWarnings("unused")
    private static boolean isFreight(String name)   { return name != null && name.startsWith("F"); }

    // ---- Helpers (occupancy + movement)
    private boolean isSectionFree(int sectionId) {
        return !sectionOwner.containsKey(sectionId);
    }
    private void occupy(int sectionId, String trainName) {
        sectionOwner.put(sectionId, trainName);
        trainSection.put(trainName, sectionId);
    }
    private void vacate(int sectionId) {
        String t = sectionOwner.remove(sectionId);
        if (t != null) trainSection.remove(t);
    }
    private int pickNext(int from, int dest) {
        List<Integer> opts = NEXT_HOPS.containsKey(from) ? NEXT_HOPS.get(from) : Collections.<Integer>emptyList();
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

        if (trainName == null || trainName.trim().isEmpty())
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
        Set<String> seen = new HashSet<String>();
        for (String name : trainNames) {
            if (name == null || name.trim().isEmpty())
                throw new IllegalArgumentException("train name in batch is empty");
            if (!trainSection.containsKey(name))
                throw new IllegalArgumentException("unknown train: " + name);
            if (!seen.add(name))
                throw new IllegalArgumentException("duplicate train in batch: " + name);
        }

        // stage claims
        Map<String,Integer> desired = new HashMap<String,Integer>();
        Map<Integer,List<String>> targetClaims = new HashMap<Integer,List<String>>();

        for (String t : trainNames) {
            int from = trainSection.get(t);
            int dest = trainDest.containsKey(t) ? trainDest.get(t) : from;
            int next = pickNext(from, dest);
            if (next == from) continue;           // no progress
            if (!isSectionFree(next)) continue;   // blocked by occupancy
            desired.put(t, next);
            List<String> lst = targetClaims.get(next);
            if (lst == null) { lst = new ArrayList<String>(); targetClaims.put(next, lst); }
            lst.add(t);
        }

        // resolve conflicts:
        // - crossing targets: passenger-first (others denied)
        // - non-crossing: deny-all (phase-1 simple policy)
        Map<String,Integer> granted = new HashMap<String,Integer>();

        for (Map.Entry<Integer,List<String>> entry : targetClaims.entrySet()) {
            int target = entry.getKey();
            List<String> claimants = entry.getValue();

            if (claimants.size() == 1) {
                granted.put(claimants.get(0), target);
                continue;
            }

            if (CROSSING_SECTIONS.contains(target)) {
                String passenger = null;
                for (String c : claimants) { if (isPassenger(c)) { passenger = c; break; } }
                if (passenger != null) {
                    granted.put(passenger, target); // passenger wins; others denied
                }
                // else: all denied at crossing
                continue;
            }
            // non-crossing multi-claim: deny all (no grant)
        }

        // Apply granted moves atomically
        int moved = 0;
        for (Map.Entry<String,Integer> g : granted.entrySet()) {
            String t = g.getKey();
            int to = g.getValue();
            // still free?
            if (!isSectionFree(to) && !t.equals(sectionOwner.get(to))) continue;
            int from = trainSection.get(t);
            vacate(from);
            occupy(to, t);
            moved++;
        }
        return moved;
    }

    @Override
    public String getSection(int trackSection) throws IllegalArgumentException {
        if (trackSection <= 0) throw new IllegalArgumentException("Invalid section id");
        return sectionOwner.containsKey(trackSection) ? sectionOwner.get(trackSection) : null;
    }

    @Override
    public int getTrain(String trainName) throws IllegalArgumentException {
        if (trainName == null || trainName.trim().isEmpty())
            throw new IllegalArgumentException("trainName must be non-empty");
        Integer sec = trainSection.get(trainName);
        if (sec == null) throw new IllegalArgumentException("unknown train: " + trainName);
        return sec;
    }
}

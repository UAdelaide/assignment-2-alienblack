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

    // ---- Track graph (passenger + freight lines) ----
    private static final Map<Integer, List<Integer>> NEXT_HOPS = new HashMap<Integer, List<Integer>>();
    private static final Set<Integer> PASSENGER_SECTIONS = new HashSet<Integer>(Arrays.asList(1, 2, 5, 6, 8, 9, 10));
    private static final Set<Integer> FREIGHT_SECTIONS   = new HashSet<Integer>(Arrays.asList(3, 4, 7, 11));
    private static final Map<Integer, Map<Integer, List<Integer>>> ROUTES;
    static {
        NEXT_HOPS.put(1, Arrays.asList(5));
        NEXT_HOPS.put(2, Arrays.asList(6));
        NEXT_HOPS.put(3, Arrays.asList(4, 7));
        NEXT_HOPS.put(4, Arrays.asList(3, 7));
        NEXT_HOPS.put(5, Arrays.asList(1, 8, 9));
        NEXT_HOPS.put(6, Arrays.asList(2, 9, 10));
        NEXT_HOPS.put(7, Arrays.asList(3, 4, 11));
        NEXT_HOPS.put(8, Arrays.asList(5));
        NEXT_HOPS.put(9, Arrays.asList(5, 6));
        NEXT_HOPS.put(10, Arrays.asList(6));
        NEXT_HOPS.put(11, Arrays.asList(7));
        ROUTES = computeRoutes();
    }

    // Crossing targets where priority applies (adjust if your map changes)
    private static final Set<Integer> CROSSING_SECTIONS = Collections.emptySet();

    // ---- State
    /** sectionId -> trainName */
    private final Map<Integer, String> sectionOwner = new HashMap<Integer, String>();
    /** trainName -> sectionId */
    private final Map<String, Integer> trainSection = new HashMap<String, Integer>();
    /** trainName -> destination sectionId */
    private final Map<String, Integer> trainDest = new HashMap<String, Integer>();
    /** trains that must exit on their next move */
    private final Set<String> exitReady = new HashSet<String>();

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
    private static boolean isPassengerSection(int section) { return PASSENGER_SECTIONS.contains(section); }
    private static boolean isFreightSection(int section)   { return FREIGHT_SECTIONS.contains(section); }

    // ---- Helpers (occupancy + movement)
    private boolean isSectionFree(int sectionId) {
        return !sectionOwner.containsKey(sectionId);
    }
    private void occupy(int sectionId, String trainName) {
        sectionOwner.put(sectionId, trainName);
        trainSection.put(trainName, sectionId);
        exitReady.remove(trainName);
    }
    private void vacate(int sectionId) {
        String t = sectionOwner.remove(sectionId);
        if (t != null) trainSection.put(t, Integer.valueOf(-1));
    }
    private int pickNext(int from, int dest) {
        if (from == dest) return from;
        Map<Integer, List<Integer>> destMap = ROUTES.get(from);
        if (destMap == null) return from;
        List<Integer> path = destMap.get(dest);
        if (path == null || path.size() < 2) return from;
        return path.get(1);
    }

    // ---- Interlocking API
    @Override
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection)
            throws IllegalArgumentException, IllegalStateException {

        if (trainName == null || trainName.trim().isEmpty())
            throw new IllegalArgumentException("trainName must be non-empty");
        if (entryTrackSection <= 0 || destinationTrackSection <= 0)
            throw new IllegalArgumentException("Sections must be positive IDs");
        if (trainDest.containsKey(trainName))
            throw new IllegalStateException("Train already exists: " + trainName);

        if (isPassenger(trainName)) {
            if (!isPassengerSection(entryTrackSection) || !isPassengerSection(destinationTrackSection))
                throw new IllegalArgumentException("Passenger train cannot use freight sections");
        } else if (isFreight(trainName)) {
            if (!isFreightSection(entryTrackSection) || !isFreightSection(destinationTrackSection))
                throw new IllegalArgumentException("Freight train cannot use passenger sections");
        }

        boolean southbound = isSouthbound(entryTrackSection, destinationTrackSection);
        if (!isValidEntry(southbound, entryTrackSection))
            throw new IllegalArgumentException("Illegal entry for direction: " + entryTrackSection);
        if (!isValidExit(southbound, destinationTrackSection))
            throw new IllegalArgumentException("Illegal destination for direction: " + destinationTrackSection);
        if (!isSectionFree(entryTrackSection))
            throw new IllegalStateException("Entry section occupied: " + entryTrackSection);
        if (!hasRoute(entryTrackSection, destinationTrackSection))
            throw new IllegalArgumentException("No route from entry to destination");

        trainDest.put(trainName, destinationTrackSection);
        occupy(entryTrackSection, trainName);
        if (entryTrackSection == destinationTrackSection) exitReady.add(trainName);
    }

    @Override
    public int moveTrains(String[] trainNames) throws IllegalArgumentException {
        if (trainNames == null) throw new IllegalArgumentException("trainNames must not be null");

        // validate inputs & duplicates
        Set<String> seen = new HashSet<String>();
        for (String name : trainNames) {
            if (name == null || name.trim().isEmpty())
                throw new IllegalArgumentException("train name in batch is empty");
            if (!trainDest.containsKey(name))
                throw new IllegalArgumentException("train not active in corridor: " + name);
            if (!seen.add(name))
                throw new IllegalArgumentException("duplicate train in batch: " + name);
        }

        int moved = 0;
        List<String> exiting = new ArrayList<String>();

        // stage claims
        Map<String,Integer> desired = new HashMap<String,Integer>();
        Map<Integer,List<String>> targetClaims = new HashMap<Integer,List<String>>();

        for (String t : trainNames) {
            if (exitReady.contains(t)) {
                Integer current = trainSection.get(t);
                if (current != null && current.intValue() >= 0) {
                    vacate(current.intValue());
                }
                exiting.add(t);
                continue;
            }
            int from = trainSection.get(t);
            int dest = trainDest.get(t);
            int next = pickNext(from, dest);
            if (next == from) continue;           // no progress
            if (!isSectionFree(next)) continue;   // blocked by occupancy
            desired.put(t, next);
            List<String> lst = targetClaims.get(next);
            if (lst == null) { lst = new ArrayList<String>(); targetClaims.put(next, lst); }
            lst.add(t);
        }

        // resolve conflicts (phase-1 policy):
        // - crossing targets: passenger-first (others denied)
        // - non-crossing: deny-all
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
        for (Map.Entry<String,Integer> g : granted.entrySet()) {
            String t = g.getKey();
            int to = g.getValue();
            // still free?
            if (!isSectionFree(to) && !t.equals(sectionOwner.get(to))) continue;
            int from = trainSection.get(t);
            vacate(from);
            occupy(to, t);
            moved++;
            if (trainDest.get(t) != null && to == trainDest.get(t)) {
                exitReady.add(t);
            }
        }

        // Process exits after movement
        for (String t : exiting) {
            exitReady.remove(t);
            trainDest.remove(t);
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

    // ---- Routing helpers ----
    private static boolean hasRoute(int from, int dest) {
        Map<Integer, List<Integer>> destMap = ROUTES.get(from);
        return destMap != null && destMap.containsKey(dest);
    }

    private static Map<Integer, Map<Integer, List<Integer>>> computeRoutes() {
        Map<Integer, Map<Integer, List<Integer>>> routes = new HashMap<Integer, Map<Integer, List<Integer>>>();
        for (Map.Entry<Integer, List<Integer>> entry : NEXT_HOPS.entrySet()) {
            int start = entry.getKey();
            Map<Integer, Integer> prev = new HashMap<Integer, Integer>();
            Queue<Integer> queue = new ArrayDeque<Integer>();
            queue.add(Integer.valueOf(start));
            prev.put(Integer.valueOf(start), null);

            while (!queue.isEmpty()) {
                int node = queue.remove().intValue();
                List<Integer> neighbours = NEXT_HOPS.get(node);
                if (neighbours == null) continue;
                for (int nxt : neighbours) {
                    if (prev.containsKey(Integer.valueOf(nxt))) continue;
                    if (!sameLine(start, nxt)) continue;
                    prev.put(Integer.valueOf(nxt), Integer.valueOf(node));
                    queue.add(Integer.valueOf(nxt));
                }
            }

            Map<Integer, List<Integer>> dests = new HashMap<Integer, List<Integer>>();
            for (Integer reach : prev.keySet()) {
                int dest = reach.intValue();
                if (!sameLine(start, dest)) continue;
                List<Integer> path = buildPath(start, dest, prev);
                if (!path.isEmpty()) {
                    dests.put(Integer.valueOf(dest), Collections.unmodifiableList(path));
                }
            }
            routes.put(Integer.valueOf(start), Collections.unmodifiableMap(dests));
        }
        return Collections.unmodifiableMap(routes);
    }

    private static List<Integer> buildPath(int start, int dest, Map<Integer, Integer> prev) {
        List<Integer> path = new ArrayList<Integer>();
        Integer cursor = Integer.valueOf(dest);
        while (cursor != null) {
            path.add(0, cursor.intValue());
            cursor = prev.get(cursor);
        }
        if (path.isEmpty() || path.get(0).intValue() != start) {
            return Collections.<Integer>emptyList();
        }
        return path;
    }

    private static boolean sameLine(int a, int b) {
        boolean passengerA = isPassengerSection(a);
        boolean passengerB = isPassengerSection(b);
        boolean freightA = isFreightSection(a);
        boolean freightB = isFreightSection(b);
        if (passengerA && passengerB) return true;
        if (freightA && freightB) return true;
        return (!passengerA && !freightA) && (!passengerB && !freightB); // default allow only if both unknown
    }
}

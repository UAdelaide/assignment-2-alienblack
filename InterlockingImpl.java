import java.util.*;

/**
 * Minimal, safe implementation scaffold + phase-1 movement (Java 8 compatible):
 * - Tracks which train is in which section (single-occupancy).
 * - Validates legal entry/exit sections.
 * - moveTrains: adjacency + single-occupancy, atomic apply, deny same-target conflicts.
 * - No Java 9+/11 APIs (uses Arrays.asList / Collections.emptyList and trim().isEmpty()).
 */
public class InterlockingImpl implements Interlocking {

    public enum Outcome {
        PROGRESS,
        BLOCKED,
        DEADLOCK
    }

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
    private static final Set<Integer> FAIR_MERGE_TARGETS = new HashSet<Integer>(Arrays.asList(6, 7, 9));
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

        // Precompute simple BFS routes between same-line sections
        Map<Integer, Map<Integer, List<Integer>>> routes = new HashMap<Integer, Map<Integer, List<Integer>>>();
        for (Integer s : NEXT_HOPS.keySet()) {
            Map<Integer, List<Integer>> to = new HashMap<Integer, List<Integer>>();
            for (Integer d : NEXT_HOPS.keySet()) {
                if (!sameLine(s, d)) continue;
                List<Integer> path = bfsPath(s, d);
                if (!path.isEmpty()) to.put(d, path);
            }
            routes.put(s, to);
        }
        ROUTES = Collections.unmodifiableMap(routes);
    }

    // Occupancy map: section -> trainName
    private final Map<Integer, String> sectionOwner = new HashMap<Integer, String>();
    /** trainName -> sectionId */
    private final Map<String, Integer> trainSection = new HashMap<String, Integer>();
    /** trainName -> destination sectionId */
    private final Map<String, Integer> trainDest = new HashMap<String, Integer>();
    /** trains that must exit on their next move */
    private final Set<String> exitReady = new HashSet<String>();
    /** fairness queues for non-crossing merges */
    private final Map<Integer, LinkedList<String>> mergeQueues = new HashMap<Integer, LinkedList<String>>();
    /** last movement outcome for diagnostics */
    private Outcome lastOutcome = Outcome.BLOCKED;

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

    private static boolean isPassengerSection(int s) { return PASSENGER_SECTIONS.contains(Integer.valueOf(s)); }
    private static boolean isFreightSection(int s)   { return FREIGHT_SECTIONS.contains(Integer.valueOf(s)); }

    private boolean isSectionFree(int s) {
        return !sectionOwner.containsKey(Integer.valueOf(s));
    }

    private void occupy(int s, String train) {
        sectionOwner.put(Integer.valueOf(s), train);
        trainSection.put(train, Integer.valueOf(s));
        exitReady.remove(train);
    }

    private void vacate(int s) {
        String t = sectionOwner.remove(Integer.valueOf(s));
        if (t != null) {
            trainSection.remove(t);
            exitReady.remove(t);
        }
    }

    // Crossing edges: passenger (1<->5, 2<->6) and freight (3<->4)
    private static boolean isCrossingEdge(int from, int to) {
        return (from == 1 && to == 5) || (from == 5 && to == 1)
                || (from == 2 && to == 6) || (from == 6 && to == 2)
                || (from == 3 && to == 4) || (from == 4 && to == 3);
    }

    private static String chooseCrossingWinner(List<String> trains) {
        for (String t : trains) {
            if (isPassenger(t)) {
                return t;
            }
        }
        return trains.get(0);
    }

    private boolean isFairMergeTarget(int section) {
        return FAIR_MERGE_TARGETS.contains(Integer.valueOf(section));
    }

    private void cleanupMergeQueue(int target, List<String> claimants) {
        if (!isFairMergeTarget(target)) {
            mergeQueues.remove(Integer.valueOf(target));
            return;
        }
        LinkedList<String> queue = mergeQueues.get(Integer.valueOf(target));
        if (queue == null) return;
        // Keep only active claimants, in order
        Iterator<String> it = queue.iterator();
        while (it.hasNext()) {
            String t = it.next();
            if (!claimants.contains(t)) it.remove();
        }
        if (queue.isEmpty()) mergeQueues.remove(Integer.valueOf(target));
    }

    private String pickFairWinner(int target, List<String> claimants) {
        if (!isFairMergeTarget(target)) return null;

        LinkedList<String> queue = mergeQueues.get(Integer.valueOf(target));
        if (queue == null) {
            queue = new LinkedList<String>();
            mergeQueues.put(Integer.valueOf(target), queue);
        }
        // Ensure all current claimants are queued (preserve order)
        for (String claimant : claimants) {
            if (!queue.contains(claimant)) {
                queue.addLast(claimant);
            }
        }
        Iterator<String> it = queue.iterator();
        while (it.hasNext()) {
            String candidate = it.next();
            if (claimants.contains(candidate)) {
                it.remove();
                queue.addLast(candidate);
                return candidate;
            }
        }
        return null;
    }

    private void removeFromQueues(String trainName) {
        for (Iterator<Map.Entry<Integer, LinkedList<String>>> it = mergeQueues.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, LinkedList<String>> entry = it.next();
            LinkedList<String> queue = entry.getValue();
            if (queue.remove(trainName) && queue.isEmpty()) {
                it.remove();
            }
        }
    }

    // ---- Routing helpers ----
    private static boolean hasRoute(int from, int dest) {
        Map<Integer, List<Integer>> destMap = ROUTES.get(Integer.valueOf(from));
        return destMap != null && destMap.containsKey(Integer.valueOf(dest));
    }

    private static int pickNext(int from, int dest) {
        if (from == dest) return from;
        Map<Integer, List<Integer>> destMap = ROUTES.get(Integer.valueOf(from));
        if (destMap == null) return from;
        List<Integer> path = destMap.get(Integer.valueOf(dest));
        if (path == null || path.isEmpty()) return from;
        if (path.size() >= 2) return path.get(1).intValue();
        return from;
    }

    // Basic BFS over same-line graph; prefers staying on line, not occupancy-aware
    private static List<Integer> bfsPath(int start, int dest) {
        if (start == dest) return Arrays.asList(start);
        Queue<Integer> q = new ArrayDeque<Integer>();
        Map<Integer,Integer> prev = new HashMap<Integer,Integer>();
        q.add(Integer.valueOf(start));
        prev.put(Integer.valueOf(start), null);
        while (!q.isEmpty()) {
            int u = q.remove().intValue();
            for (Integer vObj : NEXT_HOPS.getOrDefault(Integer.valueOf(u), Collections.<Integer>emptyList())) {
                int v = vObj.intValue();
                if (!sameLine(start, v)) continue;
                if (prev.containsKey(Integer.valueOf(v))) continue;
                prev.put(Integer.valueOf(v), Integer.valueOf(u));
                if (v == dest) {
                    List<Integer> path = new ArrayList<Integer>();
                    Integer cursor = Integer.valueOf(v);
                    while (cursor != null) {
                        path.add(0, cursor.intValue());
                        cursor = prev.get(cursor);
                    }
                    return path;
                }
                q.add(Integer.valueOf(v));
            }
        }
        // Reconstruct and verify (no path found)
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
        boolean pA = PASSENGER_SECTIONS.contains(a), pB = PASSENGER_SECTIONS.contains(b);
        boolean fA = FREIGHT_SECTIONS.contains(a),   fB = FREIGHT_SECTIONS.contains(b);
        return (pA && pB) || (fA && fB);
    }

    // ================= Interface methods =================

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
        Map<String,Integer> exitPositions = new HashMap<String,Integer>();
        Map<String,Integer> desired = new LinkedHashMap<String,Integer>();
        Map<String,int[]> desiredEdges = new HashMap<String,int[]>();

        boolean anyRequests = false;
        boolean blockedByOccupancy = false;
        boolean blockedByPolicy = false;

        // Build desired moves
        for (String t : trainNames) {
            Integer pos = trainSection.get(t);
            if (pos == null) continue;
            int from = pos.intValue();
            int dest = trainDest.get(t);
            if (exitReady.contains(t)) {
                // next move is exit: record for later
                exiting.add(t);
                anyRequests = true;
                exitPositions.put(t, Integer.valueOf(from));
                continue;
            }

            int next = pickNext(from, dest);
            if (next == from) continue;           // no progress
            anyRequests = true;
            if (!isSectionFree(next)) {
                blockedByOccupancy = true;
                continue;   // blocked by occupancy
            }
            desired.put(t, Integer.valueOf(next));
            desiredEdges.put(t, new int[]{from, next});
        }

        // Crossing arbitration: only one train can own the diamond per tick (passenger priority)
        List<String> crossingTrains = new ArrayList<String>();
        for (Map.Entry<String,int[]> entry : desiredEdges.entrySet()) {
            int[] edge = entry.getValue();
            if (isCrossingEdge(edge[0], edge[1])) {
                crossingTrains.add(entry.getKey());
            }
        }
        if (crossingTrains.size() > 1) {
            String winner = chooseCrossingWinner(crossingTrains);
            for (String t : crossingTrains) {
                if (!t.equals(winner)) {
                    desired.remove(t);
                    desiredEdges.remove(t);
                    blockedByPolicy = true;
                }
            }
        }

        Map<Integer,List<String>> targetClaims = new HashMap<Integer,List<String>>();
        for (Map.Entry<String,Integer> en : desired.entrySet()) {
            int to = en.getValue().intValue();
            List<String> list = targetClaims.get(Integer.valueOf(to));
            if (list == null) {
                list = new ArrayList<String>();
                targetClaims.put(Integer.valueOf(to), list);
            }
            list.add(en.getKey());
        }
        for (Map.Entry<Integer,List<String>> en : targetClaims.entrySet()) {
            int target = en.getKey().intValue();
            List<String> claimants = en.getValue();
            cleanupMergeQueue(target, claimants);
            if (claimants.size() == 1) {
                continue;
            }
            if (isFairMergeTarget(target)) {
                String winner = pickFairWinner(target, claimants);
                if (winner != null) {
                    for (String t : new ArrayList<String>(claimants)) {
                        if (!t.equals(winner)) {
                            desired.remove(t);
                            desiredEdges.remove(t);
                            blockedByPolicy = true;
                        }
                    }
                }
                continue;
            }
            // non-fair targets: deny everyone this tick
            for (String t : new ArrayList<String>(claimants)) {
                desired.remove(t);
                desiredEdges.remove(t);
            }
            blockedByPolicy = true;
        }

        // Apply moves atomically
        for (Map.Entry<String,Integer> en : desired.entrySet()) {
            String t = en.getKey();
            int to = en.getValue().intValue();
            int from = trainSection.get(t).intValue();
            // sanity: check still free
            if (!isSectionFree(to)) {
                blockedByOccupancy = true;
                continue;
            }
            // move
            sectionOwner.remove(Integer.valueOf(from));
            occupy(to, t);

            // If reached destination, mark to exit on next move
            if (to == trainDest.get(t)) {
                exitReady.add(t);
                removeFromQueues(t);
            }
            moved++;
        }

        // Process exits after movement
        // Exit-ready trains drop out after everyone else has been processed,
        // so followers still observe the section as occupied during arbitration.
        for (String t : exiting) {
            Integer section = exitPositions.get(t);
            if (section != null) {
                vacate(section.intValue());
            }
            exitReady.remove(t);
            trainDest.remove(t);
            removeFromQueues(t);
            moved++;
        }

        // Outcome classification (refined)
        if (moved > 0) {
            lastOutcome = Outcome.PROGRESS;
        } else if (anyRequests && blockedByOccupancy) {
            lastOutcome = Outcome.DEADLOCK;
        } else if (anyRequests && blockedByPolicy) {
            lastOutcome = Outcome.BLOCKED;
        } else {
            lastOutcome = Outcome.BLOCKED;
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
        Integer s = trainSection.get(trainName);
        if (s == null) throw new IllegalArgumentException("Unknown train: " + trainName);
        return s.intValue();
    }

    public Outcome getLastOutcome() {
        return lastOutcome;
    }
}


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
        DEADLOCK,
        COLLISION
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
    private static final Set<Integer> FAIR_MERGE_TARGETS = new HashSet<Integer>(Arrays.asList(6, 7));
    static {
        NEXT_HOPS.put(1, Arrays.asList(5));
        NEXT_HOPS.put(3, Arrays.asList(4, 7));
        NEXT_HOPS.put(4, Arrays.asList(3));
        NEXT_HOPS.put(5, Arrays.asList(8, 9));
        NEXT_HOPS.put(6, Arrays.asList(2));
        NEXT_HOPS.put(7, Arrays.asList(3, 11));
        NEXT_HOPS.put(9, Arrays.asList(6));
        NEXT_HOPS.put(10, Arrays.asList(6));
        NEXT_HOPS.put(11, Arrays.asList(7));
        ROUTES = computeRoutes();
    }

    // ---- State
    /** sectionId -> trainName */
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
    private static boolean isPassengerSection(int section) { return PASSENGER_SECTIONS.contains(section); }
    private static boolean isFreightSection(int section)   { return FREIGHT_SECTIONS.contains(section); }
    private static boolean isExitSection(int section) {
        return SOUTH_EXIT.contains(section) || NORTH_EXIT.contains(section);
    }

    // ---- Helpers (occupancy + movement)
    private boolean isSectionFree(int sectionId) {
        return !sectionOwner.containsKey(sectionId);
    }
    private void occupy(int sectionId, String trainName) {
        sectionOwner.put(sectionId, trainName);
        trainSection.put(trainName, sectionId);
        updateExitReadiness(trainName);
    }
    private void vacate(int sectionId) {
        String t = sectionOwner.remove(sectionId);
        if (t != null) {
            trainSection.remove(t);
            exitReady.remove(t);
        }
    }
    private int pickNext(int from, int dest) {
        if (from == dest) return from;
        Map<Integer, List<Integer>> destMap = ROUTES.get(from);
        if (destMap == null) return from;
        List<Integer> path = destMap.get(dest);
        if (path == null || path.size() < 2) return from;
        return path.get(1);
    }

    private void updateExitReadiness(String trainName) {
        Integer section = trainSection.get(trainName);
        Integer dest = trainDest.get(trainName);
        if (section != null && dest != null && section.intValue() == dest.intValue()) {
            exitReady.add(trainName);
        } else {
            exitReady.remove(trainName);
        }
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
    }

    @Override
    public int moveTrains(String[] trainNames) throws IllegalArgumentException {
        if (trainNames == null) {
            throw new IllegalArgumentException("trainNames must not be null");
        }
        // validate inputs and detect duplicates
        Set<String> batch = new LinkedHashSet<String>();
        for (String name : trainNames) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("train name in batch is empty");
            }
            if (!trainDest.containsKey(name)) {
                throw new IllegalArgumentException("train not active in corridor: " + name);
            }
            if (!batch.add(name)) {
                throw new IllegalArgumentException("duplicate train in batch: " + name);
            }
        }

        // record trains that will exit in this cycle
        Set<String> exitingTrains = new HashSet<String>();
        Map<String,Integer> exitPositions = new HashMap<String,Integer>();
        for (String t : batch) {
            if (exitReady.contains(t)) {
                Integer pos = trainSection.get(t);
                if (pos != null) {
                    exitingTrains.add(t);
                    exitPositions.put(t, pos);
                }
            }
        }

        // compute tentative next moves for every train
        Map<String,Integer> nextSection = new HashMap<String,Integer>();
        boolean anyRequests = false;
        for (String t : batch) {
            if (exitingTrains.contains(t)) {
                nextSection.put(t, null);
                anyRequests = true;
                continue;
            }
            int from = trainSection.get(t);
            int dest = trainDest.get(t);
            int next = pickNext(from, dest);
            if (next != from) {
                nextSection.put(t, Integer.valueOf(next));
                anyRequests = true;
            }
        }

        // start with every train that requested a move and iteratively prune the ones
        // whose targets will not be vacated by an exiting or moving train
        Set<String> candidates = new LinkedHashSet<String>();
        for (String t : batch) {
            if (nextSection.get(t) != null) {
                candidates.add(t);
            }
        }
        boolean changed;
        do {
            changed = false;
            for (Iterator<String> it = candidates.iterator(); it.hasNext(); ) {
                String t = it.next();
                Integer target = nextSection.get(t);
                if (target == null) {
                    it.remove();
                    changed = true;
                    continue;
                }
                String occupier = sectionOwner.get(target.intValue());
                if (occupier != null
                        && !exitingTrains.contains(occupier)
                        && !candidates.contains(occupier)) {
                    it.remove();
                    changed = true;
                }
            }
        } while (changed);

        boolean blockedByOccupancy = false;
        boolean dependencyCycle = false;
        for (String t : batch) {
            if (exitingTrains.contains(t)) {
                continue;
            }
            Integer target = nextSection.get(t);
            if (target == null) {
                continue;
            }
            if (!candidates.contains(t)) {
                String occupier = sectionOwner.get(target.intValue());
                if (occupier != null && !exitingTrains.contains(occupier)) {
                    blockedByOccupancy = true;
                    if (batch.contains(occupier)
                            && nextSection.get(occupier) != null
                            && !candidates.contains(occupier)) {
                        dependencyCycle = true;
                    }
                }
            }
        }

        Map<String,Integer> candidateTargets = new LinkedHashMap<String,Integer>();
        for (String t : batch) {
            if (candidates.contains(t)) {
                Integer target = nextSection.get(t);
                if (target != null) {
                    candidateTargets.put(t, target);
                }
            }
        }

        // detect swap collisions among scheduled moves
        Set<String> collisionTrains = new HashSet<String>();
        for (Map.Entry<String,Integer> entry : candidateTargets.entrySet()) {
            String t = entry.getKey();
            int target = entry.getValue().intValue();
            String occupier = sectionOwner.get(target);
            if (occupier != null && candidateTargets.containsKey(occupier)) {
                int occupierTarget = candidateTargets.get(occupier).intValue();
                int fromT = trainSection.get(t);
                if (occupierTarget == fromT) {
                    collisionTrains.add(t);
                    collisionTrains.add(occupier);
                }
            }
        }
        boolean collisionDetected = !collisionTrains.isEmpty();
        if (collisionDetected) {
            for (String t : collisionTrains) {
                candidateTargets.remove(t);
                candidates.remove(t);
            }
        }

        Map<String,int[]> desiredEdges = new HashMap<String,int[]>();
        for (Map.Entry<String,Integer> entry : candidateTargets.entrySet()) {
            String t = entry.getKey();
            int from = trainSection.get(t);
            int to = entry.getValue().intValue();
            desiredEdges.put(t, new int[]{from, to});
        }

        // handle crossing conflicts: only one train may traverse a crossing edge in a cycle
        boolean blockedByPolicy = false;
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
                    candidateTargets.remove(t);
                    desiredEdges.remove(t);
                    blockedByPolicy = true;
                }
            }
        }

        // handle merge fairness (6 and 7): if multiple trains target same merge section, choose fairly
        Map<Integer,List<String>> targetClaims = new HashMap<Integer,List<String>>();
        for (Map.Entry<String,Integer> entry : candidateTargets.entrySet()) {
            int target = entry.getValue().intValue();
            targetClaims.computeIfAbsent(target, k -> new ArrayList<String>()).add(entry.getKey());
        }
        Map<String,Integer> granted = new LinkedHashMap<String,Integer>();
        for (Map.Entry<Integer,List<String>> entry : targetClaims.entrySet()) {
            int target = entry.getKey().intValue();
            List<String> claimants = entry.getValue();
            cleanupMergeQueue(target, claimants);
            if (claimants.size() == 1) {
                granted.put(claimants.get(0), Integer.valueOf(target));
            } else {
                blockedByPolicy = true;
                if (isFairMergeTarget(target)) {
                    String winner = selectFairWinner(target, claimants);
                    if (winner != null) {
                        granted.put(winner, Integer.valueOf(target));
                    }
                }
            }
        }

        // prune moves whose targets will remain occupied
        boolean pruned;
        do {
            pruned = false;
            for (Iterator<Map.Entry<String,Integer>> it = granted.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String,Integer> entry = it.next();
                String t = entry.getKey();
                int target = entry.getValue().intValue();
                String occupier = sectionOwner.get(target);
                if (occupier != null
                        && !exitingTrains.contains(occupier)
                        && !granted.containsKey(occupier)) {
                    it.remove();
                    pruned = true;
                    blockedByOccupancy = true;
                }
            }
        } while (pruned);

        Map<String,Integer> moveFrom = new LinkedHashMap<String,Integer>();
        for (Map.Entry<String,Integer> entry : granted.entrySet()) {
            moveFrom.put(entry.getKey(), trainSection.get(entry.getKey()));
        }

        int moved = 0;
        for (Map.Entry<String,Integer> entry : moveFrom.entrySet()) {
            vacate(entry.getValue().intValue());
        }
        for (String t : exitingTrains) {
            Integer section = exitPositions.get(t);
            if (section != null) {
                vacate(section.intValue());
            }
        }
        for (Map.Entry<String,Integer> entry : granted.entrySet()) {
            String t = entry.getKey();
            int to = entry.getValue().intValue();
            occupy(to, t);
            moved++;
            Integer dest = trainDest.get(t);
            if (dest != null && to == dest.intValue()) {
                removeFromQueues(t);
            }
        }
        for (String t : exitingTrains) {
            exitReady.remove(t);
            trainDest.remove(t);
            removeFromQueues(t);
            moved++;
        }

        if (moved > 0) {
            lastOutcome = Outcome.PROGRESS;
        } else if (!anyRequests) {
            lastOutcome = Outcome.BLOCKED;
        } else if (collisionDetected) {
            lastOutcome = Outcome.COLLISION;
        } else if (dependencyCycle) {
            lastOutcome = Outcome.DEADLOCK;
        } else if (blockedByPolicy || blockedByOccupancy) {
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
        Integer sec = trainSection.get(trainName);
        if (sec == null) throw new IllegalArgumentException("unknown train: " + trainName);
        return sec;
    }

    public Outcome getLastOutcome() {
        return lastOutcome;
    }

    private static boolean isCrossingEdge(int from, int to) {
        return (from == 1 && to == 5) || (from == 5 && to == 1)
                || (from == 2 && to == 6) || (from == 6 && to == 2)
                || (from == 3 && to == 4) || (from == 4 && to == 3);
    }

    private String chooseCrossingWinner(List<String> trains) {
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
        for (Iterator<String> it = queue.iterator(); it.hasNext(); ) {
            String t = it.next();
            if (!claimants.contains(t)) {
                it.remove();
            }
        }
        if (queue.isEmpty()) {
            mergeQueues.remove(Integer.valueOf(target));
        }
    }

    private String selectFairWinner(int target, List<String> claimants) {
        LinkedList<String> queue = mergeQueues.get(Integer.valueOf(target));
        if (queue == null) {
            queue = new LinkedList<String>();
            mergeQueues.put(Integer.valueOf(target), queue);
        }
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
        boolean pA = PASSENGER_SECTIONS.contains(a), pB = PASSENGER_SECTIONS.contains(b);
        boolean fA = FREIGHT_SECTIONS.contains(a),   fB = FREIGHT_SECTIONS.contains(b);
        return (pA && pB) || (fA && fB);
    }
}

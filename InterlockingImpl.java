
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
        exitReady.remove(trainName);
    }
    private void vacate(int sectionId) {
        String t = sectionOwner.remove(sectionId);
        if (t != null) trainSection.remove(t);
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
        if (entryTrackSection == destinationTrackSection && isExitSection(destinationTrackSection)) exitReady.add(trainName);
    }

    @Override
    public int moveTrains(String[] trainNames) throws IllegalArgumentException {
        if (trainNames == null) {
            throw new IllegalArgumentException("trainNames must not be null");
        }
        // validate inputs and detect duplicates
        Set<String> seen = new HashSet<String>();
        for (String name : trainNames) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("train name in batch is empty");
            }
            if (!trainDest.containsKey(name)) {
                throw new IllegalArgumentException("train not active in corridor: " + name);
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("duplicate train in batch: " + name);
            }
        }

        // record trains that will exit in this cycle and pre-vacate their sections for planning
        Set<String> exitingTrains = new HashSet<String>();
        Map<String,Integer> exitPositions = new HashMap<String,Integer>();
        // copy current ownership map for planning (we'll modify a local copy when simulating moves)
        Map<Integer,String> currentOwner = new HashMap<Integer,String>(sectionOwner);
        // pre-vacate exit-ready sections in the planning map
        for (String t : trainNames) {
            if (exitReady.contains(t)) {
                Integer pos = trainSection.get(t);
                if (pos != null) {
                    exitingTrains.add(t);
                    exitPositions.put(t, pos);
                    // remove from planning occupancy so that other trains can plan to move into it
                    currentOwner.remove(pos);
                }
            }
        }

        // compute tentative next moves for every train
        Map<String,Integer> nextSection = new HashMap<String,Integer>();
        boolean anyRequests = false;
        for (String t : trainNames) {
            if (exitingTrains.contains(t)) {
                // exit trains have no next section (they leave the map)
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

        // build initial candidate moves, allowing moves into sections whose occupier is either exiting or also moving away
        Map<String,Integer> candidates = new LinkedHashMap<String,Integer>();
        boolean blockedByOccupancy = false;
        for (String t : trainNames) {
            Integer next = nextSection.get(t);
            if (next == null) {
                continue;
            }
            int target = next.intValue();
            String occupier = currentOwner.get(target);
            if (occupier == null) {
                // completely free
                candidates.put(t, Integer.valueOf(target));
                // update planning occupancy: mark this train as moving away and occupying its target
                int from = trainSection.get(t);
                currentOwner.remove(from);
                currentOwner.put(target, t);
            } else if (exitingTrains.contains(occupier)) {
                // target is occupied by an exiting train; allow move
                candidates.put(t, Integer.valueOf(target));
                int from = trainSection.get(t);
                currentOwner.remove(from);
                currentOwner.put(target, t);
            } else {
                // target may be occupied by another train; defer decision to check if that train is also moving away
                // we will check swap/collision conditions later
                blockedByOccupancy = true;
            }
        }

        // allow moves into sections whose current occupier is also scheduled to move away
        // second pass to handle chain moves regardless of input order
        for (String t : trainNames) {
            // skip if already a candidate or exiting
            if (candidates.containsKey(t) || exitingTrains.contains(t)) {
                continue;
            }
            Integer next = nextSection.get(t);
            if (next == null) {
                continue;
            }
            int target = next.intValue();
            // check if target is occupied by a train whose next move is not null (i.e., is moving away)
            String occupier = sectionOwner.get(target);
            if (occupier != null) {
                Integer occupierNext = nextSection.get(occupier);
                if (occupierNext != null) {
                    // occupier will move; allow this move
                    candidates.put(t, Integer.valueOf(target));
                }
            }
        }

        // detect swap collisions: two trains wanting to swap each other's sections
        Set<String> collisionTrains = new HashSet<String>();
        for (Map.Entry<String,Integer> e : candidates.entrySet()) {
            String t = e.getKey();
            int target = e.getValue().intValue();
            String occupier = sectionOwner.get(target);
            if (occupier != null) {
                Integer occupierTarget = candidates.get(occupier);
                if (occupierTarget != null) {
                    int occupierNext = occupierTarget.intValue();
                    int fromT = trainSection.get(t);
                    if (occupierNext == fromT) {
                        // t and occupier want to swap
                        collisionTrains.add(t);
                        collisionTrains.add(occupier);
                    }
                }
            }
        }
        if (!collisionTrains.isEmpty()) {
            // remove both trains involved in collision from candidates
            for (String t : collisionTrains) {
                candidates.remove(t);
            }
        }

        // build desiredEdges for candidates
        Map<String,int[]> desiredEdges = new HashMap<String,int[]>();
        for (Map.Entry<String,Integer> entry : candidates.entrySet()) {
            String t = entry.getKey();
            int from = trainSection.get(t);
            int to = entry.getValue().intValue();
            desiredEdges.put(t, new int[]{from, to});
        }

        // handle crossing conflicts: only one train may traverse a crossing edge in a cycle
        List<String> crossingTrains = new ArrayList<String>();
        for (Map.Entry<String,int[]> entry : desiredEdges.entrySet()) {
            int[] edge = entry.getValue();
            if (isCrossingEdge(edge[0], edge[1])) {
                crossingTrains.add(entry.getKey());
            }
        }
        boolean blockedByPolicy = false;
        if (crossingTrains.size() > 1) {
            String winner = chooseCrossingWinner(crossingTrains);
            for (String t : crossingTrains) {
                if (!t.equals(winner)) {
                    candidates.remove(t);
                    desiredEdges.remove(t);
                    blockedByPolicy = true;
                }
            }
        }

        // handle merge fairness (6 and 7): if multiple trains target same merge section, choose fairly
        Map<Integer,List<String>> targetClaims = new HashMap<Integer,List<String>>();
        for (String t : candidates.keySet()) {
            int target = candidates.get(t).intValue();
            targetClaims.computeIfAbsent(target, k -> new ArrayList<String>()).add(t);
        }
        Map<String,Integer> granted = new HashMap<String,Integer>();
        for (Map.Entry<Integer,List<String>> entry : targetClaims.entrySet()) {
            int target = entry.getKey().intValue();
            List<String> claimants = entry.getValue();
            cleanupMergeQueue(target, claimants);
            if (claimants.size() == 1) {
                granted.put(claimants.get(0), Integer.valueOf(target));
            } else {
                if (isFairMergeTarget(target)) {
                    String winner = selectFairWinner(target, claimants);
                    if (winner != null) {
                        granted.put(winner, Integer.valueOf(target));
                        if (claimants.size() > 1) {
                            blockedByPolicy = true;
                        }
                    }
                } else {
                    // deny all for non-fair merge targets
                    blockedByPolicy = true;
                }
            }
        }

        // apply granted moves atomically
        int moved = 0;
        for (Map.Entry<String,Integer> e : granted.entrySet()) {
            String t = e.getKey();
            int to = e.getValue().intValue();
            int from = trainSection.get(t);
            // ensure the target is still free or occupied by this train (in case of chain moves)
            if (!isSectionFree(to) && !t.equals(sectionOwner.get(to)) && !exitingTrains.contains(sectionOwner.get(to))) {
                continue;
            }
            vacate(from);
            occupy(to, t);
            moved++;
            Integer dest = trainDest.get(t);
            if (dest != null && to == dest.intValue()) {
                if (isExitSection(dest.intValue())) {
                    exitReady.add(t);
                }
                removeFromQueues(t);
            }
        }
        // finally process exits
        for (String t : exitingTrains) {
            Integer section = exitPositions.get(t);
            if (section != null) {
                vacate(section.intValue());
            }
            exitReady.remove(t);
            trainDest.remove(t);
            removeFromQueues(t);
            moved++;
        }
        // set lastOutcome appropriately
        if (moved > 0) {
            lastOutcome = Outcome.PROGRESS;
        } else if (anyRequests && !candidates.isEmpty() && collisionTrains.isEmpty()) {
            // if no one moved but there were requests and no collisions, it's a deadlock
            lastOutcome = Outcome.DEADLOCK;
        } else if (anyRequests && (blockedByPolicy || !collisionTrains.isEmpty() || blockedByOccupancy)) {
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

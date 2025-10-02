import java.util.*;

/**
 * Minimal, safe implementation scaffold:
 * - Tracks which train is in which section (single-occupancy).
 * - Validates legal entry/exit sections.
 * - Exposes getSection/getTrain per the interface.
 * - moveTrains(...) currently only validates input and returns 0 (no movement yet).
 */
public class InterlockingImpl implements Interlocking {

    // ---- Assignment-specific entry/exit constraints
    private static final Set<Integer> SOUTH_ENTRY = Set.of(1, 3);
    private static final Set<Integer> SOUTH_EXIT  = Set.of(4, 8, 9, 11);
    private static final Set<Integer> NORTH_ENTRY = Set.of(4, 9, 10, 11);
    private static final Set<Integer> NORTH_EXIT  = Set.of(2, 3);

    // If you later need routing, populate NEXT_HOPS with the track graph.
    private static final Map<Integer, List<Integer>> NEXT_HOPS = new HashMap<>();

    // ---- State
    /** sectionId -> trainName (occupied) */
    private final Map<Integer, String> sectionOwner = new HashMap<>();
    /** trainName -> sectionId (current location) */
    private final Map<String, Integer> trainSection = new HashMap<>();
    /** trainName -> destination sectionId */
    private final Map<String, Integer> trainDest = new HashMap<>();

    // ---- Helpers
    private static boolean isSouthbound(int entry, int dest) {
        if (SOUTH_ENTRY.contains(entry)) return true;
        if (NORTH_ENTRY.contains(entry)) return false;
        if (SOUTH_EXIT.contains(dest)) return true;
        if (NORTH_EXIT.contains(dest)) return false;
        return true; // default; addTrain will still validate
    }

    private static boolean isValidEntry(boolean southbound, int section) {
        return southbound ? SOUTH_ENTRY.contains(section) : NORTH_ENTRY.contains(section);
    }

    private static boolean isValidExit(boolean southbound, int section) {
        return southbound ? SOUTH_EXIT.contains(section) : NORTH_EXIT.contains(section);
    }

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
            throw new IllegalArgumentException("Illegal entry section for direction: " + entryTrackSection);
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

        Set<String> seen = new HashSet<>();
        for (String name : trainNames) {
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("train name in batch is empty");
            if (!trainSection.containsKey(name))
                throw new IllegalArgumentException("unknown train: " + name);
            if (!seen.add(name))
                throw new IllegalArgumentException("duplicate train in batch: " + name);
        }

        // TODO: implement real movement; for now, no changes.
        return 0;
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

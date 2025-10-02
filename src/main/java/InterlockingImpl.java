import java.util.Set;
import java.util.HashSet;

public class InterlockingImpl implements Interlocking {
    // South can ENTER at 1 or 3; EXIT at 4,8,9,11
    private static final Set<Integer> SOUTH_ENTRY = Set.of(1,3);
    private static final Set<Integer> SOUTH_EXIT  = Set.of(4,8,9,11);
    // North can ENTER at 4,9,10,11; EXIT at 2 or 3
    private static final Set<Integer> NORTH_ENTRY = Set.of(4,9,10,11);
    private static final Set<Integer> NORTH_EXIT  = Set.of(2,3);

    private boolean isValidEntry(boolean fromSouth, int section) {
        return fromSouth ? SOUTH_ENTRY.contains(section) : NORTH_ENTRY.contains(section);
    }
    private boolean isValidExit(boolean toSouth, int section) {
        return toSouth ? SOUTH_EXIT.contains(section) : NORTH_EXIT.contains(section);
    }

    @Override
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection) throws IllegalArgumentException, IllegalStateException {

    }

    @Override
    public int moveTrains(String[] trainNames) throws IllegalArgumentException {
        return 0;
    }

    @Override
    public String getSection(int trackSection) throws IllegalArgumentException {
        return null;
    }

    @Override
    public int getTrain(String trainName) throws IllegalArgumentException {
        return 0;
    }

    // TODO: wherever trains are created/enter or exit, enforce these sets.
    // TODO: keep passenger vs freight segregation per your design.
}

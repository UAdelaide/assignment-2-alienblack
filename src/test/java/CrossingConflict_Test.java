import org.junit.Test;
import static org.junit.Assert.*;

public class CrossingConflict_Test {
    @Test
    public void freightProceedsAfterPassengerClearsCrossing() {
        Interlocking sys = new InterlockingImpl();

        sys.addTrain("P1", 1, 8); // passenger will take edge 1->5
        sys.addTrain("F7", 3, 4); // freight taking edge 3->4

        int moved = sys.moveTrains(new String[]{"P1","F7"});
        assertEquals(1, moved);
        assertEquals(5, sys.getTrain("P1"));
        assertEquals(3, sys.getTrain("F7"));

        // Passenger clears the crossing area on the next move
        sys.moveTrains(new String[]{"P1"});
        assertEquals(8, sys.getTrain("P1"));

        // Now freight should be able to proceed through the crossing
        moved = sys.moveTrains(new String[]{"F7"});
        assertEquals(1, moved);
        assertEquals(4, sys.getTrain("F7"));
    }
}

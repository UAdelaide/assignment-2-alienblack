import org.junit.Test;
import static org.junit.Assert.*;

public class MovePhase1_Test {

    @Test
    public void movesIntoFreeAdjacentSection() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("A", 1, 4);                // path: 1 -> 2 -> 4
        int moved = sys.moveTrains(new String[]{"A"});
        assertEquals(1, moved);
        assertEquals(2, sys.getTrain("A"));
    }

    @Test
    public void deniesMoveIntoOccupiedTarget() {
        Interlocking sys = new InterlockingImpl();

        // Occupy section 2 legally: enter at 3 then move once (3 -> 2)
        sys.addTrain("B", 3, 4);                  // legal south entry
        int movedB = sys.moveTrains(new String[]{"B"});
        assertEquals(1, movedB);
        assertEquals(2, sys.getTrain("B"));       // B now occupies 2

        // Now A tries to move from 1 -> 2 but should be blocked by occupancy
        sys.addTrain("A", 1, 4);                  // legal south entry
        int moved = sys.moveTrains(new String[]{"A"}); // don't move B this tick
        assertEquals(0, moved);                    // A blocked
        assertEquals(1, sys.getTrain("A"));       // A stays at 1
        assertEquals(2, sys.getTrain("B"));       // B still at 2
    }


    @Test
    public void conflictingClaimsDenyAll() {
        // Only if you later add a fork where two trains claim the same target.
        // For now, this is a placeholder to expand when NEXT_HOPS has a fan-in.
        assertTrue(true);
    }
}

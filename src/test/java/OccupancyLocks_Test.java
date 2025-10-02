import org.junit.Test;
import static org.junit.Assert.*;

public class OccupancyLocks_Test {

    @Test
    public void cannotAddTwoTrainsIntoSameEntrySection() {
        Interlocking sys = new InterlockingImpl();
        // Valid southbound example: enter at 1, dest 4 (adjust if your map differs)
        sys.addTrain("P1", 1, 4);
        try {
            sys.addTrain("P2", 1, 8);
            fail("should not allow second train in same section");
        } catch (IllegalStateException expected) { /* ok */ }
    }

    @Test
    public void getSectionAndGetTrainReflectState() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("X", 3, 8);
        assertEquals("X", sys.getSection(3));
        assertEquals(3, sys.getTrain("X"));
        assertNull(sys.getSection(2));
    }
}

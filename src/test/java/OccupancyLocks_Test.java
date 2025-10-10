import org.junit.Test;
import static org.junit.Assert.*;

public class OccupancyLocks_Test {

    @Test
    public void cannotAddTwoTrainsIntoSameEntrySection() {
        Interlocking sys = new InterlockingImpl();
        // Valid southbound example: passenger enters at 1, aiming for exit 8.
        sys.addTrain("P1", 1, 8);
        try {
            sys.addTrain("P2", 1, 9);
            fail("should not allow second train in same section");
        } catch (IllegalStateException expected) { /* ok */ }
    }

    @Test
    public void getSectionAndGetTrainReflectState() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("F1", 3, 11);
        assertEquals("F1", sys.getSection(3));
        assertEquals(3, sys.getTrain("F1"));
        assertNull(sys.getSection(5));
    }
}

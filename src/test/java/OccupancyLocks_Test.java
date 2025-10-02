import org.junit.Test;
import static org.junit.Assert.*;

public class OccupancyLocks_Test {

    @Test
    public void twoTrainsCannotShareASection() {
        Interlocking sys = new InterlockingImpl();

        // TODO: replace with your real API from Interlocking.java
        // e.g., sys.addTrain("P1", /*enter at*/ 1); sys.addTrain("F7", 1);
        // For now, keep placeholders so file compiles once you wire real calls.

        // Pseudocode (adapt to API):
        // sys.placeTrain("T1", /*section*/ 4);
        // boolean t2Placed = sys.tryPlaceTrain("T2", /*section*/ 4);
        // assertFalse("Second train must not be allowed into occupied section", t2Placed);

        assertTrue(true); // remove after wiring real calls
    }

    @Test
    public void moveDeniedWhenTargetOccupied() {
        Interlocking sys = new InterlockingImpl();

        // Pseudocode (adapt):
        // sys.placeTrain("A", 3);
        // sys.placeTrain("B", 4);
        // sys.requestMove("A", /*to*/ 4);   // should be denied
        // sys.tick();
        // assertEquals(3, sys.whereIs("A"));
        // assertEquals(4, sys.whereIs("B"));

        assertTrue(true); // remove after wiring real calls
    }
}

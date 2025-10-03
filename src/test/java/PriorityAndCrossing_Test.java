import org.junit.Test;
import static org.junit.Assert.*;

public class PriorityAndCrossing_Test {

    @Test
    public void passengerWinsAtCrossing() {
        Interlocking sys = new InterlockingImpl();
        // Our mini graph: 1->2 and 3->2, so both want section 2 this tick.
        sys.addTrain("P1", 1, 4); // passenger approaching 2
        sys.addTrain("F7", 3, 4); // freight approaching 2

        int moved = sys.moveTrains(new String[]{"P1","F7"});
        assertEquals(1, moved);
        assertEquals(2, sys.getTrain("P1"));  // passenger advanced into 2
        assertEquals(3, sys.getTrain("F7"));  // freight stayed
    }

    @Test
    public void freightProceedsWhenNoPassenger() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("F7", 3, 4); // freight alone to 2
        int moved = sys.moveTrains(new String[]{"F7"});
        assertEquals(1, moved);
        assertEquals(2, sys.getTrain("F7"));
    }
}

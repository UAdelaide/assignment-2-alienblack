import org.junit.Test;
import static org.junit.Assert.*;

public class PriorityAndCrossing_Test {

    @Test
    public void passengerWinsAtCrossing() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("P1", 1, 8); // passenger approaching crossing edge 1->5
        sys.addTrain("F7", 3, 4); // freight approaching crossing edge 3->4

        int moved = sys.moveTrains(new String[]{"P1","F7"});
        assertEquals(1, moved);
        assertEquals(5, sys.getTrain("P1"));  // passenger advanced
        assertEquals(3, sys.getTrain("F7"));  // freight stayed
    }

    @Test
    public void freightProceedsWhenNoPassenger() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("F7", 3, 4);
        int moved = sys.moveTrains(new String[]{"F7"});
        assertEquals(1, moved);
        assertEquals(4, sys.getTrain("F7"));
    }
}

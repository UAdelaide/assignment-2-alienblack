import org.junit.Test;
import static org.junit.Assert.*;

public class CrossingConflict_Test {
    @Test
    public void twoFreightsContendForCrossing_BothDenied() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("F1", 1, 4); // wants 2
        sys.addTrain("F2", 3, 4); // wants 2
        int moved = sys.moveTrains(new String[]{"F1","F2"});
        assertEquals(0, moved);
        assertEquals(1, sys.getTrain("F1"));
        assertEquals(3, sys.getTrain("F2"));
    }

    @Test
    public void passengerBeatsFreightAtCrossing() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("P1", 1, 4);
        sys.addTrain("F2", 3, 4);
        int moved = sys.moveTrains(new String[]{"P1","F2"});
        assertEquals(1, moved);
        assertEquals(2, sys.getTrain("P1"));
        assertEquals(3, sys.getTrain("F2"));
    }
}

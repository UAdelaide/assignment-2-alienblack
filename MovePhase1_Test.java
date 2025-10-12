import org.junit.Test;
import static org.junit.Assert.*;

public class MovePhase1_Test {

    @Test
    public void passengerFollowsMainlinePath() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("P1", 1, 8); // passenger route: 1 -> 5 -> 8

        int moved = sys.moveTrains(new String[]{"P1"});
        assertEquals(1, moved);
        assertEquals(5, sys.getTrain("P1"));

        moved = sys.moveTrains(new String[]{"P1"});
        assertEquals(1, moved);
        assertEquals(8, sys.getTrain("P1"));
    }

    @Test
    public void freightStaysOnFreightLine() {
        Interlocking sys = new InterlockingImpl();

        sys.addTrain("F1", 3, 11); // freight route: 3 -> 7 -> 11
        int moved = sys.moveTrains(new String[]{"F1"});
        assertEquals(1, moved);
        assertEquals(7, sys.getTrain("F1"));

        moved = sys.moveTrains(new String[]{"F1"});
        assertEquals(1, moved);
        assertEquals(11, sys.getTrain("F1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void passengerCannotTargetFreightSection() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("P1", 1, 11); // illegal: passenger into freight exit
    }

    @Test
    public void exitsOnNextRequestedMove() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("P1", 1, 8);

        sys.moveTrains(new String[]{"P1"}); // 1 -> 5
        sys.moveTrains(new String[]{"P1"}); // 5 -> 8 (destination)
        assertEquals(8, sys.getTrain("P1"));

        int moved = sys.moveTrains(new String[]{"P1"}); // exit
        assertEquals(1, moved);
        try {
            sys.getTrain("P1");
            fail("Train should have exited corridor");
        } catch (IllegalArgumentException expected) {
            // expected after exit
        }
    }
}

import org.junit.Test;
import static org.junit.Assert.*;

public class MovePhase1_Test {

    @Test
    public void passengerFollowsMainlinePath() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("P1", 1, 8); // passenger route: 1 -> 5 -> 8 (exit)

        int moved = sys.moveTrains(new String[]{"P1"});
        assertEquals(1, moved);
        assertEquals(5, sys.getTrain("P1"));

        moved = sys.moveTrains(new String[]{"P1"});
        assertEquals(1, moved);
        try {
            sys.getTrain("P1");
            fail("Passenger should exit immediately upon reaching section 8");
        } catch (IllegalArgumentException expected) {
            // expected: train exits when reaching its destination exit
        }
    }

    @Test
    public void freightStaysOnFreightLine() {
        Interlocking sys = new InterlockingImpl();

        sys.addTrain("F1", 3, 11); // freight route: 3 -> 7 -> 11 (exit)
        int moved = sys.moveTrains(new String[]{"F1"});
        assertEquals(1, moved);
        assertEquals(7, sys.getTrain("F1"));

        moved = sys.moveTrains(new String[]{"F1"});
        assertEquals(1, moved);
        try {
            sys.getTrain("F1");
            fail("Freight should exit immediately upon reaching section 11");
        } catch (IllegalArgumentException expected) {
            // expected exit
        }
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
        sys.moveTrains(new String[]{"P1"}); // 5 -> 8 (destination & exit)

        try {
            sys.getTrain("P1");
            fail("Train should have exited immediately after reaching section 8");
        } catch (IllegalArgumentException expected) {
            // expected after exit
        }
    }
}

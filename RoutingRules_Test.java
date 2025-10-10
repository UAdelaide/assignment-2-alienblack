import org.junit.Test;
import static org.junit.Assert.*;

public class RoutingRules_Test {
    @Test
    public void southOnlyEntersVia1or3() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("P1", 1, 8);
        sys.addTrain("F1", 3, 4);
        try {
            sys.addTrain("P2", 2, 8);
            fail("Section 2 should not accept southbound entry");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void northOnlyEntersVia4_9_10_11() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("F4", 4, 3);  // northbound freight
        sys.addTrain("P9", 9, 2);  // northbound passenger
        sys.addTrain("P10", 10, 2);
        try {
            sys.addTrain("P1", 5, 2);
            fail("Section 5 cannot be used for northbound entry");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}

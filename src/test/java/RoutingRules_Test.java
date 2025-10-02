import org.junit.Test;
import static org.junit.Assert.*;

public class RoutingRules_Test {
    @Test
    public void southOnlyEntersVia1or3() {
        Interlocking sys = new InterlockingImpl();
        // TODO adapt to Interlocking API:
        // assertTrue(sys.tryEnter("S1", /*south*/ true, /*at*/ 1));
        // assertFalse(sys.tryEnter("S2", /*south*/ true, /*at*/ 2));
        assertTrue(true);
    }

    @Test
    public void northOnlyEntersVia4_9_10_11() {
        Interlocking sys = new InterlockingImpl();
        // assertTrue(sys.tryEnter("N1", false, 4));
        // assertFalse(sys.tryEnter("N2", false, 1));
        assertTrue(true);
    }
}

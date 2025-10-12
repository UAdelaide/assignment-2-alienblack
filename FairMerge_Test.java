import org.junit.Test;
import static org.junit.Assert.*;

public class FairMerge_Test {

    @Test
    public void alternatingAccessToMergeSectionSix() {
        InterlockingImpl sys = new InterlockingImpl();

        sys.addTrain("P9", 9, 2);   // northbound passenger already at section 9
        sys.addTrain("P10", 10, 2); // northbound passenger at section 10

        int moved = sys.moveTrains(new String[]{"P9","P10"});
        assertEquals(0, moved);
        assertEquals(InterlockingImpl.Outcome.DEADLOCK, sys.getLastOutcome());

        // Break the tie manually and let the chosen train exit
        int movedSingle = sys.moveTrains(new String[]{"P9"});
        assertEquals(1, movedSingle);
        assertEquals(6, sys.getTrain("P9"));
        int movedToExit = sys.moveTrains(new String[]{"P9"}); // 6 -> 2 (exit)
        assertEquals(1, movedToExit);
        try {
            sys.getTrain("P9");
            fail("Train should exit immediately upon reaching section 2");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        // Introduce a new challenger on the same merge
        sys.addTrain("P11", 9, 2);

        int movedSecondAttempt = sys.moveTrains(new String[]{"P10","P11"});
        assertEquals(0, movedSecondAttempt);
        assertEquals(InterlockingImpl.Outcome.DEADLOCK, sys.getLastOutcome());

        int movedSecond = sys.moveTrains(new String[]{"P10"});
        assertEquals(1, movedSecond);
        assertEquals(6, sys.getTrain("P10"));
        int challengerPos = sys.getTrain("P11");
        assertTrue(challengerPos == 9 || challengerPos == 10);
    }
}

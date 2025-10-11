import org.junit.Test;
import static org.junit.Assert.*;

public class FairMerge_Test {

    @Test
    public void alternatingAccessToMergeSectionSix() {
        Interlocking sys = new InterlockingImpl();

        sys.addTrain("P9", 9, 2);   // northbound passenger already at section 9
        sys.addTrain("P10", 10, 2); // northbound passenger at section 10

        int moved = sys.moveTrains(new String[]{"P9","P10"});
        assertEquals(1, moved);
        String first = sys.getTrain("P9") == 6 ? "P9" : "P10";
        String waiting = first.equals("P9") ? "P10" : "P9";
        assertEquals(6, sys.getTrain(first));

        // Move the winner toward exit 2 and let it leave the corridor
        sys.moveTrains(new String[]{first}); // 6 -> 2
        assertEquals(2, sys.getTrain(first));
        sys.moveTrains(new String[]{first}); // exit
        try {
            sys.getTrain(first);
            fail("Train should have exited corridor");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        // Introduce a new challenger on the same merge
        String challengerEntry = first.equals("P9") ? "P3" : "P4";
        if (first.equals("P9")) {
            sys.addTrain(challengerEntry, 9, 2);
        } else {
            sys.addTrain(challengerEntry, 10, 2);
        }

        int movedSecond = sys.moveTrains(new String[]{waiting, challengerEntry});
        assertEquals(1, movedSecond);
        assertEquals(6, sys.getTrain(waiting));
        int challengerPos = sys.getTrain(challengerEntry);
        assertTrue(challengerPos == 9 || challengerPos == 10);
    }
}

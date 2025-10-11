import org.junit.Test;
import static org.junit.Assert.*;

public class RegressionScenarios_Test {

    @Test
    public void exitReadyTrainKeepsSectionUntilAfterTick() {
        InterlockingImpl sys = new InterlockingImpl();

        sys.addTrain("Train163", 10, 2);
        sys.moveTrains(new String[]{"Train163"}); // 10 -> 6
        sys.moveTrains(new String[]{"Train163"}); // 6 -> 2 (exit-ready)

        sys.addTrain("Train164", 10, 2);
        sys.moveTrains(new String[]{"Train164"}); // 10 -> 6

        int moved = sys.moveTrains(new String[]{"Train163", "Train164"});
        assertEquals(1, moved);

        try {
            sys.getTrain("Train163");
            fail("Train163 should have exited the corridor");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        assertEquals(6, sys.getTrain("Train164"));
        assertEquals("Train164", sys.getSection(6));
        assertNull(sys.getSection(2));

        moved = sys.moveTrains(new String[]{"Train164"});
        assertEquals(1, moved);
        assertEquals(2, sys.getTrain("Train164"));

        moved = sys.moveTrains(new String[]{"Train164"});
        assertEquals(1, moved);
        try {
            sys.getTrain("Train164");
            fail("Train164 should have exited after reaching destination");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void policyBlockedContestReportsBlockedOutcome() {
        InterlockingImpl sys = new InterlockingImpl();

        sys.addTrain("TrainNorth", 11, 3);
        sys.moveTrains(new String[]{"TrainNorth"}); // 11 -> 7

        sys.addTrain("TrainCross", 4, 3);

        int moved = sys.moveTrains(new String[]{"TrainNorth", "TrainCross"});
        assertEquals(0, moved);
        assertEquals(InterlockingImpl.Outcome.BLOCKED, sys.getLastOutcome());
        assertEquals(7, sys.getTrain("TrainNorth"));
        assertEquals(4, sys.getTrain("TrainCross"));
        assertNull(sys.getSection(3));

        moved = sys.moveTrains(new String[]{"TrainNorth"});
        assertEquals(1, moved);
        assertEquals(InterlockingImpl.Outcome.PROGRESS, sys.getLastOutcome());
        assertEquals(3, sys.getTrain("TrainNorth"));
    }
}

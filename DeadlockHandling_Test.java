import org.junit.Test;
import static org.junit.Assert.*;

public class DeadlockHandling_Test {

    @Test
    public void detectsHeadOnFreightDeadlock() {
        InterlockingImpl sys = new InterlockingImpl();

        sys.addTrain("F1", 3, 11);
        sys.addTrain("F2", 11, 3);

        sys.moveTrains(new String[]{"F2"}); // position northbound in section 7

        int moved = sys.moveTrains(new String[]{"F1", "F2"});
        assertEquals(0, moved);
        assertEquals(InterlockingImpl.Outcome.COLLISION, sys.getLastOutcome());
    }

    @Test
    public void reportsBlockWhenNoTrainsRequested() {
        InterlockingImpl sys = new InterlockingImpl();
        int moved = sys.moveTrains(new String[0]);
        assertEquals(0, moved);
        assertEquals(InterlockingImpl.Outcome.BLOCKED, sys.getLastOutcome());
    }

    @Test
    public void progressResetsOutcomeToProgress() {
        InterlockingImpl sys = new InterlockingImpl();
        sys.addTrain("P1", 1, 8);
        int moved = sys.moveTrains(new String[]{"P1"});
        assertEquals(1, moved);
        assertEquals(InterlockingImpl.Outcome.PROGRESS, sys.getLastOutcome());
    }
}

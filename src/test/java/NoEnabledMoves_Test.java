import org.junit.Test;
import static org.junit.Assert.*;

public class NoEnabledMoves_Test {

    @Test
    public void nothingMovesWhenAllTargetsAlreadyTaken() {
        Interlocking sys = new InterlockingImpl();

        // Step 1: freight northbound occupies section 7
        sys.addTrain("F2", 11, 3);         // north entry 11 heading to exit 3
        sys.moveTrains(new String[]{"F2"}); // 11 -> 7

        // Step 2: freight southbound waits at section 3 aiming for 11
        sys.addTrain("F1", 3, 11);         // south entry 3 heading to 11

        // Both trains are blocked: F1 wants 7 (occupied by F2), F2 wants 3 (occupied by F1).
        int moved = sys.moveTrains(new String[]{"F1","F2"});
        assertEquals(0, moved);

        assertEquals(3, sys.getTrain("F1"));
        assertEquals(7, sys.getTrain("F2"));
    }
}

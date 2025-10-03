import org.junit.Test;
import static org.junit.Assert.*;

public class NoEnabledMoves_Test {

    @Test
    public void nothingMovesWhenAllTargetsAlreadyTaken() {
        Interlocking sys = new InterlockingImpl();

        // Step 1: occupy 2 legally
        sys.addTrain("B", 3, 4);           // south entry 3, heading toward 4
        sys.moveTrains(new String[]{"B"}); // B: 3 -> 2

        // Step 2: occupy 4 legally (north entry)
        sys.addTrain("C", 4, 2);           // north entry 4, any north exit (2 or 3) is fine

        // Step 3: A legally enters at 1 and wants 2, but 2 is already taken by B
        sys.addTrain("A", 1, 4);

        // Now: A wants 2 (blocked by B), B wants 4 (blocked by C), C has no next.
        int moved = sys.moveTrains(new String[]{"A","B","C"});
        assertEquals(0, moved);

        assertEquals(1, sys.getTrain("A"));
        assertEquals(2, sys.getTrain("B"));
        assertEquals(4, sys.getTrain("C"));
    }
}

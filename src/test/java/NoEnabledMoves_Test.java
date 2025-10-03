import org.junit.Test;
import static org.junit.Assert.*;

public class NoEnabledMoves_Test {
    @Test
    public void nothingMovesWhenAllTargetsAlreadyTaken() {
        Interlocking sys = new InterlockingImpl();
        sys.addTrain("B", 2, 4); // occupies 2
        sys.addTrain("C", 4, 4); // occupies 4
        sys.addTrain("A", 1, 4); // wants 2 but it's taken

        int moved = sys.moveTrains(new String[]{"A","B","C"});
        assertEquals(0, moved);
        assertEquals(1, sys.getTrain("A"));
        assertEquals(2, sys.getTrain("B"));
        assertEquals(4, sys.getTrain("C"));
    }

}

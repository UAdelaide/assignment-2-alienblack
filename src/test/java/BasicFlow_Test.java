import org.junit.Test;
import static org.junit.Assert.*;

public class BasicFlow_Test {
    @Test
    public void projectCompilesAndRuns() {
        Interlocking impl = new InterlockingImpl();
        assertNotNull(impl);
    }
}

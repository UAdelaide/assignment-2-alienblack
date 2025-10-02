public class InterlockingImpl implements Interlocking {
    @Override
    public void addTrain(String trainName, int entryTrackSection, int destinationTrackSection) throws IllegalArgumentException, IllegalStateException {

    }

    @Override
    public int moveTrains(String[] trainNames) throws IllegalArgumentException {
        return 0;
    }

    @Override
    public String getSection(int trackSection) throws IllegalArgumentException {
        return null;
    }

    @Override
    public int getTrain(String trainName) throws IllegalArgumentException {
        return 0;
    }
    // TODO: add real state & logic

    // Implement every method from Interlocking exactly (signatures must match).
    // For now, return safe defaults that compile.
}

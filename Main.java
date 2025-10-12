public class Main {
    public static void main(String[] args) {
        InterlockingImpl impl = new InterlockingImpl();
        if (args.length == 0 || "demo".equalsIgnoreCase(args[0])) {
            runDemo(impl);
        } else {
            System.out.println("Usage: java Main [demo]");
        }
    }

    private static void runDemo(InterlockingImpl impl) {
        System.out.println("Running demo scenario with passenger priority at the crossing.");
        impl.addTrain("P1", 1, 8);
        impl.addTrain("F3", 3, 4);

        int moved = impl.moveTrains(new String[]{"P1", "F3"});
        System.out.println("Trains moved this tick: " + moved);
        System.out.println("Section 5 now has: " + impl.getSection(5));
        System.out.println("Section 3 now has: " + impl.getSection(3));

        moved = impl.moveTrains(new String[]{"P1"});
        System.out.println("Passenger steps toward exit (moved=" + moved + ")");
        moved = impl.moveTrains(new String[]{"P1"});
        System.out.println("Passenger exits (moved=" + moved + ")");
    }
}

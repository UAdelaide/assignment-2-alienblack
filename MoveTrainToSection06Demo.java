public final class MoveTrainToSection06Demo {

    public static void main(String[] args) {
        InterlockingImpl sys = new InterlockingImpl();

        sys.addTrain("Train163", 10, 2);
        System.out.println("Added Train163 -> section " + sys.getTrain("Train163"));

        sys.moveTrains(new String[]{"Train163"});
        System.out.println("After move 1 (163): section " + sys.getTrain("Train163"));

        sys.moveTrains(new String[]{"Train163"});
        System.out.println("After move 2 (163): section " + sys.getTrain("Train163"));

        sys.addTrain("Train164", 10, 2);
        sys.moveTrains(new String[]{"Train164"});
        System.out.println("After move 1 (164): section " + sys.getTrain("Train164"));

        sys.addTrain("Train165", 10, 2);

        int moved = sys.moveTrains(new String[]{"Train163", "Train164", "Train165"});
        System.out.println("Batch move -> moved=" + moved);

        printState(sys, "Train163");
        printState(sys, "Train164");
        printState(sys, "Train165");
    }

    private static void printState(Interlocking sys, String name) {
        try {
            System.out.println(name + " now at section " + sys.getTrain(name));
        } catch (IllegalArgumentException ex) {
            System.out.println(name + " exited");
        }
    }
}

public class Main {
    public static void main(String[] args) {
        Interlocking impl = new InterlockingImpl();
        System.out.println("Interlocking driver booted. Args: " + String.join(" ", args));
        // TODO: load a scenario file and tick the network
    }
}

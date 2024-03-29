public class Debugger {
    private static final boolean debug = false;

    public static void println(Object o) {
        if (debug) {
            System.out.println(o);
        }
    }
}

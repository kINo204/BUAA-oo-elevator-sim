import com.oocourse.elevator1.TimableOutput;

public class Debugger {
    private static final boolean debugOut = false;
    private static final boolean output = true;

    public static void dbgPrintln(Object o) {
        if (debugOut) {
            System.out.println(o);
        }
    }

    public static void timePrintln(Object o) {
        if (output) {
            TimableOutput.println(o);
        }
    }
}

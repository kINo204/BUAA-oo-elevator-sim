import com.oocourse.elevator2.TimableOutput;

import java.util.HashMap;

public class Debugger {
    private static final boolean debugOut = false;
    private static final HashMap<String, Boolean> infoSwitch = new HashMap<>();

    static {
        infoSwitch.put("scheduler", true);
        infoSwitch.put("elevator", true);
        infoSwitch.put("elevator thread", true);
        infoSwitch.put("read requests", false);
        infoSwitch.put("server", true);
        infoSwitch.put("reset", true);
        infoSwitch.put("commandlist", false);
        infoSwitch.put("command", true);
        infoSwitch.put("buffer thread", true);
    }

    private static final boolean output = true;

    public static void dbgPrintln(Object o) {
        if (debugOut) {
            System.out.println(o);
        }
    }

    public static void dbgPrintln(Object o, String type) {
        if (infoSwitch.get(type)) {
            dbgPrintln(o);
        }
    }

    public static void timePrintln(Object o) {
        if (output) {
            TimableOutput.println(o);
        }
    }
}

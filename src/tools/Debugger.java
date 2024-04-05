package tools;

import com.oocourse.elevator2.TimableOutput;

import java.util.HashMap;

public class Debugger {
    private static final boolean debugOut = true;
    private static final HashMap<String, Boolean> infoSwitch = new HashMap<>();

    static {
        infoSwitch.put("scheduler", false);
        infoSwitch.put("elevator", false);
        infoSwitch.put("elevator thread", false);
        infoSwitch.put("read requests", false);
        infoSwitch.put("server", true);
        infoSwitch.put("reset", false);
        infoSwitch.put("commandlist", false);
        infoSwitch.put("command", false);
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

package tools;

import com.oocourse.elevator3.TimableOutput;

import java.util.HashMap;

public class Debugger {
    private static final boolean debugOut = false;
    private static final HashMap<String, Boolean> infoSwitch = new HashMap<>();
    private static final String eid = "";

    static {
        infoSwitch.put("scheduler", false);
        infoSwitch.put("elevator", false);
        infoSwitch.put("elevator thread", true);
        infoSwitch.put("read requests", false);
        infoSwitch.put("server", true);
        infoSwitch.put("reset", false);
        infoSwitch.put("commandlist", true);
        infoSwitch.put("command", true);
        infoSwitch.put("buffer thread", false);
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

    public static void dbgPrintln(Object o, String type, String selId) {
        if (!eid.isEmpty()) {
            if (eid.equals(selId)) {
                dbgPrintln(o, type);
            }
        } else {
            dbgPrintln(o, type);
        }
    }

    public static void timePrintln(Object o) {
        if (output) {
            TimableOutput.println(o);
        }
    }
}

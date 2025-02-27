import com.oocourse.elevator3.ElevatorInput;
import com.oocourse.elevator3.Request;
import com.oocourse.elevator3.TimableOutput;
import tools.Debugger;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // initialize timed-output class
        TimableOutput.initStartTimestamp();
        // prepare the request queue for server and input reader
        RequestQueue requestQueue = new RequestQueue();
        ServerThread serverThread = new ServerThread(requestQueue);
        // call the elevator server
        serverThread.start();
        // read requests from STDIN and add it to the request queue
        readRequests(requestQueue);
    }

    /**
     * Read requests from the standard input, which has been sent by its time,
     * and add it to the request queue.
     */
    private static void readRequests(RequestQueue requestQueue) {
        // Reading requests from STDIN.
        ElevatorInput elevatorInput = new ElevatorInput(System.in);
        while (true) {
            Debugger.dbgPrintln("@Thread{Main.readRequests}: running", "read requests");
            // try to get a new request
            /* NOTE: THESE FORMATS ARE SPECIALIZED IN HW5
              Structure of a request:
                [time]uid-FROM-beg-TO-dst-BY-eid
              PersonRequest's methods:
              - int getPersonID();  (uid)
              - int getFromFloor(); (beg)
              - int getToFloor();   (dst)
              - int getElevatorId();(eid)
              (override methods)
              - String toString();
              - int hashCode();
              - boolean equals(Object obj);
             */
            Request request = elevatorInput.nextRequest();
            if (request == null) {  // failed - no more new request from STDIN
                requestQueue.setEnd(true);
                Debugger.dbgPrintln("@Thread{Main.readRequests}: exiting", "read requests");
                break;
            } else {  // succeeded
                requestQueue.addRequest(request);
            }
        }
        try {
            elevatorInput.close();
        } catch (IOException e) {
            System.out.println("Elevator.close(): IOException caught");
        }
    }
}

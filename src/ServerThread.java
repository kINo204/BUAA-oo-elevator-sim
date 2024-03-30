import com.oocourse.elevator1.PersonRequest;

import java.util.ArrayList;

public class ServerThread extends Thread {
    private final RequestQueue requestQueue;
    private final ArrayList<Elevator> elevators;
    private final ArrayList<ElevatorThread> elevatorThreads;
    private final int elevatorNum = 6;

    ServerThread(RequestQueue requestQueue) {
        this.requestQueue = requestQueue;
        elevators = new ArrayList<>();
        elevatorThreads = new ArrayList<>();
        for (int i = 0; i < elevatorNum; i++) {
            Elevator elevator = new Elevator();
            elevators.add(elevator);
            ElevatorThread elevatorThread = new ElevatorThread(elevator, i + 1);
            elevatorThreads.add(elevatorThread);
        }
    }

    @Override
    public void start() {
        super.start();
        for (ElevatorThread elevatorThread : elevatorThreads) {
            elevatorThread.start();
        }
    }

    @Override
    public void run() {
        while (true) {
            Debugger.dbgPrintln(
                    "@Thread{ServerThread}: running"
            );
            // Exiting condition. EMPTY means no requests left, and END means no more new requests.
            if (requestQueue.isEmpty() && requestQueue.isEnd()) {
                Debugger.dbgPrintln(
                        "@Thread{ServerThread}: exiting"
                );
                for (Elevator elevator : elevators) {
                    elevator.setEnd();
                }
                return;
            }

            // Try to get a new request from the request queue.
            PersonRequest request = requestQueue.getRequest();
            if (request == null) {
                continue;
            }
            // A valid request get.
            schedule(request);
        }
    }

    private void schedule(PersonRequest request) {
        Elevator elevator = elevators.get(request.getElevatorId() - 1);  // EID starts at 1, so -1
        // This thread ensures that once a new request is acquired from the request
        // queue, it's immediately written to the scheduled elevator's fr_table, so
        // that an elevator can read its scheduled requests in real time.
        elevator.addRequest(request);  // write request in elevator's fr_table
    }
}

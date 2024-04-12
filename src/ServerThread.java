import com.oocourse.elevator3.DoubleCarResetRequest;
import com.oocourse.elevator3.NormalResetRequest;
import com.oocourse.elevator3.PersonRequest;
import com.oocourse.elevator3.Request;
import com.oocourse.elevator3.ResetRequest;
import tools.Debugger;

import java.util.HashMap;
import java.util.HashSet;

public class ServerThread extends Thread {
    private Boolean end = false;
    private final RequestQueue requestQueue;
    private final RequestQueue bufferQueue;
    private final BufferThread bufferThread;
    private final HashMap<String, Elevator> elevators;
    private final HashMap<String, ElevatorThread> elevatorThreads;
    private final int initElevatorNum = 6;

    ServerThread(RequestQueue requestQueue) {
        this.requestQueue = requestQueue;

        this.bufferQueue = new RequestQueue();
        this.bufferThread = new BufferThread(requestQueue, bufferQueue);

        elevators = new HashMap<>();
        elevatorThreads = new HashMap<>();
        for (int i = 0; i < initElevatorNum; i++) {
            String eid = Integer.toString(i + 1); // eid starts from 1
            Elevator elevator = new Elevator(eid);
            elevators.put(eid, elevator);
            ElevatorThread elevatorThread = new ElevatorThread(this, elevator, eid);
            elevatorThreads.put(eid, elevatorThread);
        }
    }

    @Override
    public void start() {
        super.start();
        for (ElevatorThread elevatorThread : elevatorThreads.values()) {
            elevatorThread.start();
        }
        bufferThread.start();
    }

    @Override
    public void run() {
        while (true) {
            Debugger.dbgPrintln(
                    "@Thread{ServerThread}: running", "server"
            );
            // Exiting condition. EMPTY means no requests left, and END means no more new requests.
            boolean exit = bufferQueue.isEmpty() && elevatorsFeedbackRequestEnd();
            exit &= requestQueue.isEmpty() && requestQueue.isEnd();
            if (exit) {
                Debugger.dbgPrintln(
                        "@Thread{ServerThread}: exiting"
                );
                // TODO newly created elevator in DCElevator may not be informed
                synchronized (this) {
                    end = true;
                    notifyAll();
                }
                synchronized (elevators) {
                    for (Elevator elevator : elevators.values()) {
                        elevator.setEnd();
                    }
                }
                bufferQueue.setEnd(true);
                bufferThread.setStart(false);
                return;
            }

            // Try to get a new request from the request queue.
            Request request = requestQueue.getRequest();
            if (request == null) {
                continue;
            }
            // A valid request get.
            schedule(request);
        }
    }

    public synchronized boolean isEnd() {
        notifyAll();
        return end;
    }

    private boolean elevatorsFeedbackRequestEnd() {
        boolean ret = true;
        synchronized (elevators) {
            for (Elevator elevator : elevators.values()) {
                ret &= elevator.isFeedbackRequestEnd();
            }
        }
        return ret;
    }

    public void noteElevatorDirectionChange() {
        bufferThread.setStart(true);
        requestQueue.note();
    }

    private int schedulePriority(Elevator elevator, PersonRequest request) {
        int priority = 0;
        // 5th: random of [0,10)
        priority += (int) (Math.random() * 10);

        // highest: idle
        if (elevator.getDirection() == Elevator.Direction.STAY) {
            return priority;
        }
        // moving towards:
        // 1st: not full
        if (elevator.isFull()) { return 9999; }
        // 2nd: shorter distance
        priority += 1000 * Math.abs(request.getFromFloor() - elevator.getFloor());
        // 3nd: less load
        priority += 100 * elevator.getLoad();
        // CHECKME best fit serving range
        // 4th:
        priority += 10 * elevator.fitRange(request);

        return priority;
    }

    private void schedule(Request inputRequest) {
        if (inputRequest instanceof PersonRequest) {
            PersonRequest request = (PersonRequest) inputRequest;
            // look in (towards + idle) for an elevator of shortest distance
            int minPriority = 100000;
            String selectedElevatorId = "";
            boolean allReset = true;
            boolean splitRequest = true;
            boolean allBackwards = true;
            synchronized (elevators) {
                for (String eid : elevators.keySet()) {
                    Elevator elevator = elevators.get(eid);
                    Debugger.dbgPrintln("schedule for " + eid, "scheduler");
                    // elevator under reset, not operable
                    if (elevator.getState() == Elevator.State.RESETTING) {
                        continue;
                    }
                    allReset = false;
                    // CHECKME check range of request and elevator serving floors
                    if (!elevator.containRange(request)) {
                        continue;
                    }
                    splitRequest = false;
                    // if away from the request, jump it
                    // jump on equal because this leads to uncertainty, the req may not
                    // get picked up
                    boolean jump = elevator.getDirection() != Elevator.Direction.STAY &&
                            elevator.getFloor() == request.getFromFloor();
                    jump |= elevator.getDirection() != Elevator.Direction.STAY
                            && elevator.getFloor() < request.getFromFloor()
                            && elevator.nextDirection() == -1;
                    jump |= elevator.getDirection() != Elevator.Direction.STAY
                            && elevator.getFloor() > request.getFromFloor()
                            && elevator.nextDirection() == 1;
                    if (jump) {
                        continue;
                    }
                    allBackwards = false;
                    // measure priority and record
                    // smaller value of priority means higher priority level!!!
                    int priority = schedulePriority(elevator, request);
                    Debugger.dbgPrintln(eid + ": priority=" + priority, "scheduler");
                    // "<"(instead of <=) means if multiple found, take the 1st
                    if (priority < minPriority) {
                        minPriority = priority;
                        selectedElevatorId = eid;
                    }
                }
            }
            if (allReset) {
                Debugger.dbgPrintln("@Scheduler{}: req jumped, all resetting");
                bufferThread.addRequest(request);
            } else if (splitRequest) {
                // CHECKME if no elevator contains the range:
                // TODO pick a floor and split the request
                int transFloor = 0;
                synchronized (elevators) {
                    for (Elevator elevator : elevators.values()) {
                        if (elevator.getState() != Elevator.State.RESETTING) {
                            transFloor = elevator.getTransFloor();
                        }
                    }
                }
                Debugger.dbgPrintln(
                        "@Thread{ServerThread}: picked trans_flr" + transFloor, "server"
                );
                PersonRequest nextRequest = new PersonRequest(
                        transFloor, request.getToFloor(), request.getPersonId());
                requestQueue.addRequest(new ParaRequest(
                        request.getFromFloor(), transFloor, request.getPersonId(), nextRequest));
            } else if (allBackwards) {
                Debugger.dbgPrintln("@Scheduler{}: req jumped", "scheduler");
                bufferThread.addRequest(request);
            } else {
                synchronized (elevators) {
                    // elevator found, start scheduling
                    // EID starts at 1, so -1
                    Elevator elevator = elevators.get(selectedElevatorId);
                    Debugger.timePrintln(
                            String.format(
                                    "RECEIVE-%d-%s", request.getPersonId(), selectedElevatorId)
                    );
                    // This thread ensures that once a new request is acquired from the request
                    // queue, it's immediately written to the scheduled elevator's fr_table, so
                    // that an elevator can read its scheduled requests in real time.
                    elevator.addRequest(request);  // write request in elevator's fr_table
                }
            }
            // End of PersonRequest handling
        } else if (inputRequest instanceof ResetRequest) {
            String eid;
            if (inputRequest instanceof NormalResetRequest) {
                eid = Integer.toString(((NormalResetRequest) inputRequest).getElevatorId());
            } else {
                eid = Integer.toString(((DoubleCarResetRequest) inputRequest).getElevatorId());
            }
            synchronized (elevators) {
                Debugger.dbgPrintln("@Scheduler{}: rst_req", "scheduler");
                Elevator elevator = elevators.get(eid);
                elevator.addRequest(inputRequest);
            }
        }
    }

    public void addElevator(String eid, Elevator elevator, ElevatorThread elevatorThread) {
        synchronized (elevators) {
            elevators.put(eid, elevator);
            elevators.put(eid.charAt(0) + "-A", elevators.get(eid.substring(0,1)));
            elevators.remove(eid.substring(0,1));
        }
        synchronized (elevatorThreads) { elevatorThreads.put(eid, elevatorThread); }
    }

    public void addRequests(HashSet<PersonRequest> requests) {
        for (PersonRequest request : requests) {
            this.requestQueue.addRequest(request);
        }
    }
}

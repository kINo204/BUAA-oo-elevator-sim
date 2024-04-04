import com.oocourse.elevator2.PersonRequest;
import com.oocourse.elevator2.Request;
import com.oocourse.elevator2.ResetRequest;

import java.util.ArrayList;
import java.util.HashSet;

public class ServerThread extends Thread {
    private final RequestQueue requestQueue;
    private final RequestQueue bufferQueue;
    private final BufferThread bufferThread;
    private final ArrayList<Elevator> elevators;
    private final ArrayList<ElevatorThread> elevatorThreads;
    private final int elevatorNum = 6;

    ServerThread(RequestQueue requestQueue) {
        this.requestQueue = requestQueue;

        this.bufferQueue = new RequestQueue();
        this.bufferThread = new BufferThread(requestQueue, bufferQueue);

        elevators = new ArrayList<>();
        elevatorThreads = new ArrayList<>();
        for (int i = 0; i < elevatorNum; i++) {
            Elevator elevator = new Elevator();
            elevators.add(elevator);
            ElevatorThread elevatorThread = new ElevatorThread(this, elevator, i + 1);
            elevatorThreads.add(elevatorThread);
        }
    }

    @Override
    public void start() {
        super.start();
        for (ElevatorThread elevatorThread : elevatorThreads) {
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
            if (
                    requestQueue.isEmpty() && requestQueue.isEnd()
                    && bufferQueue.isEmpty()
            ) {
                Debugger.dbgPrintln(
                        "@Thread{ServerThread}: exiting", "server"
                );
                for (Elevator elevator : elevators) {
                    elevator.setEnd();
                }
                bufferQueue.setEnd(true);
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

    public void noteElevatorDirectionChange() {
        bufferThread.setStart(true);
    }

    private void schedule(Request inputRequest) {
        if (inputRequest instanceof PersonRequest) {
            PersonRequest request = (PersonRequest) inputRequest;
            // look in (towards + idle) for an elevator of shortest distance
            int minDistance = 100000;
            int selectedElevatorId = 0;  // from 1 to 6
            for (Elevator elevator : elevators) {
                // elevator under reset, not operable
                if (elevator.getState() == Elevator.State.RESETTING) { continue; }
                // if away from the request, jump it
                // jump on equal because this leads to uncertainty, the req may not get picked up
                boolean jump = elevator.getDirection() != Elevator.Direction.STAY &&
                        elevator.getFloor() == request.getFromFloor();
                jump |= elevator.getDirection() != Elevator.Direction.STAY
                        && elevator.getFloor() < request.getFromFloor()
                        && elevator.nextDirection() == -1;
                jump |= elevator.getDirection() != Elevator.Direction.STAY
                        && elevator.getFloor() > request.getFromFloor()
                        && elevator.nextDirection() == 1;
                if (jump) { continue; }
                // measure distance and record
                int distance = Math.abs(request.getFromFloor() - elevator.getFloor());
                // "<"(instead of <=) means if multiple found, take the 1st
                if (distance < minDistance) {
                    minDistance = distance;
                    selectedElevatorId = elevators.indexOf(elevator) + 1;
                }
            }
            if (minDistance != 100000) {
                // elevator found, start scheduling
                Elevator elevator = elevators.get(selectedElevatorId - 1);// EID starts at 1, so -1
                Debugger.timePrintln(
                        String.format("RECEIVE-%d-%d", request.getPersonId(), selectedElevatorId)
                );
                // This thread ensures that once a new request is acquired from the request
                // queue, it's immediately written to the scheduled elevator's fr_table, so
                // that an elevator can read its scheduled requests in real time.
                elevator.addRequest(request);  // write request in elevator's fr_table
            }
            else {  // if all elevator moving away from the req, store req in buffer
                Debugger.dbgPrintln("@Scheduler{}: req jumped", "scheduler");
                bufferThread.addRequest(request);
            }
        } else if (inputRequest instanceof ResetRequest) {
            Debugger.dbgPrintln("@Scheduler{}: rst_req", "scheduler");
            Elevator elevator = elevators.get(((ResetRequest) inputRequest).getElevatorId() - 1);
            elevator.addRequest(inputRequest);
        }
    }

    public void addRequests(HashSet<PersonRequest> requests) {
        for (PersonRequest request : requests) {
            this.requestQueue.addRequest(request);
        }
    }
}

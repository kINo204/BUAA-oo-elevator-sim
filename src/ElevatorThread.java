import com.oocourse.elevator2.PersonRequest;

import java.util.HashSet;

public class ElevatorThread extends Thread {
    private final ServerThread server;
    private final Elevator elevator;
    private final int eid;
    private Command command;
    private boolean jump = false; // if last floor still has waiters, jump

    // elevator running time constants(in ms)
    private int moveTime = 400;
    private final int openTime = 200;
    private final int closeTime = 200;
    private final int resetTime = 1200;

    public ElevatorThread(ServerThread server, Elevator elevator, int eid) {
        this.server = server;
        this.elevator = elevator;
        this.eid = eid;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Debugger.dbgPrintln(
                        String.format(
                                "@Thread{ElevatorThread,eid=%d,dir=%s,cur_flr=%d,load=%d}: running",
                                eid, elevator.getDirection(), elevator.getFloor(), elevator.getLoad()
                        ), "elevator thread"
                );
                // exiting condition: finish all commands
                if (elevator.isCommandEmpty() && elevator.isCommandEnd()) {
                    Debugger.dbgPrintln(
                            String.format(
                                    "@Thread{ElevatorThread,eid=%d}: exiting",
                                    eid
                            ), "elevator thread"
                    );
                    return;
                }
                // get the next command
                command = elevator.nextCommand(jump);
                jump = false; // the jump information is used, thus outdated
                if (command == null) { continue; }
                // enter specific motion of current state
                switch (elevator.getState()) {
                    case MOVING:
                        motionMoving();
                        break;
                    case OPENING:
                        Debugger.dbgPrintln(
                                String.format(
                                        "@Thread{ElevatorThread,eid=%d}: enter opening",
                                        eid
                                ), "elevator thread"
                        );
                        motionOpening();
                        break;
                    case CLOSING:
                        Debugger.dbgPrintln(
                                String.format(
                                        "@Thread{ElevatorThread,eid=%d}: enter closing",
                                        eid
                                ), "elevator thread"
                        );
                        motionClosing();
                        break;
                    case RESETTING:
                        Debugger.dbgPrintln(
                                String.format(
                                        "@Thread{ElevatorThread,eid=%d}: enter resetting",
                                        eid
                                ), "elevator thread"
                        );
                        motionReset();
                        break;
                    default:
                }
            }
        } catch (InterruptedException ignored) { return; }
    }

    private void motionReset() throws InterruptedException {
        // create new requests and force unload
        if (elevator.getLoad() != 0) {
            Debugger.timePrintln(
                    String.format("OPEN-%d-%d", elevator.getFloor(), eid)
            );
            sleep(openTime);
            HashSet<PersonRequest> unloaded = elevator.forceUnloadAll(eid);
            server.addRequests(unloaded);
            sleep(closeTime);
            Debugger.timePrintln(
                    String.format("CLOSE-%d-%d", elevator.getFloor(), eid)
            );
        }
        // reset elevator status structures
        Debugger.timePrintln(
                String.format("RESET_BEGIN-%d", eid)
        );
        sleep(resetTime);
        this.moveTime = command.getResetSpeed();
        elevator.reset(command);
        Debugger.timePrintln(
                String.format("RESET_END-%d", eid)
        );
        server.noteElevatorDirectionChange();
    }

    private void motionMoving() throws InterruptedException {
        Debugger.dbgPrintln(
                String.format(
                        "@Thread{ElevatorThread,eid=%d}: enter moving=%s",
                        eid, elevator.getDirection()
                ), "elevator thread"
        );
        // resetting entry
        if (command.isReset()) {
            elevator.setState(Elevator.State.RESETTING);
            return;
        }
        // save direction for further use(only a new command modifies the elevator's direction)
        if (command.getDestination() == elevator.getFloor()) {
            elevator.setDirection(Elevator.Direction.STAY);
        } else if (command.getDestination() > elevator.getFloor()) {
            elevator.setDirection(Elevator.Direction.UP);
        } else { // command.getDst() < elevator.getFloor()
            elevator.setDirection(Elevator.Direction.DOWN);
        }
        // move one floor
        if (elevator.getDirection() != Elevator.Direction.STAY) {
            sleep(moveTime);
            elevator.moveOneFloor(elevator.getDirection());
            // output arrival info on each move
            Debugger.timePrintln(
                    String.format("ARRIVE-%d-%d", elevator.getFloor(), eid)
            );
        }
        // if the moving stage has been finished:
        if (command.getDestination() == elevator.getFloor()) {
            // if the elevator is full and only loading required on this floor,
            // jump the command once
            if (elevator.isFull() && !elevator.needUnloading()) {
                jump = true; // jump the command once
            } else {
                elevator.setState(Elevator.State.OPENING);          // open the door next
            }
        }
    }

    private void motionOpening() throws InterruptedException {
        // start opening the door
        Debugger.timePrintln(
                String.format("OPEN-%d-%d", elevator.getFloor(), eid)
        );
        sleep(openTime);
        elevator.setState(Elevator.State.CLOSING);              // load and unload passengers next
    }

    private void motionClosing() throws InterruptedException {
        // TODO wait before closing the door
        sleep(closeTime);
        // calc next direction
        int dirFlag = elevator.nextDirection();
        Debugger.dbgPrintln("dir_load_rem=" + dirFlag, "elevator thread");
        switch (elevator.getDirection()) {
            case UP:
                if (dirFlag != 1) {
                    server.noteElevatorDirectionChange();
                }
                break;
            case DOWN:
                if (dirFlag != -1) {
                    server.noteElevatorDirectionChange();
                }
                break;
            default:
                if (dirFlag != 0) {
                    server.noteElevatorDirectionChange();
                }
                break;
        }
        // unloading
        elevator.unloadPassengers(eid);
        // loading
        jump = elevator.loadPassengers(dirFlag, eid);
        Debugger.dbgPrintln("set jump=" + jump, "elevator thread");
        elevator.removeCurCommand(dirFlag, jump);  // the current command finished, remove it
        // finished closing the door
        Debugger.timePrintln(
                String.format("CLOSE-%d-%d", elevator.getFloor(), eid)
        );
        elevator.setState(Elevator.State.MOVING);  // now the elevator is free to move again
    }
}
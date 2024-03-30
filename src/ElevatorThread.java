public class ElevatorThread extends Thread {
    private final Elevator elevator;
    private final int eid;
    private Command command;
    private boolean jump = false; // if last floor still has waiters, jump

    // elevator running time constants(in ms)
    private final int moveTime = 400;
    private final int openTime = 200;
    private final int closeTime = 200;

    public ElevatorThread(Elevator elevator, int eid) {
        this.elevator = elevator;
        this.eid = eid;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Debugger.dbgPrintln(
                        String.format(
                                "@Thread{ElevatorThread,eid=%d,cur_flr=%d,load=%d}: running",
                                eid, elevator.getFloor(), elevator.getLoad()
                        )
                );
                // exiting condition: finish all commands
                if (elevator.isCommandEmpty() && elevator.isCommandEnd()) {
                    Debugger.dbgPrintln(
                            String.format(
                                    "@Thread{ElevatorThread,eid=%d}: exiting",
                                    eid
                            )
                    );
                    return;
                }

                command = elevator.nextCommand(jump);
                jump = false;
                if (command == null) {
                    continue;
                }
                switch (elevator.getState()) {
                    case MOVING:
                        motionMoving();
                        break;
                    case OPENING:
                        Debugger.dbgPrintln(
                                String.format(
                                        "@Thread{ElevatorThread,eid=%d}: enter opening",
                                        eid
                                )
                        );
                        motionOpening();
                        break;
                    case CLOSING:
                        Debugger.dbgPrintln(
                                String.format(
                                        "@Thread{ElevatorThread,eid=%d}: enter closing",
                                        eid
                                )
                        );
                        motionClosing();
                        break;
                    default:
                }
            }
        } catch (InterruptedException ignored) { return; }
    }

    private void motionMoving() throws InterruptedException {
        // save direction for further use(only a new command modify the elevator's direction)
        if (command.getDestination() == elevator.getFloor()) {
            elevator.setDirection(Elevator.Direction.STAY);
        } else if (command.getDestination() > elevator.getFloor()) {
            elevator.setDirection(Elevator.Direction.UP);
        } else { // command.getDst() < elevator.getFloor()
            elevator.setDirection(Elevator.Direction.DOWN);
        }
        Debugger.dbgPrintln(
                String.format(
                        "@Thread{ElevatorThread,eid=%d}: enter moving=%s",
                        eid, elevator.getDirection()
                )
        );
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
            //if (elevator.isFull()) {
            // TODO don't open and close if full already OPTIMIZE
            //} else {
            elevator.setState(Elevator.State.OPENING);          // open the door next
            //}
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
        sleep(closeTime);
        int dirFlag = elevator.nextDirection();
        Debugger.dbgPrintln("dir_load_rem=" + dirFlag);
        // loading
        jump = elevator.loadPassengers(dirFlag, eid);
        if (!jump) {
            elevator.removeCurCommand(dirFlag);  // the current command finished, remove it
        }
        // unloading
        elevator.unloadPassengers(eid);
        // finished closing the door
        Debugger.timePrintln(
                String.format("CLOSE-%d-%d", elevator.getFloor(), eid)
        );
        elevator.setState(Elevator.State.MOVING);  // now the elevator is free to move again
    }
}
import com.oocourse.elevator1.TimableOutput;

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
                // exiting condition: finish all commands
                if (elevator.isCommandEmpty() && elevator.isCommandEnd()) {
                    Debugger.println(eid + " exits");
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
                        motionOpening();
                        break;
                    case CLOSING:
                        motionClosing();
                        break;
                    default:
                }
            }
        } catch (InterruptedException ignored) { return; }
    }

    private void motionMoving() throws InterruptedException {
        Debugger.println("Moving");
        // save direction for further use(only a new command modify the elevator's direction)
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
            TimableOutput.println(
                    String.format("ARRIVE-%d-%d", elevator.getFloor(), eid)
            );
        }
        // if the moving stage has been finished:
        if (command.getDestination() == elevator.getFloor()) {
            elevator.setState(Elevator.State.OPENING);          // open the door next
        }
    }

    private void motionOpening() throws InterruptedException {
        Debugger.println("Opening");
        // start opening the door
        TimableOutput.println(
                String.format("OPEN-%d-%d", elevator.getFloor(), eid)
        );
        sleep(openTime);
        elevator.setState(Elevator.State.CLOSING);              // load and unload passengers next
    }

    private void motionClosing() throws InterruptedException {
        Debugger.println("Closing");
        sleep(closeTime);
        // TODO loading all before closing the door
        // loading and unloading passengers
        // loading
        // TODO what direction to go next?
        int dirFlag = elevator.nextDirection();
        Debugger.println("nextdir=" + dirFlag);
        jump = elevator.loadPassengers(dirFlag, eid);
        if (!jump) {
            elevator.removeCurCommand(dirFlag);  // the current command finished, remove it
        }
        //        if (elevator.nextCommand(true) != null) { // if there isn't next command, leave at once
        //            Command nxt = elevator.nextCommand(true);
        //            if (nxt.getDestination() > elevator.getFloor()) { // will go upwards
        //            } else if (nxt.getDestination() < elevator.getFloor()) { // will go downwards
        //                elevator.removeCurCommand(-1);  // the current command finished, remove it
        //                elevator.loadPassengers(-1, eid);
        //            } else { // the next command is STAY
        //            }
        //        }
        // unloading
        elevator.unloadPassengers(eid);
        // finished closing the door
        TimableOutput.println(
                String.format("CLOSE-%d-%d", elevator.getFloor(), eid)
        );
        elevator.setState(Elevator.State.MOVING);  // now the elevator is free to move again
    }
}
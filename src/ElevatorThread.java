import com.oocourse.elevator3.PersonRequest;
import tools.Debugger;

import java.util.HashSet;

public class ElevatorThread extends Thread {
    private final ServerThread server;
    private final Elevator elevator;
    private Elevator para = null;
    private ParaLock paraLock = null;
    private String eid; // TODO not thread safe
    private Command command;
    private boolean jump = false; // if last floor still has waiters, jump

    // elevator running time constants(in ms)
    private int moveTime = 400;
    private final int openTime = 200;
    private final int closeTime = 200;
    private final int resetTime = 1200;

    public ElevatorThread(ServerThread server, Elevator elevator, String eid) {
        this.server = server;
        this.elevator = elevator;
        this.eid = eid;
    }

    public ElevatorThread(ServerThread server, Elevator elevator, String eid,
                          Elevator para, ParaLock paraLock, Command command) {
        this.server = server;
        this.elevator = elevator;
        this.eid = eid;
        this.para = para;
        this.paraLock = paraLock;
        this.moveTime = command.getResetSpeed();
    }

    @Override
    public void run() {
        try {
            while (true) {
                Debugger.dbgPrintln(
                        String.format(
                                "@Thread{ElevatorThread,eid=%s,dir=%s,cur_flr=%d,load=%d}: running",
                                eid, elevator.getDirection(), elevator.getFloor(),
                                elevator.getLoad()
                        ), "elevator thread", eid
                );
                // exiting condition: finish all commands
                Debugger.dbgPrintln(
                        String.format(
                                "@Thread{ElevatorThread,eid=%s,cmdemt=%s,svend=%s}: trying exiting",
                                eid, elevator.isCommandEmpty(), server.isEnd()
                        ), "elevator thread", eid
                );
                if (elevator.isCommandEmpty() && server.isEnd()) {
                    Debugger.dbgPrintln(
                            String.format(
                                    "@Thread{ElevatorThread,eid=%s,cmdemt=%s,svend=%s}: exiting",
                                    eid, elevator.isCommandEmpty(), server.isEnd()
                            ), "elevator thread", eid
                    );
                    return;
                }
                // get the next command
                command = elevator.nextCommand(jump);
                jump = false; // the jump information is used, thus outdated
                if (command == null) {
                    Debugger.dbgPrintln("null command", "elevator thread");
                    continue; }
                // enter specific motion of current state
                switch (elevator.getState()) {
                    case MOVING:
                        motionMoving();
                        break;
                    case OPENING:
                        Debugger.dbgPrintln(
                                String.format(
                                        "@Thread{ElevatorThread,eid=%s}: enter opening",
                                        eid
                                ), "elevator thread", eid
                        );
                        motionOpening();
                        break;
                    case CLOSING:
                        Debugger.dbgPrintln(
                                String.format(
                                        "@Thread{ElevatorThread,eid=%s}: enter closing",
                                        eid
                                ), "elevator thread", eid
                        );
                        motionClosing();
                        break;
                    case RESETTING:
                        Debugger.dbgPrintln(
                                String.format(
                                        "@Thread{ElevatorThread,eid=%s}: enter resetting",
                                        eid
                                ), "elevator thread", eid
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
                    String.format("OPEN-%d-%s", elevator.getFloor(), eid)
            );
            sleep(openTime);
            HashSet<PersonRequest> unloaded = elevator.forceUnloadAll();
            server.addRequests(unloaded);
            sleep(closeTime);
            Debugger.timePrintln(
                    String.format("CLOSE-%d-%s", elevator.getFloor(), eid)
            );
        }
        // reset elevator status structures
        Debugger.timePrintln(
                String.format("RESET_BEGIN-%s", eid)
        );
        sleep(resetTime);
        this.moveTime = command.getResetSpeed();
        server.addRequests(elevator.reset(command));

        int resetTransFloor = command.getResetTransFloor();
        if (resetTransFloor != 0) {
            // TODO create para elevator and modify self
            ParaLock lock = new ParaLock();
            // create para
            String paraEid = eid + "-B";
            Elevator ev = new Elevator(paraEid, resetTransFloor,
                    elevator.getMaxFloor(), resetTransFloor, command);
            ElevatorThread et = new ElevatorThread(server, ev, paraEid, elevator, lock, command);
            server.addElevator(paraEid, ev, et);
            et.start();
            // modify self
            eid += "-A";
            elevator.setFloor(command.getResetTransFloor() - 1);
            elevator.setEid(elevator.getEid() + "-A");
            paraLock = lock;
            para = ev;
            elevator.setRange(elevator.getMinFloor(), resetTransFloor);
            elevator.setTransFloor(resetTransFloor);
        }
        Debugger.timePrintln(
                String.format("RESET_END-%s", eid.charAt(0))
        );
        // set state to MOVING and note the server
        elevator.setState(Elevator.State.MOVING);
        server.noteElevatorDirectionChange();
    }

    private void motionMoving() throws InterruptedException {
        Debugger.dbgPrintln(
                String.format(
                        "@Thread{ElevatorThread,eid=%s}: enter moving=%s",
                        eid, elevator.getDirection()
                ), "elevator thread", eid
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
        // CHECKME if moving to transferring floor(move only 1 flr), check for conflict
        // if is one of DCE:
        if (elevator.getTransFloor() != 0) {
            assert (paraLock != null);
            if (// if moving to transferring floor from neighbouring floor, conflicts possible
                    (elevator.isUpperDcElevator() && elevator.atFloor(elevator.getTransFloor() + 1)
                    && elevator.nextDirection() == -1)
                    ||
                    (!elevator.isUpperDcElevator() && elevator.atFloor(elevator.getTransFloor() - 1)
                    && elevator.nextDirection() == 1)
            ) {
                Debugger.dbgPrintln("self: conflict, checking para", "elevator thread");
                // will wait on any para conflicts until solved:
                // 1. idle(not moving with direction != STAY) at transFloor
                // 2. moving towards transFloor from neighbouring floor(direction != STAY)
                checkParaConflicts();
            }
        }

        // move one floor
        if (elevator.getDirection() != Elevator.Direction.STAY) {
            sleep(moveTime);
            elevator.moveOneFloor(elevator.getDirection());
            // output arrival info on each move
            Debugger.timePrintln(
                    String.format("ARRIVE-%d-%s", elevator.getFloor(), eid)
            );
            if (paraLock != null) {
                Debugger.dbgPrintln("para: notifying");
                paraLock.setLogic(true);
            }
        }
        // if the moving stage has been finished:
        if (command.getDestination() == elevator.getFloor()) {
            // if the elevator is full and only loading required on this floor,
            // jump the command once
            if (elevator.isFull() && !elevator.needUnloading()) {
                if (elevator.nextCommand(false).isReset()) {
                    return;
                }
                jump = true; // jump the command once
            } else {
                elevator.setState(Elevator.State.OPENING);          // open the door next
            }
        }
    }

    private void checkParaConflicts() throws InterruptedException {
        /*
        Para Lock design introduction:

        We're using the para lock to solve the following problems:
        1.
        2.
        */
        assert (para != null);
        // check partner elevator for any conflicts:
        boolean conflict = para.atFloor(para.getTransFloor());
        Debugger.dbgPrintln("cond1=" + conflict, "elevator thread");
        conflict |= (para.atFloor(para.getTransFloor() + 1)
                && para.nextDirection() == -1);
        Debugger.dbgPrintln("cond12=" + conflict, "elevator thread");
        conflict |= (para.atFloor(para.getTransFloor() - 1)
                && para.nextDirection() == 1);
        Debugger.dbgPrintln("cond123=" + conflict, "elevator thread");
        while (conflict) {
            // let para go first if conflict
            Debugger.dbgPrintln("para: conflict!", "elevator thread");
            elevator.setDirection(Elevator.Direction.STAY);
            paraLock.waitOn();
            Debugger.dbgPrintln("para: noted!", "elevator thread");
            conflict = para.atFloor(para.getTransFloor());
            Debugger.dbgPrintln("cond1=" + conflict, "elevator thread");
            conflict |= (para.atFloor(para.getTransFloor() + 1)
                    && para.nextDirection() == -1);
            Debugger.dbgPrintln("cond12=" + conflict, "elevator thread");
            conflict |= (para.atFloor(para.getTransFloor() - 1)
                    && para.nextDirection() == 1);
            Debugger.dbgPrintln("cond123=" + conflict, "elevator thread");
        }
    }

    private void motionOpening() throws InterruptedException {
        // start opening the door
        Debugger.timePrintln(
                String.format("OPEN-%d-%s", elevator.getFloor(), eid)
        );
        sleep(openTime);
        elevator.setState(Elevator.State.CLOSING);              // load and unload passengers next
    }

    private void motionClosing() throws InterruptedException {
        // wait before closing the door
        sleep(closeTime);
        // calc next direction
        int dirFlag = elevator.nextDirection();
        Debugger.dbgPrintln("dir_load_rem=" + dirFlag, "elevator thread", eid);
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
        elevator.unloadPassengers(server);
        // loading
        jump = elevator.loadPassengers(dirFlag);
        Debugger.dbgPrintln("set jump=" + jump, "elevator thread", eid);
        elevator.removeCurCommand(dirFlag, jump);  // the current command finished, remove it
        // finished closing the door
        Debugger.timePrintln(
                String.format("CLOSE-%d-%s", elevator.getFloor(), eid)
        );
        elevator.setState(Elevator.State.MOVING);  // now the elevator is free to move again
    }
}
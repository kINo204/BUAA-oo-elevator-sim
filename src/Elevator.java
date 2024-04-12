import com.oocourse.elevator3.DoubleCarResetRequest;
import com.oocourse.elevator3.NormalResetRequest;
import com.oocourse.elevator3.PersonRequest;
import com.oocourse.elevator3.Request;
import tools.Debugger;

import java.util.HashSet;
import java.util.Iterator;

//  thread safe
public class Elevator {
    private final CommandList commandList;  // list of commands waiting to be executed
    private final HashSet<PersonRequest> passengers;
    private final FloorRequestTable floorRequestTable;  // the fr_table
    private String eid;
    private int maxSpace = 6;
    // a table of request scheduled to be handled by the current elevator
    private int floor;  // current floor
    private int minFloor = 1;
    private int maxFloor = 11;
    private int transFloor;

    public synchronized boolean containRange(PersonRequest request) {
        notifyAll();
        return
                request.getFromFloor() >= minFloor && request.getFromFloor() <= maxFloor
                && request.getToFloor() >= minFloor && request.getToFloor() <= maxFloor;
    }

    public synchronized int fitRange(PersonRequest request) {
        assert (containRange(request));
        return maxFloor - minFloor - Math.abs(request.getToFloor() - request.getFromFloor());
    }

    public synchronized void setEid(String s) {
        eid = s;
    }

    public synchronized String getEid() {
        return eid;
    }

    public synchronized void setFloor(int i) {
        floor = i;
        notifyAll();
    }

    public enum State {
        MOVING, OPENING, CLOSING, RESETTING
    }

    private State state;  // the processing state of the current command

    public enum Direction {
        UP, DOWN, STAY
    }

    private Direction direction;

    Elevator(String eid) {
        this.eid = eid;
        floor = 1;
        transFloor = 0;
        state = State.MOVING;
        direction = Direction.STAY;
        commandList = new CommandList(minFloor, maxFloor);
        passengers = new HashSet<>();
        floorRequestTable = new FloorRequestTable(minFloor, maxFloor);
    }

    Elevator(String eid, int minFloor, int maxFloor, int transFloor, Command command) {
        this.eid = eid;
        state = State.MOVING;
        direction = Direction.STAY;
        this.maxSpace = command.getResetLoad();
        // set initial floor
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.transFloor = transFloor;
        assert (minFloor == transFloor || maxFloor == transFloor);
        if (minFloor == transFloor) {
            floor = transFloor + 1;
        } else {
            floor = transFloor - 1;
        }
        commandList = new CommandList(minFloor, maxFloor);
        passengers = new HashSet<>();
        floorRequestTable = new FloorRequestTable(minFloor, maxFloor);
    }

    public synchronized HashSet<PersonRequest> reset(Command command) {
        // State is not set here as it'll affect scheduling process,
        // and thus put at the end of ElevatorThread::motionReset(),
        // after output "RESET_END".
        this.direction = Direction.STAY;
        this.maxSpace = command.getResetLoad();
        this.commandList.reset();
        notifyAll();
        return this.floorRequestTable.reset();
    }

    /**
     * <p>
     *     This method is called in the ServerThread.schedule(). When
     *     a new request is scheduled to the specified elevator, it
     *     immediately calls this method, so the elevator
     *     can always keep its commandList refreshed just in time.
     * </p>
     */
    public synchronized void addRequest(Request inputRequest) {
        if (inputRequest instanceof PersonRequest) {
            PersonRequest request = (PersonRequest) inputRequest;
            // modify the fr_table
            floorRequestTable.addRequest(request);
            // modify the command list table
            commandList.addEntry(request);  // this will write a U/D entry to the table
        } else if (inputRequest instanceof NormalResetRequest) {
            NormalResetRequest request = (NormalResetRequest) inputRequest;
            commandList.addReset(request.getCapacity(), request.getSpeed(),
                    0);
        } else if (inputRequest instanceof DoubleCarResetRequest) {
            DoubleCarResetRequest request = (DoubleCarResetRequest) inputRequest;
            commandList.addReset(request.getCapacity(), request.getSpeed(),
                    request.getTransferFloor());
        }
        notifyAll();
    }

    /**
     * Figure out which direction the elevator should go next.
     * <p>
     *     The method search along the elevator's last direction,
     *     and decides if it should reverse its direction next.
     * </p>
     * @implNote If the elevator's direction is STAY, the method
     * will consider both sides(up and down); if not enough info is
     * get, it automatically returns the direction UP.
     * @return the next direction for the elevator
     */
    public synchronized int nextDirection() {
        if (commandList.isEmpty() && floor == transFloor) {
            return minFloor == transFloor ? 1 : -1;
        } else if (commandList.isEmpty()) {
            return 0; // TODO ?? can this work right
        }
        // ret = false if direction is STAY
        boolean ret = commandList.hasEntryInDirection(floor, direction);
        Debugger.dbgPrintln("- hasEntryInDir=" + ret, "elevator", eid);
        notifyAll();
        switch (direction) {
            case UP:
                return ret ? 1 : -1;
            case DOWN:
                return ret ? -1 : 1;
            default: // STAY
                Debugger.dbgPrintln("STAY:", "elevator");
                boolean upward = commandList.hasEntryInDirection(floor, Direction.UP);
                boolean downward = commandList.hasEntryInDirection(floor, Direction.DOWN);
                Debugger.dbgPrintln("\thasEntryInDir Up=" + upward, "elevator", eid);
                Debugger.dbgPrintln("\thasEntryInDir Dw=" + downward, "elevator", eid);
                if (upward) { // upward is of higher priority
                    return 1;
                } else if (downward) {
                    return -1;
                } else {
                    return 1;
                }
        }
    }

    /**
     * If the command list is not empty, ask it for a next command.
     * @param jumpCurrent if true, jump over the command when it's on the current floor
     * @return the next command for the elevator
     * @throws InterruptedException if the current thread is interrupted in waiting
     */
    public synchronized Command nextCommand(boolean jumpCurrent) throws InterruptedException {
        // CHECKME for DCE: if IDLE at transferring floor, move away one floor and continue
        if (commandList.isEmpty() && floor == transFloor) {
            return new Command(
                    minFloor == transFloor ? transFloor + 1 : transFloor - 1, 0);
        }
        if (commandList.isEmpty()) {
            wait();  // command not ended, but no new commands yet
        }
        if (commandList.isEmpty()) {
            return null;  // command might have ended, loop and try again
        }
        Command ret = commandList.nextCommand(floor, direction, jumpCurrent);
        Debugger.dbgPrintln(ret, "command", eid);  // debug print the command get
        Debugger.dbgPrintln(commandList, "commandlist", eid);
        notifyAll();
        return ret;
    }

    public synchronized void removeCurCommand(int dirFlag, boolean jump) {
        commandList.removeCurCommand(floor, dirFlag, jump, passengers);
        notifyAll();
    }

    public synchronized boolean loadPassengers(int dirFlag) {
        // calculate space left on elevator
        int restSpace = maxSpace - passengers.size();
        if (restSpace == 0) {
            return true;
        }
        final boolean overFlow = restSpace < floorRequestTable.getFloorWaiterNum(floor, dirFlag);
        // get loaded passengers from fr_table and load them
        HashSet<PersonRequest> loadedPassengers =
                floorRequestTable.getFloorWaiters(floor, dirFlag, restSpace);
        passengers.addAll(loadedPassengers);
        // print loading messages
        for (PersonRequest personRequest : loadedPassengers) {
            Debugger.timePrintln(
                    String.format(
                            "IN-%d-%d-%s",
                            personRequest.getPersonId(), floor, eid
                    )
            );
        }
        notifyAll();
        return overFlow;
    }

    public synchronized boolean needUnloading() {
        for (PersonRequest personRequest : passengers) {
            if (personRequest.getToFloor() == floor) {
                return true;
            }
        }
        notifyAll();
        return false;
    }

    public synchronized void unloadPassengers(ServerThread server) {
        Iterator<PersonRequest> iterator = passengers.iterator();
        PersonRequest personRequest;
        while (iterator.hasNext()) {
            personRequest = iterator.next();
            if (personRequest.getToFloor() == floor) {
                Debugger.timePrintln(
                        String.format(
                                "OUT-%d-%d-%s",
                                personRequest.getPersonId(), floor, eid
                        )
                );
                // CHECKME for ParaRequest, send next request to Server now
                if (personRequest instanceof ParaRequest) {
                    HashSet<PersonRequest> hashSet = new HashSet<>();
                    hashSet.add(((ParaRequest) personRequest).getNextRequest());
                    server.addRequests(hashSet);
                    Debugger.dbgPrintln("ParaRequest sent", "elevator");
                }
                iterator.remove();
            }
        }
        notifyAll();
    }

    public synchronized boolean isFull() {
        boolean ret = passengers.size() == maxSpace;
        notifyAll();
        return ret;
    }

    public synchronized int getLoad() {
        int ret = passengers.size();
        notifyAll();
        return ret;
    }

    public synchronized HashSet<PersonRequest> forceUnloadAll() {
        HashSet<PersonRequest> unloaded = new HashSet<>();
        for (PersonRequest personRequest : passengers) {
            // force unloading the passenger
            Debugger.timePrintln(
                    String.format(
                            "OUT-%d-%d-%s",
                            personRequest.getPersonId(), floor, eid
                    )
            );
            // create new request from the unfinished origin request
            PersonRequest newRequest = new PersonRequest(
                    floor,
                    personRequest.getToFloor(),
                    personRequest.getPersonId()
            );
            unloaded.add(newRequest);
        }
        passengers.clear();
        notifyAll();
        return unloaded;
    }

    public synchronized State getState() {
        notifyAll();
        return state;
    }

    public synchronized void setState(State state) {
        this.state = state;
        notifyAll();
    }

    public synchronized void setEnd() {
        commandList.setEnd(true);
        notifyAll();
    }

    public synchronized Direction getDirection() {
        notifyAll();
        return direction;
    }

    public synchronized void setDirection(Direction direction) {
        this.direction = direction;
        notifyAll();
    }

    public synchronized int getTransFloor() {
        notifyAll();
        return transFloor;
    }

    public synchronized void setRange(int min, int max) {
        minFloor = min;
        maxFloor = max;
        commandList.setRange(min, max);
        floorRequestTable.setRange(min, max);
        notifyAll();
    }

    public synchronized boolean isCommandEmpty() {
        boolean ret = commandList.isEmpty();
        notifyAll();
        return ret;
    }

    public synchronized boolean isCommandEnd() {
        boolean ret = commandList.isEnd();
        notifyAll();
        return ret;
    }

    public synchronized int getFloor() {
        notifyAll();
        return floor;
    }

    public synchronized void moveOneFloor(Direction direction) {
        switch (direction) {
            case UP:
                floor++;
                break;
            case DOWN:
                floor--;
                break;
            default:
        }
        notifyAll();
    }

    public synchronized boolean isFeedbackRequestEnd() {
        notifyAll();
        boolean ret = true;
        for (PersonRequest personRequest : passengers) {
            if (personRequest instanceof ParaRequest) {
                ret = false;
                break;
            }
        }
        ret &= !floorRequestTable.hasParaReq();
        return !commandList.isReset() && ret;
    }

    public synchronized boolean atFloor(int toCompare) {
        notifyAll();
        return toCompare == floor;
    }

    public synchronized int getMinFloor() {
        notifyAll();
        return minFloor;
    }

    public synchronized int getMaxFloor() {
        notifyAll();
        return maxFloor;
    }

    public synchronized void setTransFloor(int transFloor) {
        this.transFloor = transFloor;
        notifyAll();
    }

    public synchronized boolean isUpperDcElevator() {
        assert transFloor != 0;
        notifyAll();
        return minFloor == transFloor;
    }
}

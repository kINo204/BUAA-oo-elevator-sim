import com.oocourse.elevator1.PersonRequest;

import java.util.HashSet;
import java.util.Iterator;

// TODO not thread safe
public class Elevator {
    private final CommandList commandList;  // list of commands waiting to be executed
    private final HashSet<PersonRequest> passengers;
    private final int maxSpace = 6;
    // a table of request scheduled to be handled by the current elevator
    private final FloorRequestTable floorRequestTable;  // the fr_table
    private int floor;  // current floor
    private final int minFloor = 1;
    private final int maxFloor = 11;

    public synchronized boolean loadPassengers(int dirFlag, int eid) {
        int restSpace = maxSpace - passengers.size();
        if (restSpace == 0) {
            return true;
        }
        boolean overFlow = restSpace < floorRequestTable.getFloorWaiterNum(floor, dirFlag);
        HashSet<PersonRequest> loadedPassengers = floorRequestTable.getFloorWaiters(floor, dirFlag, restSpace);
        passengers.addAll(loadedPassengers);
        for (PersonRequest personRequest : loadedPassengers) {
            Debugger.timePrintln(
                    String.format(
                            "IN-%d-%d-%d",
                            personRequest.getPersonId(), floor, eid
                    )
            );
        }
        notifyAll();
        return overFlow;
    }

    public synchronized void unloadPassengers(int eid) {
        Iterator<PersonRequest> iterator = passengers.iterator();
        PersonRequest personRequest;
        while (iterator.hasNext()) {
            personRequest = iterator.next();
            if (personRequest.getToFloor() == floor) {
                Debugger.timePrintln(
                        String.format(
                                "OUT-%d-%d-%d",
                                personRequest.getPersonId(), floor, eid
                        )
                );
                iterator.remove();
            }
        }
        notifyAll();
    }

    public enum State {
        MOVING, OPENING, CLOSING
    }

    private State state;  // the processing state of the current command

    public enum Direction {
        UP, DOWN, STAY
    }

    public synchronized int nextDirection() {
        if (direction == Direction.STAY) {
            return 1; // forever look up
        }
        boolean ret = commandList.hasEntryInDirection(floor, direction);
        notifyAll();
        switch (direction) {
            case UP:
                return ret ? 1 : -1;
            case DOWN:
                return ret ? -1 : 1;
            default:
                return 0; // TODO
        }
    }

    private Direction direction;

    Elevator() {
        floor = 1;
        state = State.MOVING;
        direction = Direction.STAY;
        commandList = new CommandList(minFloor, maxFloor);
        passengers = new HashSet<>();
        floorRequestTable = new FloorRequestTable(minFloor, maxFloor);
    }

    /**
     * <p>
     *     This method is called in the ServerThread.schedule(). When
     *     a new request is scheduled to the specified elevator, it
     *     immediately calls this method, so the elevator
     *     can always keep its commandList refreshed just in time.
     * </p>
     */
    public synchronized void addRequest(PersonRequest request) {
        // modify the fr_table
        floorRequestTable.addRequest(request);
        // modify the command list table
        commandList.addEntry(request);  // this will write a U/D entry to the table
        notifyAll();
    }



    // TODO should we clone a command?
    public synchronized Command nextCommand(boolean jumpCurrent) throws InterruptedException {
        if (commandList.isEmpty()) {
            wait();  // command not ended, but no new commands yet
        }
        if (commandList.isEmpty()) {
            return null;  // command might have ended, loop and try again
        }
        Command ret = commandList.nextCommand(floor, direction, jumpCurrent);
        Debugger.dbgPrintln(ret);  // debug print the command get
        notifyAll();
        return ret;
    }

    public synchronized void removeCurCommand(int dirFlag) {
        commandList.removeCurCommand(floor, dirFlag); // TODO what direction
        notifyAll();
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
}

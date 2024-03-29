import com.oocourse.elevator1.PersonRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

// TODO not thread safe
public class CommandList {
    private final ArrayList<HashSet<CommandTableEntry>> commandTable;
    private final int minFloor;
    private final int maxFloor;
    private boolean end;

    CommandList(int minFloor, int maxFloor) {
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        commandTable = new ArrayList<>();
        for (int i = 0; i < maxFloor - minFloor + 1; i++) {
            commandTable.add(new HashSet<>());
        }
        end = false;
    }

    //    This method travels through the CLT and figure out the next command.
    //
    //    Algorithm
    //        newCommand:(dst, 0)
    //        set the dst to the floor of the first found item below:
    //            a. from the current floor, to the current direction:
    //                look for a U/D same as the current direction; or an E
    //                if unable, look for a U/D of different direction
    //            b. if still unable, look to the reversed direction:
    //                look for a U/D same as the current direction; or an E
    //                if unable, look for a U/D of different direction
    //            c. if nothing found, do not give a command
    public synchronized Command nextCommand(int floor, Elevator.Direction direction) {
        // TODO after a STAY command, the elevator always chooses to go up
        int la1;
        int la2;
        int lb;
        int lc1;
        int lc2;
        int ld;
        int dirFlag;
        if (direction != Elevator.Direction.DOWN) {
            dirFlag = 1;
            la1 = lookingLength(floor, 1, CommandTableEntry.Direction.UP);
            la2 = lookingLength(floor, 1, CommandTableEntry.Direction.END);
            lb  = lookingLength(floor, 1, CommandTableEntry.Direction.DOWN);
            lc1 = lookingLength(floor, -1, CommandTableEntry.Direction.DOWN);
            lc2 = lookingLength(floor, -1, CommandTableEntry.Direction.END);
            ld  = lookingLength(floor, -1, CommandTableEntry.Direction.UP);
        } else {
            dirFlag = -1;
            la1 = lookingLength(floor, 1, CommandTableEntry.Direction.DOWN);
            la2 = lookingLength(floor, 1, CommandTableEntry.Direction.END);
            lb  = lookingLength(floor, 1, CommandTableEntry.Direction.UP);
            lc1 = lookingLength(floor, -1, CommandTableEntry.Direction.UP);
            lc2 = lookingLength(floor, -1, CommandTableEntry.Direction.END);
            ld  = lookingLength(floor, -1, CommandTableEntry.Direction.DOWN);
        }
        int destination;
        if (la1 != -1 && la2 != -1) {
            destination = floor + dirFlag * (Math.min(la1, la2));
        } else if (la1 != -1 || la2 != -1) {
            destination = floor + dirFlag * (la1 == -1 ? la2 : la1);
        } else if (lb != -1) {
            destination = floor + dirFlag * lb;
        } else if (lc1 != -1 && lc2 != -1) {
            destination = floor + dirFlag * (Math.min(lc1, lc2));
        } else if (lc1 != -1 || lc2 != -1) {
            destination = floor + dirFlag * (lc1 == -1 ? lc2 : lc1);
        } else if (ld != -1) {
            destination = floor + dirFlag * ld;
        } else {
            return null;  // the table is empty
        }
        return new Command(destination, 0);
    }

    // -1 indicates no entry found
    private synchronized int lookingLength(
            int startFloor, int dirFlag, CommandTableEntry.Direction targetDir) {
        for (int i = startFloor; i >= 0 && i < maxFloor - minFloor + 1; i += dirFlag) {
            for (CommandTableEntry entry : commandTable.get(i)) {
                if (entry.getDirection() == targetDir) {
                    return dirFlag * (i - startFloor);
                }
            }
        }
        return -1;
    }

    public synchronized boolean isEmpty() {
        boolean ret = true;
        for (HashSet<CommandTableEntry> hashSet : commandTable) {
            if (!hashSet.isEmpty()) {
                ret = false;
                break;
            }
        }
        notifyAll();
        return ret;
    }

    public synchronized boolean isEnd() {
        notifyAll();
        return end;
    }

    public synchronized void setEnd(boolean isEnd) {
        end = isEnd;
        notifyAll();
    }

    // when the elevator thread finish closing the door, it informs the command list
    // to perform refreshing through this method
    public synchronized void removeCurCommand(int floor, Elevator.Direction direction) {
        HashSet<CommandTableEntry> hashSet = commandTable.get(floor);
        Iterator<CommandTableEntry> iterator = hashSet.iterator();
        CommandTableEntry entry;
        while (iterator.hasNext()) {
            entry = iterator.next();
            switch (entry.getDirection()) {
                case END:  // simply delete an END entry
                    iterator.remove();
                    break;
                // for a non-END entry, only process it when of the same direction
                case UP:
                    // create a new entry of END, and remove
                    if (direction != Elevator.Direction.DOWN) {
                        addEntry(
                                entry.getNextDestination(),
                                new CommandTableEntry(CommandTableEntry.Direction.END, 0)
                        );
                        iterator.remove();
                    }
                    break;
                case DOWN:
                    // same as UP
                    if (direction != Elevator.Direction.UP) {
                        addEntry(
                                entry.getNextDestination(),
                                new CommandTableEntry(CommandTableEntry.Direction.END, 0)
                        );
                        iterator.remove();
                    }
                    break;
                default:
            }
        }
    }

    public synchronized void addEntry(PersonRequest request) {
        // create CTE from the request
        CommandTableEntry cte = requestToCte(request);
        addEntry(request.getFromFloor(), cte);
    }

    public synchronized void addEntry(int floor, CommandTableEntry cte) {
        // look for identical CTE in the from_floor
        boolean match = false;
        for (CommandTableEntry entry : commandTable.get(floor)) {
            if (entry.equals(cte)) {
                match = true;
                break;
            }
        }
        // if there isn't any, add the new one to the from_floor
        if (!match) {
            commandTable.get(floor).add(cte);
        }
    }

    private synchronized CommandTableEntry requestToCte(PersonRequest request) {
        CommandTableEntry.Direction direction;
        if (request.getFromFloor() > request.getToFloor()) {
            direction = CommandTableEntry.Direction.DOWN;
        } else if (request.getFromFloor() < request.getToFloor()) {
            direction = CommandTableEntry.Direction.UP;
        } else {
            direction = CommandTableEntry.Direction.END; // TODO = is END direction?
        }
        return new CommandTableEntry(direction, request.getToFloor());
    }

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("@CommandList{");
        for (int i = 0; i < maxFloor - minFloor + 1; i++) {
            sb.append(i).append(":\n");
            for (CommandTableEntry c : commandTable.get(i)) {
                sb.append(c.toString());
            }
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}

import com.oocourse.elevator1.PersonRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

// thread safe

/**
 * As part of the single-elevator control algorithm, the command list conserves
 * a command table, and provides various methods of modifying or getting info
 * from the table. Generally, the class receive requests, modify itself with
 * the elevator's feedback, and provide calculated next command for the elevator.
 */
public class CommandList {
    /**
     * A table of command table entries listed in their own floor. This is the
     * key data structure of command list.
     * <p>
     *     The command table illustrates the elevator's current tasks, and
     *     provide a data structure for elevator to figure out its next command.
     *     It is dynamically modified by new requests, and the elevator's signal
     *     of completion of its tasks.
     * </p>
     *
     * @see CommandTableEntry
     */
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

    /**
     * Walk through the command table and figure out the next command.
     * @param floor         the current floor of the elevator
     * @param direction     the direction state of the elevator(its direction in the last movement)
     * @param jumpCurrent   if true, jump over the command when it's on the current floor, e.g. when
     *                      the elevator is full and won't stop on any command
     * @return              the next command which the elevator should carry out
     */
    public synchronized Command nextCommand(
            int floor, Elevator.Direction direction, boolean jumpCurrent) {
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
            la1 = lookingLength(true, floor, 1, CommandTableEntry.Direction.UP, jumpCurrent);
            la2 = lookingLength(true, floor, 1, CommandTableEntry.Direction.END, jumpCurrent);
            lb  = lookingLength(false, floor, 1, CommandTableEntry.Direction.DOWN, jumpCurrent);
            lc1 = lookingLength(true, floor, -1, CommandTableEntry.Direction.DOWN, jumpCurrent);
            lc2 = lookingLength(true, floor, -1, CommandTableEntry.Direction.END, jumpCurrent);
            ld  = lookingLength(false, floor, -1, CommandTableEntry.Direction.UP, jumpCurrent);
        } else {
            dirFlag = -1;
            la1 = lookingLength(true, floor, -1, CommandTableEntry.Direction.DOWN, jumpCurrent);
            la2 = lookingLength(true, floor, -1, CommandTableEntry.Direction.END, jumpCurrent);
            lb  = lookingLength(false, floor, -1, CommandTableEntry.Direction.UP, jumpCurrent);
            lc1 = lookingLength(true, floor, 1, CommandTableEntry.Direction.UP, jumpCurrent);
            lc2 = lookingLength(true, floor, 1, CommandTableEntry.Direction.END, jumpCurrent);
            ld  = lookingLength(false, floor, 1, CommandTableEntry.Direction.DOWN, jumpCurrent);
        }
        int destination;
        if (la1 == -1 && la2 == -1 && lb == -1) {
            dirFlag = -dirFlag;
        }
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
        notifyAll();
        return new Command(destination, 0);
    }

    /**
     * Walk through the command table to find out if there exists a valid CTE
     * in the given direction.
     * <p>
     *     A valid CTE includes any CTE to the given direction from the
     *     current floor(the current floor itself excluded), or a
     *     non-END CTE in the current floor whose next destination(where it
     *     will generate an END entry later) is in the desired direction.
     * </p>
     * @implNote STAY is an invalid direction! The method has not enough
     * information to deal with it, and will give a meaningless false value.
     *
     * @param floor         the current floor the elevator is at
     * @param direction     the desired searching direction, often directly from
     *                      the elevator's current direction.
     * @return              a boolean value indicating if there is a valid CTE in
     * the given direction
     */
    public synchronized boolean hasEntryInDirection(int floor, Elevator.Direction direction) {
        // NOTE: direction STAY invalid
        if (direction == Elevator.Direction.STAY) {
            return false;  // direction STAY ambiguous, assert false
        }
        int dirFlag = direction == Elevator.Direction.UP ? 1 : -1;
        // if there's an entry in the direction(cur floor excluded)
        for (int i = floor - 1; i >= 0 && i < maxFloor - minFloor + 1; i += dirFlag) {
            if ((i != floor - 1) && (!commandTable.get(i).isEmpty())) {
                return true;
            }
        }
        // if the current floor's entry could generate an END entry later
        for (CommandTableEntry entry : commandTable.get(floor - 1)) {
            if (entry.getDirection() == CommandTableEntry.Direction.END) {
                continue;  // END entry can't create a new entry
            }
            int dir = entry.getDirection() == CommandTableEntry.Direction.UP ? 1 : -1;
            if (dir == dirFlag) {
                return true;
            }
        }
        notifyAll();
        return false;
    }

    // -1 indicates no entry found
    private synchronized int lookingLength(
            boolean shortest, int startFloor, int dirFlag,
            CommandTableEntry.Direction targetDir, boolean jumpCurrent
    ) {
        int last = -1;
        assert dirFlag != 0;
        for (int i = startFloor - 1; i >= 0 && i < maxFloor - minFloor + 1; i += dirFlag) {
            for (CommandTableEntry entry : commandTable.get(i)) {
                if (entry.getDirection() == targetDir) {
                    if (jumpCurrent) {
                        if (i == startFloor - 1) {
                            continue;
                        }
                    }
                    // refresh `last`
                    last = dirFlag * (i - startFloor + 1);
                    if (shortest) {
                        notifyAll();
                        return last;
                    }

                }
            }
        }
        notifyAll();
        if (!shortest) {
            return last;
        } else {
            return -1;
        }
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

    /**
     * Clear the specified entries in the command table.
     * <p>
     *     When the elevator thread finish closing the door, it informs the command list
     *     to perform refreshing by calling the method.
     * </p>
     * @param floor the current floor the elevator is at
     * @param dirFlag the direction the elevator is going next. NOT the elevator's
     *                direction state!
     */
    public synchronized void removeCurCommand(int floor, int dirFlag) {
        HashSet<CommandTableEntry> hashSet = commandTable.get(floor - 1);
        Iterator<CommandTableEntry> iterator = hashSet.iterator();
        CommandTableEntry entry;
        while (iterator.hasNext()) {
            entry = iterator.next();
            switch (entry.getDirection()) {
                case END:  // simply delete an END entry, has nothing to do with dirFlag!
                    iterator.remove();
                    break;
                // for a non-END entry, only process it when of the same direction
                case UP:
                    // create a new entry of END, and remove
                    if (dirFlag != -1) {
                        addEntry(
                                entry.getNextDestination(),
                                new CommandTableEntry(CommandTableEntry.Direction.END, 0)
                        );
                        iterator.remove();
                    }
                    break;
                case DOWN:
                    // same as UP
                    if (dirFlag != 1) {
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
        notifyAll();
    }

    public synchronized void addEntry(PersonRequest request) {
        // create CTE from the request
        CommandTableEntry cte = requestToCte(request);
        addEntry(request.getFromFloor(), cte);
        notifyAll();
    }

    public synchronized void addEntry(int floor, CommandTableEntry cte) {
        // look for identical CTE in the from_floor
        boolean match = false;
        for (CommandTableEntry entry : commandTable.get(floor - 1)) {
            if (entry.equals(cte)) {
                match = true;
                break;
            }
        }
        // if there isn't any, add the new one to the from_floor
        if (!match) {
            commandTable.get(floor - 1).add(cte);
        }
        notifyAll();
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
        notifyAll();
        return new CommandTableEntry(direction, request.getToFloor());
    }

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("@CommandList{\n");
        for (int i = 0; i < maxFloor - minFloor + 1; i++) {
            sb.append(i).append(": ");
            for (CommandTableEntry c : commandTable.get(i)) {
                sb.append(c.toString());
            }
            sb.append("\n");
        }
        sb.append("}");
        notifyAll();
        return sb.toString();
    }
}

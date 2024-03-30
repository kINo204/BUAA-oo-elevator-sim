// thread safe
public class CommandTableEntry {
    public enum Direction {
        UP, DOWN, END
    }

    private final Direction direction;
    /**
     * The next destination of a CTE, if it's of type UP or DOWN(a starting CTE).
     */
    private final int nextDestination;

    CommandTableEntry(Direction direction, int nextDestination) {
        this.direction = direction;
        this.nextDestination = nextDestination;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getNextDestination() {
        return nextDestination;
    }

    @Override
    public boolean equals(Object entry) {
        if (!(entry instanceof CommandTableEntry)) {
            return false;
        }
        CommandTableEntry commandTableEntry = (CommandTableEntry) entry;
        return commandTableEntry.direction == this.direction
                && commandTableEntry.nextDestination == this.nextDestination;
    }

    @Override
    public String toString() {
        String str = "@CommandTableEntry{dir=";
        if (direction == Direction.UP) {
            str += "U";
        } else if (direction == Direction.DOWN) {
            str += "D";
        } else {
            str += "E";
        }
        str += ",nextDst=";
        str += nextDestination;
        str += "}";
        return str;
    }
}

import com.oocourse.elevator2.PersonRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

// thread safe

/**
 * A structure listing requests in their from-floors.
 * It keeps the information of requests, adds new requests to the specified floor in the table,
 * and removes the loaded passengers when informed by the elevator.
 */
public class FloorRequestTable {
    /**
     * A table recording all requests' status, listed by there from-floor.
     */
    private final ArrayList<HashSet<PersonRequest>> table;
    private final int minFloor;
    private final int maxFloor;

    FloorRequestTable(int minFloor, int maxFloor) {
        // initialize floor-request table
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        table = new ArrayList<>();
        for (int i = 0; i < maxFloor - minFloor + 1; i++) {
            table.add(new HashSet<>());
        }
    }

    public synchronized HashSet<PersonRequest> reset() {
        HashSet<PersonRequest> unfinishedReq = new HashSet<>();
        for (HashSet<PersonRequest> hashSet : table) {
            unfinishedReq.addAll(hashSet);
        }
        for (HashSet<PersonRequest> hashSet : table) {
            hashSet.clear();
        }
        notifyAll();
        return unfinishedReq;
    }

    /**
     * Search the fr_table to find out how many waiters can be picked
     * up in the given floor by the elevator of the given direction.
     * @param floor the target floor to be inquired
     * @param dirFlag the direction the elevator is going next
     * @return the number of waiters(requests) matching the requirement
     * @implNote    The dirFlag is not the direction of the elevator itself,
     *              but where it's going next.
     */
    public synchronized int getFloorWaiterNum(int floor, int dirFlag) {
        int num = 0;
        for (PersonRequest personRequest : table.get(floor - 1)) {
            int dir = personRequest.getFromFloor() < personRequest.getToFloor() ?
                    1 : -1; // same floor not allowed
            if (dir == dirFlag) {
                num++;
            }
        }
        notifyAll();
        return num;
    }

    /**
     * Read the fr_table and give a list of loadable waiters.
     * <p>
     *     If the elevator can't carry all waiters available, the
     *     method only get part of it so that the elevator barely be filled.
     * </p>
     * @param floor         the floor to search
     * @param dirFlag       the direction the elevator is going next
     * @param restSpace     the space left on the elevator
     * @return              a set of waiters satisfying the conditions
     */
    public synchronized HashSet<PersonRequest> getFloorWaiters(
            int floor, int dirFlag, int restSpace) {
        HashSet<PersonRequest> ret = new HashSet<>(table.get(floor - 1));
        HashSet<PersonRequest> toRemove = new HashSet<>();
        int num = 0;
        for (PersonRequest personRequest : ret) {
            int dir = personRequest.getFromFloor() < personRequest.getToFloor() ?
                    1 : -1; // same floor not allowed
            if (dir == dirFlag) {
                toRemove.add(personRequest);
                num++;
                if (num == restSpace) {
                    break;
                }
            }
        }
        Iterator<PersonRequest> iterator = ret.iterator();
        while (iterator.hasNext()) {
            if (!toRemove.contains(iterator.next())) {
                iterator.remove();
            }
        }
        table.get(floor - 1).removeAll(toRemove); // loaded waiters deleted
        notifyAll();
        return ret;
    }

    public synchronized void addRequest(PersonRequest request) {
        // add the request to its from-floor
        table.get(request.getFromFloor() - 1).add(request);
        notifyAll();
    }
}
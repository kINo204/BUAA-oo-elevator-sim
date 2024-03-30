import com.oocourse.elevator1.PersonRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

// TODO not thread safe
public class FloorRequestTable {
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

    public synchronized int getFloorWaiterNum(int floor, int dirFlag) {
        int num = 0;
        for (PersonRequest personRequest : table.get(floor - 1)) {
            int dir = personRequest.getFromFloor() < personRequest.getToFloor() ?
                    1 : -1; // TODO same floor not considered
            if (dir == dirFlag) {
                num++;
            }
        }
        notifyAll();
        return num;
    }

    public synchronized HashSet<PersonRequest> getFloorWaiters(
            int floor, int dirFlag, int restSpace) {
        HashSet<PersonRequest> ret = new HashSet<>(table.get(floor - 1));
        HashSet<PersonRequest> toRemove = new HashSet<>();
        int num = 0;
        for (PersonRequest personRequest : ret) {
            int dir = personRequest.getFromFloor() < personRequest.getToFloor() ?
                    1 : -1; // TODO same floor not considered
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
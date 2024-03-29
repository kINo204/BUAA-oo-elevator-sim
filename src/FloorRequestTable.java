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

    public synchronized HashSet<PersonRequest> getFloorWaiters(int floor, int dirFlag) {
        HashSet<PersonRequest> ret = new HashSet<>(table.get(floor));
        HashSet<PersonRequest> toRemove = new HashSet<>();
        Iterator<PersonRequest> iterator = ret.iterator();
        PersonRequest personRequest;
        while (iterator.hasNext()) {
            personRequest = iterator.next();
            int dir = personRequest.getFromFloor() < personRequest.getToFloor() ?
                    1 : -1; // TODO same floor not considered
            if (dir != dirFlag) { // request not the same direction as elevator
                iterator.remove();
            } else {
                toRemove.add(personRequest);
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
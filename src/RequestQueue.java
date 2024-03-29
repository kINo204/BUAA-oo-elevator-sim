import com.oocourse.elevator1.PersonRequest;

import java.util.ArrayList;

// thread safe
public class RequestQueue {
    private final ArrayList<PersonRequest> requests;
    private boolean isEnd;

    public RequestQueue() {
        requests = new ArrayList<>();
        this.isEnd = false;
    }

    public synchronized void addRequest(PersonRequest request) {
        requests.add(request);
        notifyAll();
    }

    public synchronized PersonRequest getRequest() {
        if (requests.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();  // TODO
            }
        }
        if (requests.isEmpty()) {
            return null;
        }
        PersonRequest request = requests.get(0);
        requests.remove(0);
        notifyAll();
        return request;
    }

    public synchronized void setEnd(boolean isEnd) {
        this.isEnd = isEnd;
        notifyAll();
    }

    public synchronized boolean isEnd() {
        notifyAll();
        return isEnd;
    }

    public synchronized boolean isEmpty() {
        notifyAll();
        return requests.isEmpty();
    }
}

import com.oocourse.elevator3.Request;

import java.util.ArrayList;

// thread safe
public class RequestQueue {
    private final ArrayList<Request> requests;
    private boolean isEnd;

    public RequestQueue() {
        requests = new ArrayList<>();
        this.isEnd = false;
    }

    public synchronized void addRequest(Request request) {
        requests.add(request);
        notifyAll();
    }

    public synchronized Request getRequest() {
        if (requests.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (requests.isEmpty()) {
            return null;
        }
        Request request = requests.get(0);
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

    public synchronized void note() {
        notifyAll();
    }
}

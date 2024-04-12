import com.oocourse.elevator3.PersonRequest;
import tools.Debugger;

public class BufferThread extends Thread {
    private boolean start = false;
    private RequestQueue requestQueue;
    private RequestQueue bufferQueue;

    public BufferThread(RequestQueue requestQueue, RequestQueue bufferQueue) {
        this.requestQueue = requestQueue;
        this.bufferQueue = bufferQueue;
    }

    @Override
    public synchronized void run() {
        try {
            while (true) {
                Debugger.dbgPrintln("@BufferThread: running", "buffer thread");
                if (!start) {
                    // exit condition: no more input, no buffed req
                    if (bufferQueue.isEnd() && bufferQueue.isEmpty()) {
                        Debugger.dbgPrintln("@BufferThread: exiting", "buffer thread");
                        return;
                    } else {
                        wait();
                        continue;
                    }
                }
                Debugger.dbgPrintln("@BufferThread: triggered", "buffer thread");
                while (!bufferQueue.isEmpty()) {
                    requestQueue.addRequest(bufferQueue.getRequest());
                }
                start = false;
            }
        } catch (InterruptedException e) { return; }
    }

    public synchronized void setStart(boolean start) {
        this.start = start;
        notifyAll();
    }

    public synchronized void addRequest(PersonRequest request) {
        bufferQueue.addRequest(request);
        Debugger.dbgPrintln("@BufferThread: buffed req", "buffer thread");
        notifyAll();
    }
}

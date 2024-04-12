public class ParaLock {
    private boolean logic = false;

    public synchronized void setLogic(boolean logic) {
        this.logic = logic;
        notifyAll();
    }

    public synchronized void waitOn() throws InterruptedException {
        while (!logic) {
            wait();
        }
        logic = false;
        notifyAll();
    }
}

// thread safe
public class Command {
    private final int destination;
    private final int waitTime;

    public Command(int destination, int waitTime) {
        this.destination = destination;
        this.waitTime = waitTime;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public int getDestination() {
        return destination;
    }

    @Override
    public String toString() {
        return String.format("@Command{dst=%d,wait_time=%d}", destination, waitTime);
    }
}
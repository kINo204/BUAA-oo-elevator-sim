// thread safe
public class Command {
    private final int destination; // floor to go
    private final int waitTime; // time to wait between opening and closing door
    private final boolean isReset;
    private final int resetLoad;
    private final int resetSpeed;
    private final int resetTransFloor;

    public Command(int destination, int waitTime) {
        this.destination = destination;
        this.waitTime = waitTime;
        this.isReset = false;
        this.resetLoad = 0;
        this.resetSpeed = 0;
        this.resetTransFloor = 0;
    }

    public Command(boolean isReset, int resetLoad, int resetSpeed, int resetTransFloor) {
        this.destination = 0;
        this.waitTime = 0;
        this.isReset = isReset;
        this.resetLoad = resetLoad;
        this.resetSpeed = resetSpeed;
        this.resetTransFloor = resetTransFloor;
    }

    public boolean isReset() {
        return isReset;
    }

    public int getResetLoad() { return resetLoad; }

    public int getResetSpeed() { return resetSpeed; }

    public int getWaitTime() { return waitTime; }

    public int getDestination() { return destination; }

    public int getResetTransFloor() {
        return resetTransFloor;
    }

    @Override
    public String toString() {
        if (isReset) {
            return String.format("@Command{RESET,load=%d,speed=%d}", resetLoad, resetSpeed);
        } else {
            return String.format("@Command{dst=%d,wait_time=%d}", destination, waitTime);
        }
    }
}
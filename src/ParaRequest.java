import com.oocourse.elevator3.PersonRequest;

public class ParaRequest extends PersonRequest {
    private final PersonRequest nextRequest;

    public ParaRequest(int fromFloor, int toFloor, int personId, PersonRequest nextRequest) {
        super(fromFloor, toFloor, personId);
        this.nextRequest = nextRequest;
    }

    public PersonRequest getNextRequest() {
        return nextRequest;
    }
}

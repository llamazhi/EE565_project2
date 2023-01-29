import java.util.concurrent.TimeoutException;

public class AIMDWithPacketLossDetection {
    private int cwnd; // Congestion window size
    private int ssthresh; // Slow start threshold
    private int increment; // Additive increase value
    private int decrement; // Multiplicative decrease value
    private int timeout; // Timeout for packet loss detection
    private int lastAck; // Sequence number of last acknowledged packet

    public AIMDWithPacketLossDetection() {
        this.cwnd = 1;
        this.ssthresh = Integer.MAX_VALUE;
        this.increment = 1;
        this.decrement = 2;
        this.timeout = 100; // 100ms timeout
        this.lastAck = 0;
    }

    public void sendPacket(int seqNum) throws TimeoutException {
        // Send packet with specified sequence number
        // ...

        // Wait for ACK
        int startTime = (int) System.currentTimeMillis();
        while (lastAck < seqNum) {
            int currentTime = (int) System.currentTimeMillis();
            if (currentTime - startTime > timeout) {
                // Packet loss detected
                decreaseCwnd();
                throw new TimeoutException("Packet loss detected");
            }
        }

        increaseCwnd();
    }

    public void receiveAck(int ackNum) {
        lastAck = ackNum;
    }

    public void increaseCwnd() {
        if (cwnd < ssthresh) {
            // Slow start
            cwnd += increment;
        } else {
            // Congestion avoidance
            cwnd += increment / cwnd;
        }
    }

    public void decreaseCwnd() {
        // Multiplicative decrease
        ssthresh = cwnd / decrement;
        cwnd = ssthresh;
    }

    public int getCwnd() {
        return cwnd;
    }
}

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class MyUDPClient {

    private final static int PORT = 7707;
    private static final String HOSTNAME = "localhost";
    private static int numPackets = 0;
    private static boolean[] ackSeqnum;
    // private static final byte[] ipAddr = new byte[] { 20, 106, 101, (byte) 156 };
    private final static int bufferSize = 20;

    public static void main(String[] args) {

        try (DatagramSocket socket = new DatagramSocket(0)) {
            socket.setSoTimeout(1000);
            InetAddress host = InetAddress.getByName(HOSTNAME);
            // InetAddress host = InetAddress.getByAddress(ipAddr);
            byte[] seqnumBytes = new byte[4];
            byte[] requestData = new byte[bufferSize];
            seqnumBytes = ByteBuffer.allocate(4).putInt(0).array();
            System.arraycopy(seqnumBytes, 0, requestData, 0, 4);
            byte[] messageBytes = "get test.txt".getBytes();
            System.arraycopy(messageBytes, 0, requestData, 4, messageBytes.length);
            DatagramPacket outPkt = new DatagramPacket(requestData, requestData.length, host, PORT);
            DatagramPacket inPkt = new DatagramPacket(new byte[bufferSize], bufferSize);
            socket.send(outPkt);
            try {
                // process the packet...
                socket.receive(inPkt);
                String result = new String(inPkt.getData(), 0, inPkt.getLength(), "US-ASCII");
                System.out.println(result);
                String[] responseValues = result.split(" ");
                numPackets = Integer.parseInt(responseValues[0]);
                int windowSize = Integer.parseInt(responseValues[1]);
                System.out.println("numPackets: " + numPackets + " windowSize: " + windowSize);
                ackSeqnum = new boolean[numPackets + 1];
                for (int i = 0; i < numPackets; i++) {
                    requestData = new byte[bufferSize];
                    requestData = ByteBuffer.allocate(4).putInt(i).array();
                    outPkt = new DatagramPacket(requestData, requestData.length, host, PORT);
                    socket.send(outPkt);
                }
                while (numPackets > 0) {
                    socket.receive(inPkt);
                    seqnumBytes = new byte[4];
                    System.arraycopy(inPkt.getData(), 0, seqnumBytes, 0, 4);
                    int seqnum = ByteBuffer.wrap(seqnumBytes).getInt();
                    ackSeqnum[seqnum] = true;
                    result = new String(inPkt.getData(), 4, inPkt.getLength() - 4, "US-ASCII");
                    numPackets--;
                    System.out.println(
                            "No. " + seqnum + "   " + numPackets + " packets left");
                    System.out.println(result);
                }

            } catch (SocketTimeoutException ex) {
                socket.close();
                System.err.println("No connection within 1 seconds");
                for (int i = 1; i <= numPackets; i++) {
                    if (!ackSeqnum[i]) {
                        System.out.println("packet No. " + i + " missing");
                    }
                }

            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

}
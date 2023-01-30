import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class MyUDPClient {

    private final static int PORT = 7707;
    // private static final String HOSTNAME = "localhost";
    private static int numPackets = 0;
    private static final byte[] ipAddr = new byte[] { 20, 106, 101, (byte) 156 };
    private final static int bufferSize = 20;
    private static Map<Integer, byte[]> receivedChunks = new HashMap<>();

    public static void main(String[] args) throws IOException {

        try (DatagramSocket socket = new DatagramSocket(0)) {
            // InetAddress host = InetAddress.getByName(HOSTNAME);
            InetAddress host = InetAddress.getByAddress(ipAddr);
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

                // send request for each chunk
                for (int i = 0; i < numPackets; i++) {
                    requestData = new byte[bufferSize];
                    seqnumBytes = ByteBuffer.allocate(4).putInt(i).array();
                    System.arraycopy(seqnumBytes, 0, requestData, 0, 4);
                    outPkt = new DatagramPacket(requestData, requestData.length, host, PORT);
                    socket.send(outPkt);
                }

                while (receivedChunks.size() < numPackets) {
                    int retries = 3;
                    while (retries > 0) {
                        try {
                            socket.receive(inPkt);
                            socket.setSoTimeout(3000); // wait for response for 3 seconds
                            seqnumBytes = new byte[4];
                            System.arraycopy(inPkt.getData(), 0, seqnumBytes, 0, 4);
                            int seqnum = ByteBuffer.wrap(seqnumBytes).getInt();
                            if (receivedChunks.containsKey(seqnum)) {
                                retries = 0; // break out of the loop
                            }
                            receivedChunks.put(seqnum, inPkt.getData());
                            retries = 0; // break out of loop
                            result = new String(inPkt.getData(), 4, inPkt.getLength() - 4, "US-ASCII");
                            // numPackets--;
                            System.out.println("No. " + seqnum + " received");
                            System.out.println(result);
                        } catch (SocketTimeoutException ex) {
                            retries--;
                            if (retries == 0) {
                                System.out.println(
                                        "Error: Could not receive response from server after multiple retries for some numbers");
                                break;
                            } else {
                                System.out.println(
                                        "Retransmitting requests for un-received numbers... Retries remaining: "
                                                + retries);
                                // resend requests for numbers that haven't been received
                                for (int i = 1; i <= numPackets; i++) {
                                    if (!receivedChunks.containsKey(i)) {
                                        requestData = new byte[bufferSize];
                                        seqnumBytes = ByteBuffer.allocate(4).putInt(i).array();
                                        System.arraycopy(seqnumBytes, 0, requestData, 0, 4);
                                        outPkt = new DatagramPacket(requestData, requestData.length, host, PORT);
                                        socket.send(outPkt);
                                    }
                                }
                            }
                        }

                    }
                }

            } catch (SocketTimeoutException ex) {
                socket.close();
                System.err.println("No connection within 1 seconds");

            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // write the received numbers to a file in order
        FileOutputStream fos = new FileOutputStream("received.txt");
        for (int i = 1; i <= numPackets; i++) {
            String result = new String(receivedChunks.get(i), 4, bufferSize - 4, "US-ASCII");
            System.out.println("No. " + i + " packet");
            System.out.println(result);
            fos.write(receivedChunks.get(i), 4, bufferSize - 4);
        }
        fos.close();

        System.out.println("FROM SERVER: " + receivedChunks.size() + " packets");
        System.out.println("Received bytes written to file: received.txt");

    }

}
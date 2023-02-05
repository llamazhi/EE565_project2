import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class UDPClient {
    private final static int bufferSize = 1024;
    private Map<Integer, byte[]> receivedChunks;
    private long requestFileSize;
    private long requestFileLastModified;
    private int numChunks;
    private int windowSize;
    private String requestFilename;

    public void setRequestFilename(String filename) {
        this.requestFilename = filename;
    }

    public long getRequestFileSize() {
        return this.requestFileSize;
    }

    public long getRequestFileLastModified() {
        return this.requestFileLastModified;
    }

    public int getNumChunks() {
        return this.numChunks;
    }

    public Map<Integer, byte[]> getReceivedChunks() {
        return this.receivedChunks;
    }

    public void startClient(String path, RemoteServerInfo info) {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            this.requestFilename = path;
            receivedChunks = new HashMap<>();

            // send request packet
            InetAddress host = InetAddress.getByName(info.hostname);
            byte[] seqNumBytes = new byte[4];
            byte[] requestData = new byte[bufferSize];
            seqNumBytes = ByteBuffer.allocate(4).putInt(0).array();
            System.arraycopy(seqNumBytes, 0, requestData, 0, 4);
            byte[] messageBytes = requestFilename.getBytes();
            System.arraycopy(messageBytes, 0, requestData, 4, messageBytes.length);
            DatagramPacket outPkt = new DatagramPacket(requestData, requestData.length, host, info.port);
            DatagramPacket inPkt = new DatagramPacket(new byte[bufferSize], bufferSize);
            socket.send(outPkt);

            try {
                // wait for first packet, and then process the packet...
                socket.setSoTimeout(1000); // wait for response for 1 seconds
                socket.receive(inPkt);
                String result = new String(inPkt.getData(), 0, inPkt.getLength(), "US-ASCII").trim();
                System.out.println("result: " + result);
                if (result.contains("FileNotExistsError")) {
                    return;
                }
                String[] responseValues = result.split(" ");
                this.requestFileSize = Long.parseLong(responseValues[0]);
                this.requestFileLastModified = Long.parseLong(responseValues[1]);
                this.numChunks = Integer.parseInt(responseValues[2]);
                this.windowSize = Integer.parseInt(responseValues[3]);
                System.out.println("numChunks: " + numChunks + " windowSize: " + windowSize);

                // slide window until the rightmost end hits the end of chunks
                // another condition to start receiving files is numChunks is even smaller
                // than windowSize
                int windowStart = 1;
                int windowEnd = windowSize;
                System.out.println("windowStart: " + windowStart + " , windowEnd: " + windowEnd);
                // send request for each chunk
                for (int i = 1; i <= numChunks; i++) {
                    byte[] seqnumBytes = ByteBuffer.allocate(4).putInt(i).array();
                    System.arraycopy(seqnumBytes, 0, requestData, 0, 4);
                    outPkt = new DatagramPacket(requestData, requestData.length, host, info.port);
                    socket.send(outPkt);
                }

                while (receivedChunks.size() < numChunks) {
                    VodServer.setCompleteness(100.0 * receivedChunks.size() / numChunks);
                    System.out.printf("%.2f", 100.0 * receivedChunks.size() / numChunks);
                    System.out.println(" % complete");
                    int retries = 3;
                    while (retries > 0) {
                        try {
                            socket.setSoTimeout(1000); // wait for response for 1 seconds
                            socket.receive(inPkt);
                            byte[] seqnumBytes = new byte[4];
                            System.arraycopy(inPkt.getData(), 0, seqnumBytes, 0, 4);
                            int seqnum = ByteBuffer.wrap(seqnumBytes).getInt();
                            if (receivedChunks.containsKey(seqnum)) {
                                break;
                            }
                            byte[] chunk = new byte[bufferSize];
                            System.arraycopy(inPkt.getData(), 0, chunk, 0, bufferSize);
                            receivedChunks.put(seqnum, chunk);
                            retries = 0; // break out of loop
                            // result = new String(inPkt.getData(), 4, inPkt.getLength() - 4, "US-ASCII");
                            // System.out.println("No. " + seqnum + " received");
                            // System.out.println(result);
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
                                for (int i = 1; i <= numChunks; i++) {
                                    if (!receivedChunks.containsKey(i)) {
                                        requestData = new byte[bufferSize];
                                        byte[] seqnumBytes = ByteBuffer.allocate(4).putInt(i).array();
                                        System.arraycopy(seqnumBytes, 0, requestData, 0, 4);
                                        outPkt = new DatagramPacket(requestData, requestData.length, host,
                                                info.port);
                                        socket.send(outPkt);
                                    }
                                }
                            }
                        }
                    }
                }

                VodServer.setCompleteness(100.0 * receivedChunks.size() / numChunks);
                System.out.printf("%.2f", 100.0 * receivedChunks.size() / numChunks);
                System.out.println(" % complete");

            } catch (SocketTimeoutException ex) {
                socket.close();
                System.err.println("No connection within 1 seconds");
            }

            System.out.printf("%.2f", 100.0 * receivedChunks.size() / numChunks);
            System.out.println(" % complete");
            System.out.println("FROM SERVER: " + receivedChunks.size() + " packets");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
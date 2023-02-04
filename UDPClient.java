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
    private int remoteServerPort;
    private String remoteServerHostname;
    private int trafficRate = 8096;
    private HashMap<Integer, List<Date>> dateMap;

    public void setRemoteServerPort(int port) {
        this.remoteServerPort = port;
    }

    public void setRemoteServerHostname(String hostname) {
        this.remoteServerHostname = hostname;
    }

    public void setRequestFilename(String filename) {
        this.requestFilename = filename;
    }

    public void setTrafficRate(int rate) {
        this.trafficRate = rate;
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

    public int getTrafficRate() {
        int i = 2; // the first meaningful seqNum
        if (this.dateMap != null && this.dateMap.size() > 0 && this.dateMap.get(i) != null) {
            List<Date> sendReceiveTime = this.dateMap.get(i);
            long sumTime = 0;
            for (int j = 0; j < this.dateMap.size(); j++) {
                long sentTime = sendReceiveTime.get(0).getTime();
                long receivedTime = sendReceiveTime.get(1).getTime();
                sumTime += receivedTime - sentTime;
            }
            // average RTT time for a packet, a packet is set to be 1024 bytes
            long avgTime = sumTime / this.dateMap.size(); // unit in millisec
            this.trafficRate = 1 / (int) (avgTime / 1000) * 1024;
            return this.trafficRate;
        } else {
            return this.trafficRate;
        }
    }

    // calculate how many milisecs the socket needs to stop
    // to get configureation rate
    public int getWaitTime(int rate) {
        // convert trafficRate to packet / sec
        int prevTrafficRate = getTrafficRate();
        if (prevTrafficRate > rate) { // unit in byte / sec
            // assume a 1 sec interval
            // prevTraffic rate is greater than the rate by 'coeff' times
            double coeff = prevTrafficRate / rate;
            int waitTime = (int) ((coeff - 1) * 1000);
            return waitTime;
        }
        return 0;
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
                socket.receive(inPkt);
                String result = new String(inPkt.getData(), 0, inPkt.getLength(), "US-ASCII").trim();
                if (result.equals("FileNotExistsError")) {
                    // TODO: notify http server to send 404 error
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
                this.dateMap = new HashMap<>();

                while (windowEnd <= numChunks || numChunks <= windowSize) {
                    if (this.receivedChunks.size() == this.numChunks) {
                        break;
                    }
                    System.out.println("Current windowEnd: " + windowEnd);

                    // send request for each chunk within the window
                    for (int i = windowStart; i <= windowEnd; i++) {
                        // requestData = new byte[bufferSize];
                        seqNumBytes = ByteBuffer.allocate(4).putInt(i).array();
                        System.arraycopy(seqNumBytes, 0, requestData, 0, 4);
                        outPkt = new DatagramPacket(requestData, requestData.length, host, info.port);
                        socket.send(outPkt);

                        // configure rate before send request every time
                        // int waitTime = getWaitTime(this.trafficRate);
                        // socket.setSoTimeout(waitTime);

                        // record the sent and received time of a packet
                        // int seqNum = ByteBuffer.wrap(seqNumBytes).getInt();
                        // List<Date> sendReceiveTime = new ArrayList<Date>();
                        // sendReceiveTime.add(new Date());
                        // dateMap.put(seqNum, sendReceiveTime);
                    }

                    System.out.println("This round of packets have been requested");

                    // attempt to receive packet within the window
                    for (int i = windowStart; i <= windowEnd; i++) {
                        // System.out.println("Test if pre-receive works: " + i);
                        if (this.receivedChunks.size() == this.numChunks) {
                            break;
                        }
                        socket.receive(inPkt);

                        // List<Date> sendReceiveTime = dateMap.get(i);
                        // sendReceiveTime.add(new Date());
                        // dateMap.put(i, sendReceiveTime); // update the map by adding receive time

                        seqNumBytes = new byte[4];
                        System.arraycopy(inPkt.getData(), 0, seqNumBytes, 0, 4);
                        int seqNum = ByteBuffer.wrap(seqNumBytes).getInt();

                        // move to the next iteration if the current chunk has been received
                        // else receive the packet
                        if (receivedChunks.containsKey(seqNum)) {
                            continue;
                        } else {
                            byte[] chunk = new byte[bufferSize];
                            System.arraycopy(inPkt.getData(), 0, chunk, 0, bufferSize);
                            receivedChunks.put(seqNum, chunk);
                        }
                    }

                    System.out.println("This round of packets have been received");

                    // move the window by 1 packet if the leftmost packet has been received
                    if (receivedChunks.containsKey(windowStart)) {
                        System.out.println("Receive the leftmost packet, time to slide window");
                        windowStart += 1;
                        windowEnd = windowStart + windowSize - 1;
                    }

                    System.out.printf("%.2f", 100.0 * receivedChunks.size() / numChunks);
                    System.out.println(" % complete");
                }

            } catch (SocketTimeoutException ex) {
                socket.close();
                System.err.println("No connection within 1 seconds");
            } finally {
                System.out.printf("%.2f", 100.0 * receivedChunks.size() / numChunks);
                System.out.println(" % complete");
                System.out.println("FROM SERVER: " + receivedChunks.size() + " packets");
                System.out.println("Received bytes written to file: received_" + requestFilename);
                // this.mode = "SERVER";
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
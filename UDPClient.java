import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class UDPClient {
    private final static int bufferSize = 1024;
    private Map<Integer, byte[]> receivedChunks;
    private long requestFileSize;
    private long requestFileLastModified;
    private int numChunks;
    private int windowSize;
    private String requestFilename;
    private final String CRLF = "\r\n";

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

    private String getDateInfo() {
        // produce day of the week
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
        return formatter.format(cal.getTime());
    }

    public static void intToByteArray(int value, byte[] buffer) {
        buffer[0] = (byte) (value >>> 24);
        buffer[1] = (byte) (value >>> 16);
        buffer[2] = (byte) (value >>> 8);
        buffer[3] = (byte) value;
    };

    public static int byteArrayToInt(byte[] bytes) {
        return (bytes[0] << 24) & 0xff000000 |
                (bytes[1] << 16) & 0x00ff0000 |
                (bytes[2] << 8) & 0x0000ff00 |
                (bytes[3] & 0xff);
    }

    public void startClient(String path, RemoteServerInfo info, DataOutputStream outputStream) {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            this.requestFilename = path;
            receivedChunks = new HashMap<>();

            // send request packet
            InetAddress host = InetAddress.getByName(info.hostname);
            byte[] seqNumBytes = new byte[4];
            byte[] requestData = new byte[bufferSize];
            byte[] receiveData = new byte[bufferSize];
            seqNumBytes = ByteBuffer.allocate(4).putInt(0).array();
            System.arraycopy(seqNumBytes, 0, requestData, 0, 4);
            byte[] messageBytes = requestFilename.getBytes();
            System.arraycopy(messageBytes, 0, requestData, 4, messageBytes.length);
            DatagramPacket outPkt = new DatagramPacket(requestData, requestData.length, host, info.port);
            DatagramPacket inPkt = new DatagramPacket(receiveData, receiveData.length);
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

                String date = getDateInfo();
                DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
                String MIMEType = URLConnection.getFileNameMap().getContentTypeFor(path);
                String response = "HTTP/1.1 200 OK" + this.CRLF +
                        "Content-Type: " + MIMEType + this.CRLF +
                        "Content-Length: " + this.getRequestFileSize() + this.CRLF +
                        "Date: " + date + " GMT" + this.CRLF +
                        "Last-Modified: " + formatter.format(getRequestFileLastModified()) + " GMT"
                        + this.CRLF +
                        "Connection: close" + this.CRLF +
                        this.CRLF;
                outputStream.writeBytes(response);

                // slide window until the rightmost end hits the end of chunks
                // another condition to start receiving files is numChunks is even smaller
                // than windowSize
                int windowStart = 1;
                int windowEnd = Math.min(windowSize, this.numChunks);
                System.out.println("windowStart: " + windowStart + " , windowEnd: " + windowEnd);
                byte[][] buffer = new byte[windowSize][bufferSize];
                // long startTime = System.currentTimeMillis();
                // double bitsSent = 0.0;
                // double sleepTime = 0.0;
                Set<Integer> seen = new HashSet<Integer>();

                while (windowStart <= windowEnd && windowEnd <= numChunks) {
                    boolean windowFull = true;

                    // send request for unreceived chunk within the window
                    for (int i = windowStart; i <= windowEnd; i++) {
                        if (!seen.contains(i)) {
                            intToByteArray(i, requestData);
                            outPkt = new DatagramPacket(requestData, requestData.length, host, info.port);
                            socket.send(outPkt);
                        }
                    }

                    // receive packet within the window
                    while (true) {
                        try {
                            socket.setSoTimeout(100);
                            socket.receive(inPkt);
                            int seqNum = byteArrayToInt(inPkt.getData());
                            if (seen.contains(seqNum)) {
                                continue;
                            } else {
                                buffer[seqNum - windowStart] = inPkt.getData();
                                seen.add(seqNum);
                            }
                        } catch (SocketTimeoutException ex) {
                            break;
                        }
                    }
                    for (int i = windowStart; i <= windowEnd; i++) {
                        windowFull &= seen.contains(i);
                    }

                    if (!windowFull) {
                        continue;
                    }

                    for (int i = 0; i <= windowEnd - windowStart; i++) {
                        windowFull &= seen.contains(i);
                        outputStream.write(buffer[i], 4, bufferSize - 4);
                    }
                    windowStart = windowEnd + 1;
                    windowEnd = Math.min(windowStart + windowSize - 1, numChunks);
                    System.out.printf("%.2f", 100.0 * seen.size() / numChunks);
                    System.out.println(" % complete");
                }

                VodServer.setCompleteness(100.0 * seen.size() / numChunks);
                System.out.printf("%.2f", 100.0 * seen.size() / numChunks);
                System.out.println(" % complete");

            } catch (SocketTimeoutException ex) {
                socket.close();
                System.err.println("No connection within 1 seconds");
            }

            // System.out.printf("%.2f", 100.0 * seen.size() / numChunks);
            // System.out.println(" % complete");
            // System.out.println("FROM SERVER: " + seen.size() + " packets");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.logging.*;
import java.lang.Thread;

public class UDPServer extends Thread {
    private final static Logger audit = Logger.getLogger("requests");

    private int port;
    private static int numChunks;
    private final static int bufferSize = 8192;
    // TODO: map<filename, map<seqnum, chunks>>
    private Map<Integer, byte[]> fileChunks;
    private static int bitSent = 0;
    private static long startTime;

    private final static int MAX_WINDOW_SIZE = 100;
    // private static int windowStart = 1;
    // private static int windowEnd = MAX_WINDOW_SIZE;

    public UDPServer(int port) {
        this.port = port;
        this.fileChunks = new HashMap<>();

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

    // TODO: use thread? channel? handle multiple client?
    // TODO: limit sending rate at server side using the config in add peer
    // TODO: use the config in peer/add?rate=xxx to limit the sending rate
    // TODO: client specify the sending rate when sending the request

    // time: 0 timer start
    // long bitSent;
    // for every outPkt, bitSent += outPkt.length * 8;
    // if bitSent > rate: sleep until time = 1
    // time = 1, bitSent = 0;
    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(this.port)) {
            System.out.println("UDP Server listening at: " + this.port);

            while (true) {
                DatagramPacket inPkt = new DatagramPacket(new byte[bufferSize], bufferSize);
                socket.receive(inPkt);
                handleInPacket(inPkt, socket);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void handleInPacket(DatagramPacket inPkt, DatagramSocket socket) throws IOException {
        int seqNum = byteArrayToInt(inPkt.getData());
        String requestString = new String(inPkt.getData(), 4, inPkt.getLength() - 4).trim();
        int bitRate = 0;

        if (seqNum == 0 && !requestString.isEmpty()) {
            // request for a file
            audit.info("Client request for file " + requestString);
            String[] requestValues = requestString.split(" ");
            // TODO: requestString will also have sending rate
            String path = requestValues[0];
            bitRate = Integer.parseInt(requestValues[1]);
            VodServer.setBitRate(bitRate); // unit in bit/sec
            startTime = System.currentTimeMillis();

            // TODO: if reveive new file request, add an entry to the fileChunks map
            File requestFile = new File(path);
            if (!requestFile.exists()) {
                byte[] data = ("FileNotExistsError").getBytes(Charset.forName("US-ASCII"));
                DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(), inPkt.getPort());
                socket.send(outPkt);
                return;
            }

            long fileSize = requestFile.length();
            long lastModified = requestFile.lastModified();
            numChunks = (int) Math.ceil((double) fileSize / (bufferSize - 4));
            audit.info("current file size: " + fileSize);
            audit.info("total number of chunks: " + numChunks);
            byte[] data = (fileSize + " " + lastModified + " " + numChunks + " " + MAX_WINDOW_SIZE)
                    .getBytes(Charset.forName("US-ASCII"));

            audit.info("Begin to send file ... ");

            // Read all the file into chunks
            int chunkIndex = 1;
            byte[] chunk = new byte[bufferSize];
            FileInputStream fis = new FileInputStream(requestFile);
            while (fis.read(chunk, 4, bufferSize - 4) > 0) {
                intToByteArray(chunkIndex, chunk);
                this.fileChunks.put(chunkIndex, chunk);
                chunkIndex++;
                chunk = new byte[bufferSize];
            }
            fis.close();

            // send response packet
            DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(), inPkt.getPort());
            socket.send(outPkt);
            audit.info(numChunks + " chunks in total");

        } else if (seqNum > 0 && seqNum <= numChunks) {
            // request for specific chunk of data
            // audit.info("Client request chunk packet at seqNum: " + seqNum);

            // check if seqNum is within window
            // release the buffer if the leftmost packet has been received by client
            // TODO: map<filename, map<seqnum, chunks>>
            // TODO: sleep to limit the sending rate

            DatagramPacket outPkt = new DatagramPacket(this.fileChunks.get(seqNum),
                    this.fileChunks.get(seqNum).length,
                    inPkt.getAddress(),
                    inPkt.getPort());

            bitSent += outPkt.getLength() * 8;
            // System.out.println("bitSent: " + bitSent);
            bitRate = (VodServer.getBitRate()); // unit in bit/sec
            // System.out.println("bitRate: " + bitRate);
            long elapsedTime = System.currentTimeMillis() - startTime;
            // System.out.println("elapsedTime: " + elapsedTime);
            if (bitRate != 0 && bitSent > bitRate && elapsedTime <= 1000) {
                try {
                    System.out.println("elapsed time: " + elapsedTime);
                    System.out.println("Need sleep for " + (1000 - elapsedTime) + " ms");
                    Thread.sleep(1000 - elapsedTime);
                    bitSent = 0;
                    startTime = System.currentTimeMillis(); // update startTime
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            socket.send(outPkt);
        }
    }
}

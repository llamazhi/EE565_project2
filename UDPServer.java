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
    private HashMap<SocketAddress, String> clientInfos;
    private HashMap<String, Integer> pathToBitRate;
    private HashMap<String, HashMap<Integer, byte[]>> fileChunks;
    private static int bitSent = 0;
    private final static int MAX_WINDOW_SIZE = 100;

    public UDPServer(int port) {
        this.port = port;
        this.fileChunks = new HashMap<>();
        this.clientInfos = new HashMap<>();
        this.pathToBitRate = new HashMap<>();
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
            String[] requestValues = requestString.split(" ");
            String path = requestValues[0];
            audit.info("Client request for file " + path);
            bitRate = Integer.parseInt(requestValues[1]);

            File requestFile = new File(path);
            if (!requestFile.exists()) {
                byte[] data = ("FileNotExistsError").getBytes(Charset.forName("US-ASCII"));
                DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(), inPkt.getPort());
                socket.send(outPkt);
                return;
            }

            this.pathToBitRate.put(path, bitRate); // unit in bit/sec
            this.clientInfos.put(inPkt.getSocketAddress(), path);

            long fileSize = requestFile.length();
            long lastModified = requestFile.lastModified();
            numChunks = (int) Math.ceil((double) fileSize / (bufferSize - 4));
            audit.info("current file size: " + fileSize);
            audit.info("total number of chunks: " + numChunks);
            byte[] data = (fileSize + " " + lastModified + " " + numChunks + " " + MAX_WINDOW_SIZE)
                    .getBytes(Charset.forName("US-ASCII"));

            audit.info("Begin to send file ... ");

            if (!this.fileChunks.containsKey(path)) {
                audit.info("Read new file into memory" + path);

                this.fileChunks.put(path, new HashMap<>());
                // Read all the file into chunks
                int chunkIndex = 1;
                byte[] chunk = new byte[bufferSize];
                FileInputStream fis = new FileInputStream(requestFile);
                while (fis.read(chunk, 4, bufferSize - 4) > 0) {
                    intToByteArray(chunkIndex, chunk);
                    this.fileChunks.get(path).put(chunkIndex, chunk);
                    chunkIndex++;
                    chunk = new byte[bufferSize];
                }
                fis.close();

            }
            // send response packet
            DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(), inPkt.getPort());
            socket.send(outPkt);
            audit.info(numChunks + " chunks in total");

        } else if (seqNum > 0 && seqNum <= numChunks) {
            // request for specific chunk of data
            String path = this.clientInfos.get(inPkt.getSocketAddress());
            byte[] sendingChunk = this.fileChunks.get(path).get(seqNum);
            DatagramPacket outPkt = new DatagramPacket(
                    sendingChunk,
                    sendingChunk.length,
                    inPkt.getAddress(),
                    inPkt.getPort());

            socket.send(outPkt);

            UDPServer.bitSent += outPkt.getLength() * 8;
            bitRate = this.pathToBitRate.get(path); // unit in kbps

            if (bitRate != 0 && UDPServer.bitSent > bitRate * 1000) {
                try {
                    double sleepTime = (UDPServer.bitSent / bitRate) - 1000;
                    System.out.println("Need sleep for " + sleepTime + " ms");
                    Thread.sleep((long) sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                UDPServer.bitSent = 0;
            }
        }
    }
}

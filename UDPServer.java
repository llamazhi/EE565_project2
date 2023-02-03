import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.logging.*;

public class UDPServer {
    private final static Logger audit = Logger.getLogger("requests");
    private final static Logger errors = Logger.getLogger("errors");

    private int port;
    private boolean isActive;

    private String requestFilename;
    private static int numChunks;
    private final static int bufferSize = 1024;
    private static Map<Integer, byte[]> fileChunks = new HashMap<>();

    private final static int MAX_WINDOW_SIZE = 10;
    private static int windowStart = 1;
    private static int windowEnd = MAX_WINDOW_SIZE;

    public UDPServer(int port, boolean isActive) {
        this.port = port;
        this.isActive = isActive;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void startServer() {
        try (DatagramSocket socket = new DatagramSocket(this.port)) {
            System.out.println("UDP Server listening at: " + this.port);

            // keep listening if the mode is SERVER
            while (this.isActive) {
                try {
                    DatagramPacket inPkt = new DatagramPacket(new byte[bufferSize], bufferSize);
                    socket.setSoTimeout(1000); // listening for response for 1000 ms
                    socket.receive(inPkt);
                    handleInPacket(inPkt, socket);
                } catch (SocketTimeoutException ex) {
                    System.out.println("No connection within 1000 ms");
                } catch (IOException | RuntimeException ex) {
                    errors.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void handleInPacket(DatagramPacket inPkt, DatagramSocket socket) throws IOException {
        byte[] seqNumBytes = new byte[4];
        System.arraycopy(inPkt.getData(), 0, seqNumBytes, 0, 4);
        int seqNum = ByteBuffer.wrap(seqNumBytes).getInt();
        String requestString = new String(inPkt.getData(), 4, inPkt.getLength() - 4).trim();

        if (seqNum == 0 && !requestString.isEmpty()) {
            // request for a file
            audit.info("Client request for file " + requestString);
            fileChunks = new HashMap<>();
            String path = "content/" + requestString;
            File requestFile = new File(path);
            if (!requestFile.exists()) {
                byte[] data = ("FileNotExistsError").getBytes(Charset.forName("US-ASCII"));
                DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(), inPkt.getPort());
                socket.send(outPkt);
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
                byte[] chunkNumberByte = ByteBuffer.allocate(4).putInt(chunkIndex).array();
                System.arraycopy(chunkNumberByte, 0, chunk, 0, 4);
                fileChunks.put(chunkIndex, chunk);
                chunkIndex++;
                chunk = new byte[bufferSize];
            }
            fis.close();

            // send response packet
            // DatagramPacket outPkt = new DatagramPacket(data, data.length,
            // inPkt.getAddress(),
            // inPkt.getPort());
            DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(), inPkt.getPort());
            socket.send(outPkt);
            audit.info(numChunks + " chunks in total");

        } else if (seqNum > 0 && seqNum <= numChunks) {
            // request for specific chunk of data
            audit.info("Client request chunk packet at seqNum: " + seqNum);

            // check if seqNum is within window
            // release the buffer if the leftmost packet has been received by client
            if (seqNum > windowEnd) {
                fileChunks.remove(windowStart);
            }
            DatagramPacket outPkt = new DatagramPacket(fileChunks.get(seqNum),
                    fileChunks.get(seqNum).length,
                    inPkt.getAddress(),
                    inPkt.getPort());
            socket.send(outPkt);
        }
    }
}

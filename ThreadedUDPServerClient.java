import java.nio.charset.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.logging.*;
import java.lang.Thread;

public class ThreadedUDPServerClient extends Thread {
    private final static Logger audit = Logger.getLogger("requests");
    private final static Logger errors = Logger.getLogger("errors");

    private String mode;

    private int PORT;
    // private static final String HOSTNAME = "localhost";
    private static final byte[] ipAddr = new byte[] { 20, 106, 101, (byte) 156 };

    private String requestFilename;
    private static int totalChunkSize = 0;
    private final static int bufferSize = 1024;
    private static Map<Integer, byte[]> fileChunks = new HashMap<>();
    private static Map<Integer, byte[]> receivedChunks = new HashMap<>();
    private final static int MAX_WINDOW_SIZE = 10;
    private static int windowStart = 0;
    private static int windowEnd = windowStart + MAX_WINDOW_SIZE - 1;

    public ThreadedUDPServerClient(String filename, int port, String mode) {
        this.PORT = port;
        this.requestFilename = filename;
        changeMode(mode);
    }

    public void changeMode(String mode) {
        this.mode = mode;
    }

    private void handleInPacket(DatagramPacket inPkt, DatagramSocket socket) throws IOException {
        byte[] seqNumBytes = new byte[4];
        System.arraycopy(inPkt.getData(), 0, seqNumBytes, 0, 4);
        int seqNum = ByteBuffer.wrap(seqNumBytes).getInt();
        String requestString = new String(inPkt.getData(), 4, inPkt.getLength() - 4).trim();

        if (seqNum == 0 && !requestString.isEmpty()) {
            // request for a file
            audit.info("Client request for file " + requestString);
            FileInputStream fis = new FileInputStream("Content/" + requestString);
            // int windowSize = 10;
            totalChunkSize = (int) Math.ceil((double) fis.getChannel().size() / (bufferSize - 4));
            byte[] data = (totalChunkSize + " " + MAX_WINDOW_SIZE).getBytes(Charset.forName("US-ASCII"));

            audit.info("Begin to send file ... ");

            // Read all the file into chunks
            int chunkNumber = 1;
            byte[] chunk = new byte[bufferSize];
            while (fis.read(chunk, 4, bufferSize - 4) > 0) {
                byte[] chunkNumberByte = ByteBuffer.allocate(4).putInt(chunkNumber).array();
                System.arraycopy(chunkNumberByte, 0, chunk, 0, 4);
                fileChunks.put(chunkNumber, chunk);
                chunkNumber++;
                chunk = new byte[bufferSize];
            }
            fis.close();

            // send response packet
            DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(),
                    inPkt.getPort());
            socket.send(outPkt);
            audit.info(totalChunkSize + " chunks in total");

        } else if (seqNum > 0 && seqNum <= totalChunkSize) {
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

    private void startServer() {
        try (DatagramSocket socket = new DatagramSocket(this.PORT)) {
            // keep listening if the mode is SERVER
            while (mode.equals("SERVER")) {
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

    private void startClient() {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            // send request packet
            // InetAddress host = InetAddress.getByName(HOSTNAME);
            InetAddress host = InetAddress.getByAddress(ipAddr);
            byte[] seqNumBytes = new byte[4];
            byte[] requestData = new byte[bufferSize];
            seqNumBytes = ByteBuffer.allocate(4).putInt(0).array();
            System.arraycopy(seqNumBytes, 0, requestData, 0, 4);
            byte[] messageBytes = requestFilename.getBytes();
            System.arraycopy(messageBytes, 0, requestData, 4, messageBytes.length);
            DatagramPacket outPkt = new DatagramPacket(requestData, requestData.length, host, PORT);
            DatagramPacket inPkt = new DatagramPacket(new byte[bufferSize], bufferSize);
            socket.send(outPkt);

            try {
                // wait for first packet, and then process the packet...
                socket.receive(inPkt);
                String result = new String(inPkt.getData(), 0, inPkt.getLength(), "US-ASCII");
                System.out.println(result);
                String[] responseValues = result.split(" ");
                totalChunkSize = Integer.parseInt(responseValues[0]);
                int windowSize = Integer.parseInt(responseValues[1]);
                System.out.println("numChunks: " + totalChunkSize + " windowSize: " + windowSize);
                System.out.println("windowStart: " + windowStart + " , windowEnd: " + windowEnd);

                // send request for each chunk within the window
                for (int i = windowStart; i < windowEnd; i++) {
                    // requestData = new byte[bufferSize];
                    seqNumBytes = ByteBuffer.allocate(4).putInt(i).array();
                    System.arraycopy(seqNumBytes, 0, requestData, 0, 4);
                    outPkt = new DatagramPacket(requestData, requestData.length, host, PORT);
                    socket.send(outPkt);
                }

                // slide window until the rightmost end hits the end of chunks
                while (windowEnd < totalChunkSize) {

                    // attempt to receive packet within the window
                    for (int i = windowStart; i < windowEnd; i++) {
                        socket.receive(inPkt);
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

                    System.out.printf("%.2f", 100.0 * receivedChunks.size() / totalChunkSize);
                    System.out.println(" % complete");
                }
                // move the window by 1 packet if the leftmost packet has been received
                if (receivedChunks.containsKey(windowStart)) {
                    windowStart += 1;
                    windowEnd = windowStart + windowSize - 1;
                }

                // while (receivedChunks.size() < totalChunkSize) {
                // System.out.printf("%.2f", 100.0 * receivedChunks.size() / totalChunkSize);
                // System.out.println(" % complete");
                // int retries = 3;
                // while (retries > 0) {
                // try {
                // socket.setSoTimeout(1000); // wait for response for 3 seconds
                // socket.receive(inPkt);
                // seqNumBytes = new byte[4];
                // System.arraycopy(inPkt.getData(), 0, seqNumBytes, 0, 4);
                // int seqnum = ByteBuffer.wrap(seqNumBytes).getInt();
                // if (receivedChunks.containsKey(seqnum)) {
                // break;
                // }
                // byte[] chunk = new byte[bufferSize];
                // System.arraycopy(inPkt.getData(), 0, chunk, 0, bufferSize);
                // receivedChunks.put(seqnum, chunk);
                // retries = 0; // break out of loop
                // // result = new String(inPkt.getData(), 4, inPkt.getLength() - 4,
                // "US-ASCII");
                // // System.out.println("No. " + seqnum + " received");
                // // System.out.println(result);
                // } catch (SocketTimeoutException ex) {
                // retries--;
                // if (retries == 0) {
                // System.out.println(
                // "Error: Could not receive response from server after multiple retries for
                // some numbers");
                // break;
                // } else {
                // System.out.println(
                // "Retransmitting requests for un-received numbers... Retries remaining: "
                // + retries);

                // // resend requests for numbers that haven't been received
                // for (int i = 1; i <= totalChunkSize; i++) {
                // if (!receivedChunks.containsKey(i)) {
                // requestData = new byte[bufferSize];
                // seqNumBytes = ByteBuffer.allocate(4).putInt(i).array();
                // System.arraycopy(seqNumBytes, 0, requestData, 0, 4);
                // outPkt = new DatagramPacket(requestData, requestData.length, host, PORT);
                // socket.send(outPkt);
                // }
                // }
                // }
                // }
                // }
                // }
            } catch (SocketTimeoutException ex) {
                socket.close();
                System.err.println("No connection within 1 seconds");
            } finally {
                System.out.printf("%.2f", 100.0 * receivedChunks.size() / totalChunkSize);
                System.out.println(" % complete");
                System.out.println("FROM SERVER: " + receivedChunks.size() + " packets");
                System.out.println("Received bytes written to file: received_" + requestFilename);
                this.mode = "SERVER";
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            if (this.mode.equals("SERVER")) {
                startServer();
            }
            // CLIENT mode
            startClient();
            this.mode = "SERVER";
        }

    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java udpclient file port mode");
            return;
        }
        String fileName = args[0];

        // set the port to listen on
        int port;
        try {
            port = Integer.parseInt(args[1]);
            if (port < 1024 || port > 65535)
                port = 8080;
        } catch (RuntimeException ex) {
            port = 80;
        }

        // set mode, default to SERVER
        String mode = args[2];
        ThreadedUDPServerClient server = new ThreadedUDPServerClient(fileName, port, mode);
        server.start();
        Scanner scanner = new Scanner(System.in);
        while (!mode.equals("EXIT")) {
            System.out.println("Enter mode (CLIENT/SERVER/EXIT):");
            mode = scanner.nextLine();
            server.changeMode(mode);
        }
        scanner.close();

    }
}
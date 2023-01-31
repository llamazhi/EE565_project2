import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.*;
import java.lang.Thread;

public class ThreadedUDPServerClient extends Thread {
    private String mode;
    private int PORT;
    // private static final String HOSTNAME = "localhost";
    private static int numPackets = 0;
    private static final byte[] ipAddr = new byte[] { 20, 106, 101, (byte) 156 };
    private final static int bufferSize = 1024;
    private static Map<Integer, byte[]> receivedChunks = new HashMap<>();
    private String requestFilename;
    private final static Logger audit = Logger.getLogger("requests");
    private final static Logger errors = Logger.getLogger("errors");
    private static Map<Integer, byte[]> chunks = new HashMap<>();

    public ThreadedUDPServerClient(String filename, int port, String mode) {
        this.PORT = port;
        this.requestFilename = filename;
        changeMode(mode);
    }

    public void changeMode(String mode) {
        this.mode = mode;
    }

    private void handleInPacket(DatagramPacket inPkt, DatagramSocket socket) throws IOException {
        byte[] seqnumBytes = new byte[4];
        System.arraycopy(inPkt.getData(), 0, seqnumBytes, 0, 4);
        int seqnum = ByteBuffer.wrap(seqnumBytes).getInt();
        String requestString = new String(inPkt.getData(), 4, inPkt.getLength() - 4).trim();

        if (!requestString.isEmpty()) {
            // request for a file
            audit.info(requestString);
            FileInputStream fis = new FileInputStream("Content/" + requestString);
            int windowSize = 10;
            numPackets = (int) Math.ceil((double) fis.getChannel().size() / (bufferSize - 4));
            byte[] data = (numPackets + " " + windowSize).getBytes();

            audit.info("Begin to send file ... ");
            // Here we break file into chunks
            int chunkNumber = 1;
            byte[] chunk = new byte[bufferSize];
            // int bytesRead;
            while (fis.read(chunk, 4, bufferSize - 4) > 0) {
                byte[] chunkNumberByte = ByteBuffer.allocate(4).putInt(chunkNumber).array();
                System.arraycopy(chunkNumberByte, 0, chunk, 0, 4);
                chunks.put(chunkNumber, chunk);
                chunkNumber++;
                chunk = new byte[bufferSize];
            }
            fis.close();
            DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(),
                    inPkt.getPort());
            socket.send(outPkt);
            audit.info(numPackets + " " + windowSize + " " + inPkt.getAddress());
        } else if (seqnum > 0 && seqnum <= numPackets) {
            // request for specific chunk of data
            audit.info("request seqnum: " + seqnum);
            DatagramPacket outPkt = new DatagramPacket(chunks.get(seqnum),
                    chunks.get(seqnum).length,
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
            System.out.println("hihihi");
            // send request packet
            // InetAddress host = InetAddress.getByName(HOSTNAME);
            InetAddress host = InetAddress.getByAddress(ipAddr);
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            buffer.putInt(0); // seqnum = 0
            buffer.put(requestFilename.getBytes());
            buffer.rewind();
            DatagramPacket outPkt = new DatagramPacket(buffer.array(), buffer.limit(), host, PORT);
            socket.send(outPkt);

            DatagramPacket inPkt = new DatagramPacket(buffer.array(), buffer.limit());
            try {
                System.out.println("hihihi");

                // wait for first packet, and then process the packet...
                socket.receive(inPkt);
                buffer.rewind();
                int seqnum = buffer.getInt();
                String message = "";
                while (buffer.hasRemaining()) {
                    message += buffer.getChar();
                }
                System.out.println(message);
                String[] responseValues = message.split(" ");
                numPackets = Integer.parseInt(responseValues[0]);
                int windowSize = Integer.parseInt(responseValues[1]);
                System.out.println("numPackets: " + numPackets + " windowSize: " + windowSize);

                // send request for each chunk
                for (int i = 1; i <= numPackets; i++) {
                    buffer.rewind();
                    buffer.putInt(i); // seqnum, start from 1
                    outPkt = new DatagramPacket(buffer.array(), buffer.limit(), host, PORT);
                    socket.send(outPkt);
                }

                while (receivedChunks.size() < numPackets) {
                    System.out.printf("%.2f", 100.0 * receivedChunks.size() / numPackets);
                    System.out.println(" % complete");
                    int retries = 3;
                    while (retries > 0) {
                        try {
                            socket.setSoTimeout(1000); // wait for response for 3 seconds
                            socket.receive(inPkt);
                            buffer.rewind();
                            seqnum = buffer.getInt();
                            if (receivedChunks.containsKey(seqnum)) {
                                break;
                            }
                            receivedChunks.put(seqnum, buffer.duplicate().array());
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
                                for (int i = 1; i <= numPackets; i++) {
                                    if (!receivedChunks.containsKey(i)) {
                                        buffer.rewind();
                                        buffer.putInt(i); // missing seqnum
                                        outPkt = new DatagramPacket(buffer.array(), buffer.limit(), host, PORT);
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
            } finally {
                System.out.printf("%.2f", 100.0 * receivedChunks.size() / numPackets);
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
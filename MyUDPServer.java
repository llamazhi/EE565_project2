import java.nio.ByteBuffer;
import java.net.*;
import java.util.Date;
import java.util.logging.*;
import java.io.*;
import java.util.*;

public class MyUDPServer {

    private final static int PORT = 7707;
    private final static Logger audit = Logger.getLogger("requests");
    private final static Logger errors = Logger.getLogger("errors");

    public static void main(String[] args) {

        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            Map<Integer, byte[]> chunks = new HashMap<>();
            while (true) {
                try {

                    DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
                    socket.receive(request);
                    int seqnum = ByteBuffer.wrap(request.getData()).getInt();
                    String requestString = new String(request.getData(), 0, request.getLength());
                    audit.info(requestString);

                    if (requestString.equals("get test.txt")) {
                        FileInputStream fis = new FileInputStream("Content/test.txt");
                        int windowSize = 10;
                        int bufferSize = 1024;
                        int numPackets = (int) Math.ceil((double) fis.getChannel().size() / bufferSize);
                        byte[] data = (numPackets + " " + windowSize).getBytes();

                        // System.out.println("Begin to send file ... ");

                        // Here we break file into chunks
                        int chunkNumber = 0;
                        byte[] chunk = new byte[bufferSize];
                        int bytesRead;
                        while ((bytesRead = fis.read(chunk, 4, 1020)) > 0) {
                            byte[] chunkNumberArray = ByteBuffer.allocate(4).putInt(chunkNumber).array();
                            System.arraycopy(chunkNumberArray, 0, chunk, 0, 4);
                            chunks.put(chunkNumber, chunk);
                            chunkNumber++;
                            chunk = new byte[bufferSize];
                        }
                        DatagramPacket response = new DatagramPacket(data, data.length, request.getAddress(),
                                request.getPort());
                        socket.send(response);
                        audit.info(numPackets + " " + windowSize + " " + request.getAddress());
                    } else if (seqnum >= 0 && seqnum < 10) {
                        audit.info("seqnum: " + seqnum);
                        DatagramPacket response = new DatagramPacket(chunks.get(seqnum),
                                chunks.get(seqnum).length,
                                request.getAddress(),
                                request.getPort());
                        socket.send(response);

                    }
                } catch (IOException | RuntimeException ex) {
                    errors.log(Level.SEVERE, ex.getMessage(), ex);

                }
            }
        } catch (IOException ex) {
            errors.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }

}
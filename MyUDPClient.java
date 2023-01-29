import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class MyUDPClient {

    private final static int PORT = 7707;
    private static final String HOSTNAME = "localhost";

    public static void main(String[] args) {

        try (DatagramSocket socket = new DatagramSocket(0)) {
            socket.setSoTimeout(1000);
            InetAddress host = InetAddress.getByName(HOSTNAME);
            byte[] requestData = "get test.txt".getBytes();
            DatagramPacket request = new DatagramPacket(requestData, requestData.length, host, PORT);
            DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
            socket.send(request);
            try {
                // process the packet...
                socket.receive(response);
                String result = new String(response.getData(), 0, response.getLength(), "US-ASCII");
                System.out.println(result);
                String[] responseValues = result.split(" ");
                int numPackets = Integer.parseInt(responseValues[0]);
                int windowSize = Integer.parseInt(responseValues[1]);
                System.out.println("numPackets: " + numPackets + " windowSize: " + windowSize);
                for (int i = 0; i < numPackets; i++) {
                    requestData = new byte[1024];
                    requestData = ByteBuffer.allocate(4).putInt(i).array();
                    request = new DatagramPacket(requestData, requestData.length, host, PORT);
                    socket.send(request);
                }
                while (numPackets > 0) {
                    socket.receive(response);
                    byte[] seqnumBytes = new byte[4];
                    System.arraycopy(response.getData(), 0, seqnumBytes, 0, 4);
                    result = new String(response.getData(), 4, response.getLength() - 4, "US-ASCII");
                    System.out.println(ByteBuffer.wrap(seqnumBytes).getInt());
                    System.out.println(result);
                    numPackets--;
                }

            } catch (SocketTimeoutException ex) {
                socket.close();
                System.err.println("No connection within 10 seconds");

            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

}
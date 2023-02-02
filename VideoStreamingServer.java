import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VideoStreamingServer {
    public static void main(String[] args) throws IOException {

        // Server
        int serverPort = 9999;
        DatagramSocket socket = new DatagramSocket(serverPort);

        // Receive request from client
        byte[] request = new byte[1024];
        DatagramPacket packet = new DatagramPacket(request, request.length);
        socket.receive(packet);

        // Send response to client with number of packets and window size
        int numPackets = 100;
        int windowSize = 10;
        byte[] response = (numPackets + " " + windowSize).getBytes();
        packet = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
        socket.send(packet);

        // Open the file
        FileInputStream fis = new FileInputStream("Content/test.mp4");

        // Set the initial window size and starting packet number
        int windowStart = 0;
        int windowEnd = windowSize - 1;

        // Keep sending packets until all packets have been sent
        while (windowEnd < numPackets) {
            // Send packets in the current window
            for (int i = windowStart; i <= windowEnd; i++) {
                // Read a packet from the file
                byte[] data = new byte[1024];
                int length = fis.read(data);

                // Add the packet number to the beginning of the packet
                byte[] packetData = new byte[length + 4];
                System.arraycopy(intToBytes(i), 0, packetData, 0, 4);
                System.arraycopy(data, 0, packetData, 4, length);

                // Send the packet
                packet = new DatagramPacket(packetData, packetData.length, packet.getAddress(), packet.getPort());
                socket.send(packet);
            }

            // Wait for acknowledgements
            for (int i = windowStart; i <= windowEnd; i++) {
                // Receive acknowledgement
                byte[] ackData = new byte[4];
                packet = new DatagramPacket(ackData, ackData.length);
                socket.receive(packet);

                // Extract the packet number from the acknowledgement
                int ack = bytesToInt(ackData);

                // If an acknowledgement is received for the next packet, move the window
                if (ack == windowStart) {
                    windowStart++;
                    windowEnd++;
                } else {
                    // If an acknowledgement is not received for the next packet, wait for a timeout
                    // before retransmitting
                    // ...
                }
            }
        }

        // Close the FileInputStream
        fis.close();

        // Close the DatagramSocket
        socket.close();
    }

    // Method to convert an int to a byte array
    public static byte[] intToBytes(int value) {
        return new byte[] {
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
    }

    // Method to convert a byte array to an int
    public static int bytesToInt(byte[] bytes) {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }
}
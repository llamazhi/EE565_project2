
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VideoStreamingClient {
    public static void main(String[] args) throws IOException {
        // Client
        int serverPort = 9999;
        InetAddress serverAddress = InetAddress.getByName("localhost");
        DatagramSocket socket = new DatagramSocket();

        // Send request to server
        byte[] request = "Content/test.mp4".getBytes();
        DatagramPacket packet = new DatagramPacket(request, request.length, serverAddress, serverPort);
        socket.send(packet);

        // Receive response from server with number of packets and window size
        byte[] response = new byte[1024];
        packet = new DatagramPacket(response, response.length);
        socket.receive(packet);
        String[] parts = new String(response, 0, packet.getLength()).split(" ");
        int numPackets = Integer.parseInt(parts[0]);
        int windowSize = Integer.parseInt(parts[1]);
        System.out.println("numPackets: " + numPackets + " windowSize: " + windowSize);

        // Open the file
        FileOutputStream fos = new FileOutputStream("video_copy.mp4");

        // Set the initial window size and starting packet number
        int windowStart = 0;
        int windowEnd = windowSize - 1;

        // Keep receiving packets until all packets have been received
        while (windowEnd < numPackets) {
            // Receive packets in the current window
            for (int i = windowStart; i <= windowEnd; i++) {
                // Receive packet
                byte[] data = new byte[1024];
                packet = new DatagramPacket(data, data.length);
                socket.receive(packet);

                // Extract the packet number from the packet
                int packetNum = bytesToInt(data);

                // Write the packet to the file
                fos.write(data, 4, packet.getLength() - 4);

                // Send acknowledgement for the packet
                byte[] ackData = intToBytes(packetNum);
                packet = new DatagramPacket(ackData, ackData.length, serverAddress, serverPort);
                socket.send(packet);
            }

            // If an acknowledgement is not received for the next packet, retransmit the
            // request
            // ...

            // Move the window
            windowStart = windowEnd + 1;
            windowEnd = windowStart + windowSize - 1;
        }

        // Close the FileOutputStream
        fos.close();

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
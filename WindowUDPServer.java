import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class WindowUDPServer {
    public final static int PORT = 9876;

    public static void main(String[] args) throws IOException {
        // Create a DatagramSocket to send packets
        DatagramSocket socket = new DatagramSocket();

        // Create a FileInputStream to read the video file
        FileInputStream fis = new FileInputStream("Content/test.mp4");

        // Set the buffer size for the packets
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // Create an array to store the sequence numbers of the packets
        int[] sequenceNumbers = new int[100000];

        // Create a variable to store the current sequence number
        int currentSequenceNumber = 0;

        // Create a variable to store the window size
        int windowSize = 10;

        // Create a variable to store the number of packets sent
        int packetsSent = 0;

        // Create a variable to store the number of packets acknowledged
        int packetsAcknowledged = 0;

        // Server
        // Receive request from client
        byte[] request = new byte[1024];
        DatagramPacket packet = new DatagramPacket(request, request.length);
        socket.receive(packet);

        // Send response to client with number of packets and window size
        int numPackets = (int) Math.ceil((double) fis.getChannel().size() / bufferSize);
        byte[] response = (numPackets + " " + windowSize).getBytes();
        packet = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
        socket.send(packet);

        // Send packets until the end of the file is reached
        while (fis.read(buffer) != -1) {
            // Add the sequence number to the buffer
            byte[] packetData = intToBytes(currentSequenceNumber);
            System.arraycopy(packetData, 0, buffer, 0, 4);
            sequenceNumbers[packetsSent] = currentSequenceNumber;
            // Create a DatagramPacket to send the video chunk
            packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("localhost"), PORT);

            // Send the packet
            socket.send(packet);
            packetsSent++;

            // Move the window
            if (packetsSent - packetsAcknowledged >= windowSize) {
                // Wait for an acknowledgement
                byte[] ackBuffer = new byte[4];
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                socket.receive(ackPacket);
                int ack = bytesToInt(ackPacket.getData());
                packetsAcknowledged = ack;
            }
            currentSequenceNumber++;
        }
        // Send an end of transmission packet
        byte[] endPacketData = intToBytes(-1);
        DatagramPacket endPacket = new DatagramPacket(endPacketData, endPacketData.length,
                InetAddress.getByName("localhost"), 9876);
        socket.send(endPacket);

        // Close the socket and the FileInputStream
        socket.close();
        fis.close();
    }

    // utility functions to convert int to byte[] and vice versa
    public static byte[] intToBytes(int value) {
        return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value };
    }

    public static int bytesToInt(byte[] bytes) {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }
}

import java.nio.ByteBuffer;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class WindowUDPClient {
    public final static int PORT = 9876;

    public static void main(String[] args) throws IOException {
        // Create a DatagramSocket to receive packets
        DatagramSocket socket = new DatagramSocket(PORT);

        // Create a FileOutputStream to write the video file
        FileOutputStream fos = new FileOutputStream("received_video.mp4");

        // Set the buffer size for the packets
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // Create a variable to store the current sequence number
        int currentSequenceNumber = 0;

        // // Create a variable to store the window size
        // int windowSize = 10;

        // Create a variable to store the number of packets received
        int packetsReceived = 0;

        // Create a variable to store the number of packets acknowledged
        int packetsAcknowledged = 0;

        // Client
        // Send request to server for video file
        byte[] request = "REQUEST".getBytes();
        DatagramPacket packet = new DatagramPacket(request, request.length, InetAddress.getByName("localhost"),
                PORT);
        socket.send(packet);

        // Receive response from server with number of packets and window size
        byte[] response = new byte[1024];
        packet = new DatagramPacket(response, response.length);
        socket.receive(packet);
        String responseString = new String(packet.getData());
        System.out.println(responseString);
        String[] responseValues = responseString.split(" ");
        int numPackets = Integer.parseInt(responseValues[0]);
        int windowSize = Integer.parseInt(responseValues[1]);
        System.out.println("numPackets: " + numPackets + " windowSize: " + windowSize);

        // Receive packets until the end of the transmission
        while (true) {
            // Create a DatagramPacket to receive the video chunk
            packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            // Get the sequence number of the packet
            int sequenceNumber = ByteBuffer.wrap(packet.getData()).getInt();
            System.out.println("sequenceNumber: " + sequenceNumber);

            // Check if the packet is within the window
            if (sequenceNumber >= currentSequenceNumber && sequenceNumber < currentSequenceNumber + windowSize) {
                // Write the packet to the file
                fos.write(packet.getData(), 4, packet.getLength() - 4);
                packetsReceived++;

                // Send an acknowledgement for the packet
                byte[] ackData = ByteBuffer.allocate(4).putInt(packetsReceived).array();
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, packet.getAddress(),
                        packet.getPort());
                socket.send(ackPacket);
            } else if (sequenceNumber == -1) {
                // End of transmission
                break;
            } else if (sequenceNumber < currentSequenceNumber) {
                // Send an acknowledgement for the last packet
                byte[] ackData = ByteBuffer.allocate(4).putInt(packetsReceived).array();
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, packet.getAddress(),
                        packet.getPort());
                socket.send(ackPacket);
            }

            // Check if all packets in the window have been received
            if (packetsReceived - packetsAcknowledged == windowSize) {
                // Move the window
                currentSequenceNumber += windowSize;
                packetsAcknowledged = packetsReceived;
            }
        }

        // Close the FileOutputStream
        fos.close();

        // Close the DatagramSocket
        socket.close();
    }
}

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class SeqNumUDPServer {
    public static void main(String[] args) {
        try {
            // Create a DatagramSocket to listen for incoming packets
            DatagramSocket socket = new DatagramSocket(9876);

            // Create a FileInputStream to read the video file
            FileInputStream fis = new FileInputStream("video.mp4");

            // Set the buffer size for the packets
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];

            // Create an array to store the sequence numbers
            int[] sequenceNumbers = new int[(int) Math.ceil((double) fis.getChannel().size() / bufferSize)];

            // Set the initial sequence number
            int sequenceNumber = 0;

            // Read the video file into the buffer and send it to the client
            for (int readBytes = fis.read(buffer); readBytes != -1; readBytes = fis.read(buffer)) {
                // Add the sequence number to the buffer
                byte[] data = new byte[readBytes + 4];
                System.arraycopy(buffer, 0, data, 4, readBytes);
                System.arraycopy(intToBytes(sequenceNumber), 0, data, 0, 4);

                // Create a DatagramPacket to send the video chunk
                InetAddress address = InetAddress.getByName("localhost");
                DatagramPacket packet = new DatagramPacket(data, data.length, address, 9876);

                // Send the packet to the client
                socket.send(packet);

                // Store the sequence number
                sequenceNumbers[sequenceNumber] = sequenceNumber;

                // Increment the sequence number
                sequenceNumber++;
            }

            // Close the socket and the FileInputStream
            socket.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to convert an int to a byte array
    private static byte[] intToBytes(int value) {
        return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value };
    }
}

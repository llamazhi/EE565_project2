
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class UDPServerClient {
   public static void main(String[] args) throws Exception {
      Scanner scanner = new Scanner(System.in);
      String mode = "";
      DatagramSocket socket = new DatagramSocket(7707);
      while (mode != "exit") {
         System.out.println("Enter mode (client/server/exit):");
         mode = scanner.nextLine();
         if (mode.equals("client")) {
            byte[] ipAddr = new byte[] { 20, 106, 101, (byte) 156 };
            InetAddress IPAddress = InetAddress.getByAddress(ipAddr);
            System.out.println("Starting UDP client");
            BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
               System.out.println("Enter a sentence to send to the server:");
               String sentence = inFromUser.readLine();

               byte[] sendData = sentence.getBytes();
               DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 7707);
               socket.send(sendPacket);
               if (sentence.equals("exit")) {
                  break;
               }
               byte[] receiveData = new byte[1024];
               DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
               socket.receive(receivePacket);
               String modifiedSentence = new String(receivePacket.getData());
               System.out.println("FROM SERVER:" + modifiedSentence);

            }
         } else if (mode.equals("server")) {
            System.out.println("Starting UDP server on port 7707");
            while (true) {
               byte[] receiveData = new byte[1024];
               DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
               socket.receive(receivePacket);
               String sentence = new String(receivePacket.getData()).trim();
               System.out.println("FROM CLIENT:" + sentence);

               if (sentence.equals("exit")) {
                  break;
               }
               InetAddress IPAddress = receivePacket.getAddress();
               int port = receivePacket.getPort();
               byte[] sendData = sentence.getBytes();
               DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
               socket.send(sendPacket);
            }
         }
      }
   }
}

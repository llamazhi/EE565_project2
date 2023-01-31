
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class UDPServerClient {
   public static void main(String[] args) throws Exception {
      Scanner scanner = new Scanner(System.in);
      String mode = "";
      while (!mode.equals("exit")) {
         System.out.println("Enter mode (client/server/exit):");
         mode = scanner.nextLine();
         if (mode.equals("client")) {
            DatagramSocket socket = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName("localhost");
            System.out.println("Starting UDP client");
            BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
               System.out.println("Enter a sentence to send to the server:");
               String sentence = inFromUser.readLine();
               if (sentence.equals("server")) {
                  mode = "server";
                  break;
               }
               if (sentence.equals("exit")) {
                  mode = "exit";
                  break;
               }
               byte[] sendData = sentence.getBytes();
               DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 7707);
               socket.send(sendPacket);
               byte[] receiveData = new byte[1024];
               DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
               socket.receive(receivePacket);
               String modifiedSentence = new String(receivePacket.getData());
               System.out.println("FROM SERVER:" + modifiedSentence);
            }
         } else if (mode.equals("server")) {
            DatagramSocket socket = new DatagramSocket(7707);
            System.out.println("Starting UDP server on port 7707");
            while (true) {
               byte[] receiveData = new byte[1024];
               DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
               socket.receive(receivePacket);
               String sentence = new String(receivePacket.getData());
               if (sentence.equals("client")) {
                  mode = "client";
                  break;
               }
               if (sentence.equals("exit")) {
                  mode = "exit";
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

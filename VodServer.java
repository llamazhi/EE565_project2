import java.io.*;
import java.net.*;

// This is the main driver class for the project
public class VodServer {

    public static void main(String[] args) {
        ServerSocket server = null;
        int httpPort = 8080;
        int udpPort = 8081;
        if (args.length != 2) {
            System.out.println("Usage: java VodServer http-port udp-port");
            return;
        }

        httpPort = Integer.parseInt(args[0]);
        udpPort = Integer.parseInt(args[1]);

        ThreadedUDPServerClient udpserver = new ThreadedUDPServerClient(udpPort);
        udpserver.start();

        try {
            server = new ServerSocket(httpPort);
            System.out.println("Server started, listening on: " + httpPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            while (true) {
                Socket client = server.accept();
                System.out.println("Connection accepted");

                ThreadedHTTPWorker workerThread = new ThreadedHTTPWorker(client, udpserver);
                workerThread.start();
                // System.out.println("New worker thread built");
            }
        } catch (IOException e) {
            System.out.println("Thread building issue");
            e.printStackTrace();
        }
    }
}
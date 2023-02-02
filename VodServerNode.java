import java.io.*;
import java.net.*;

public class VodServerNode {

    public VodServerNode() {

    }

    public void run(String[] args) {
        ServerSocket server = null;
        int port = 8080;
        if (args.length != 0) {
            port = Integer.parseInt(args[0]);
        }

        try {
            server = new ServerSocket(port);
            System.out.println("Server started, listening on: " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ThreadedUDPServerClient udpserver = new ThreadedUDPServerClient(port, "SERVER");
            udpserver.start();

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

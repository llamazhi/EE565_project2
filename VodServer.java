import java.io.*;
import java.net.*;
// This is the main driver class for the project
public class VodServer {

    public static void main(String[] args) {
        ServerSocket server = null;
        int port = 8080;
        if (args.length != 0) {
            port = Integer.parseInt(args[0]);
        }

        try {
            server = new ServerSocket(port);
            System.out.println("Server started, listening on: " + port);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        try {
            while(server.isBound() && !server.isClosed()) {
                Socket client = server.accept();
                System.out.println("Connection accepted");

                ThreadedHTTPWorker workerThread = new ThreadedHTTPWorker(client);
                workerThread.start();
                System.out.println("New worker thread built");
            }
        }
        catch (IOException e) {
            System.out.println("Thread building issue");
            e.printStackTrace();
        }
    }
}
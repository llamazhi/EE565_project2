import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// This is the main driver class for the project
public class VodServer {
    private static HashMap<String, ArrayList<RemoteServerInfo>> parameterMap;

    public static void addPeer(String filepath, RemoteServerInfo info) {
        if (!VodServer.parameterMap.containsKey(filepath)) {
            VodServer.parameterMap.put(filepath, new ArrayList<RemoteServerInfo>());
        }
        VodServer.parameterMap.get(filepath).add(info);
    }

    public static ArrayList<RemoteServerInfo> getRemoteServerInfo(String filepath) {
        return VodServer.parameterMap.get(filepath);
    }

    public static void main(String[] args) {
        VodServer.parameterMap = new HashMap<String, ArrayList<RemoteServerInfo>>();
        ServerSocket server = null;
        int httpPort;
        int udpPort;
        if (args.length != 2) {
            System.out.println("Usage: java VodServer http-port udp-port");
            return;
        }

        httpPort = Integer.parseInt(args[0]);
        udpPort = Integer.parseInt(args[1]);

        UDPServer udpserver = new UDPServer(udpPort);
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

                ThreadedHTTPWorker workerThread = new ThreadedHTTPWorker(client);
                workerThread.start();
                // System.out.println("New worker thread built");
            }
        } catch (IOException e) {
            System.out.println("Thread building issue");
            e.printStackTrace();
        }
    }
}
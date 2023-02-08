import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// This is the main driver class for the project
public class VodServer {
    public static HashMap<String, ArrayList<RemoteServerInfo>> parameterMap;
    public static ArrayList<Long> clientReceiveTimestamps;
    public static boolean bitRateChanged = false;
    public final static Integer bufferSize = 8192;
    private static Double completeness = 0.0;
    private static Double Currentkbps = 0.0;
    private static Integer bitRate = 0;

    public static void addPeer(String filepath, RemoteServerInfo info) {
        if (!VodServer.parameterMap.containsKey(filepath)) {
            VodServer.parameterMap.put(filepath, new ArrayList<RemoteServerInfo>());
        }
        VodServer.parameterMap.get(filepath).add(info);
        System.out.println(parameterMap);
    }

    public static void setCurrentkbps(double kbps) {
        VodServer.Currentkbps = kbps;
    }

    public static double getCurrentkbps() {
        return VodServer.Currentkbps;
    }

    public static void setCompleteness(double completeness) {
        VodServer.completeness = completeness;
    }

    public static double getCompleteness() {
        return VodServer.completeness;
    }

    public static void setBitRate(Integer bitRate) {
        VodServer.clientReceiveTimestamps = new ArrayList<>();
        VodServer.bitRate = bitRate; // kbps
        VodServer.bitRateChanged = true;
    }

    public static int getBitRate() {
        return VodServer.bitRate;
    }

    public static ArrayList<RemoteServerInfo> getRemoteServerInfo(String filepath) {
        return VodServer.parameterMap.get(filepath);
    }

    public static void main(String[] args) {
        VodServer.parameterMap = new HashMap<String, ArrayList<RemoteServerInfo>>();
        VodServer.clientReceiveTimestamps = new ArrayList<>();
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
            }
        } catch (IOException e) {
            System.out.println("Thread building issue");
            e.printStackTrace();
        }
    }
}
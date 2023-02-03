import java.lang.Thread;

public class ThreadedUDPServerClient extends Thread {
    private String mode;
    private UDPServer udpserver;
    private UDPClient udpclient;
    private String requestFilename;
    private int remoteServerPort;
    private String remoteServerHostname;

    public ThreadedUDPServerClient(int serverPort) {
        this.udpserver = new UDPServer(serverPort, true);
        this.udpclient = new UDPClient(false);
    }

    public void changeMode(String mode) {
        this.mode = mode;
    }

    public void setRequestFilename(String filename) {
        this.requestFilename = filename;
    }

    public long getRequestFileSize() {
        return this.udpclient.getRequestFileSize();
    }

    public long getRequestFileLastModified() {
        return this.udpclient.getRequestFileLastModified();
    }

    public void setRemoteServerPort(int port) {
        this.udpclient.setRemoteServerPort(port);
    }

    public void setRemoteServerHostname(String hostname) {
        this.udpclient.setRemoteServerHostname(hostname);
    }

    @Override
    public void run() {
        while (true) {
            if (this.mode.equals("SERVER")) {
                this.udpserver.startServer();
            }
            // CLIENT mode
            this.udpclient.activate();
            this.udpclient.setRequestFilename(this.requestFilename);
            this.udpclient.setRemoteServerHostname(this.remoteServerHostname);
            this.udpclient.setRemoteServerPort(this.remoteServerPort);
            this.udpclient.startClient();
            this.udpclient.deactivate();
            this.mode = "SERVER";
        }

    }

    // public static void main(String[] args) {
    // if (args.length == 0) {
    // System.out.println("Usage: java ThreadedUDPServerClient mode port filename");
    // return;
    // }

    // // set the port to listen on
    // int port;
    // try {
    // port = Integer.parseInt(args[1]);
    // if (port < 1024 || port > 65535)
    // port = 8080;
    // } catch (RuntimeException ex) {
    // port = 80;
    // }

    // // set mode, default to SERVER
    // String mode = args[0];
    // if (!mode.equals("SERVER")) {
    // if (args.length != 3) {
    // System.out.println("Usage: java ThreadedUDPServerClient port mode filename");
    // return;
    // }
    // String filename = args[2];
    // ThreadedUDPServerClient client = new ThreadedUDPServerClient(port, mode);
    // client.setRequestFilename(filename);
    // client.start();
    // } else {
    // ThreadedUDPServerClient server = new ThreadedUDPServerClient(port, mode);
    // server.start();
    // }

    // }
}
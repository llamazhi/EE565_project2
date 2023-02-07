import java.net.*;
import java.io.*;

public class RemoteServerInfo {
    public InetAddress host;
    public Integer port;
    public Integer rate;

    public RemoteServerInfo(String hostname, Integer port, Integer rate) throws IOException {
        this.host = InetAddress.getByName(hostname);
        this.port = port;
        this.rate = rate;
    }
}

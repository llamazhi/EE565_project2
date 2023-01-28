import java.net.URI;
import java.net.URISyntaxException;

public class HTTPURIParser {
    URI uriObj;
    String path;
    public HTTPURIParser(String URI) {
        try {
            this.uriObj = new URI(URI);
            this.path = uriObj.getPath();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

//    public int getTCPPort() {
//        return this.uriObj.getPort();
//    }

    // Returns all the queries contained in the uri, splitting them by "&"
    public String[] getQueries() {
        return this.uriObj.getQuery().split("&");
    }

    // Return if the uri contains "add" keyword
    public boolean ifAdd() {
        return this.path.contains("add");
    }

    // Return if the uri contains "view" keyword
    public boolean ifView() {
        return this.path.contains("view");
    }

    // Return if the uri contains "config" keyword
    public boolean ifConfig() {
        return this.path.contains("config");
    }

    // Return if the uri contains "status" keyword
    public boolean ifStatus() {
        return this.path.contains("status");
    }

}

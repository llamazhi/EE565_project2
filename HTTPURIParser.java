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

    // Returns all the queries contained in the uri, splitting them by "&"
    public String[] getQueries() {
        return this.uriObj.getQuery().split("&");
    }

    public boolean hasUDPRequest() {
        return (this.hasAdd() || this.hasView() || this.hasConfig() || this.hasStatus());
    }

    public String getPath() {
        return this.path;
    }

    // Return if the uri contains "add" keyword
    public boolean hasAdd() {
        return this.path.contains("add");
    }

    // Return if the uri contains "view" keyword
    public boolean hasView() {
        return this.path.contains("view");
    }

    // Return if the uri contains "config" keyword
    public boolean hasConfig() {
        return this.path.contains("config");
    }

    // Return if the uri contains "status" keyword
    public boolean hasStatus() {
        return this.path.contains("status");
    }

}

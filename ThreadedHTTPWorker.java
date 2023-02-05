import java.net.*;
import java.io.*;
import java.lang.Thread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// ThreadedHTTPWorker class is responsible for all the
// actual string & data transfer
public class ThreadedHTTPWorker extends Thread {
    private Socket client;
    private DataInputStream inputStream = null;
    private DataOutputStream outputStream = null;
    private final String CRLF = "\r\n";
    private boolean isRangeRequest = false;
    private int[] rangeNum;

    public ThreadedHTTPWorker(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {
        try {
            System.out.println("Worker Thread starts running ... ");
            this.outputStream = new DataOutputStream(this.client.getOutputStream());
            this.inputStream = new DataInputStream(this.client.getInputStream());

            // retrieve request header as String
            BufferedReader in = new BufferedReader(new InputStreamReader(this.client.getInputStream()));
            String inputLine;
            String req = "";
            while ((inputLine = in.readLine()) != null) {
                // System.out.println (inputLine);
                req += inputLine;
                req += "\r\n";
                if (inputLine.length() == 0) {
                    break;
                }
            }
            // System.out.println(req);
            String relativeURL = preprocessReq(req);
            parseURI(req, relativeURL);
        } catch (IOException e) {
            System.out.println("Something wrong with connection");
            e.printStackTrace();
        } finally {
            if (this.outputStream != null) {
                try {
                    this.outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (this.inputStream != null) {
                try {
                    this.inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (this.client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String preprocessReq(String req) {
        // System.out.println(req);
        String[] reqComponents = req.split("\r\n");
        System.out.println("first line: " + reqComponents[0]);
        String relativeURL = reqComponents[0];
        relativeURL = relativeURL.replace("GET /", "").replace(" HTTP/1.1", "");

        if (isRangeRequest(req)) {
            this.isRangeRequest = true;
            setRange(req);
        } else {
            this.isRangeRequest = false;
        }
        System.out.println("relativeURL: " + "\"" + relativeURL + "\"");
        return relativeURL;
    }

    private void parseURI(String req, String relativeURL) {
        HTTPURIParser parser = new HTTPURIParser(relativeURL);
        if (!parser.hasUDPRequest()) {
            // This is a local request
            String path = parser.getPath();
            System.out.println("path: " + path);
            String MIMEType = categorizeFile(path);
            File f = new File(path);
            if (f.exists()) {
                if (this.isRangeRequest) {
                    sendPartialContent(MIMEType, this.rangeNum, f);
                } else {
                    sendFullContent(MIMEType, f);
                }
            } else {
                sendErrorResponse("This file is not found in local server.");
            }
        } else if (parser.hasAdd()) {
            // store the parameter information
            String[] queries = parser.getQueries();
            addPeer(queries);
        } else if (parser.hasView()) {
            String path = parser.getPath();
            path = path.replace("peer/view/", "");
            viewContent(path);
        } else if (parser.hasConfig()) {
            // int rate = Integer.parseInt(this.parameterMap.get("rate"));
            // configureRate(rate);
        } else if (parser.hasStatus()) {
            getStatus();
            // this.outputStream.writeBytes(info);
        } else {
            sendErrorResponse("Invalid request");
        }
    }

    // store the parameter information
    private void addPeer(String[] queries) {
        try {
            HashMap<String, String> keyValue = new HashMap<>();
            for (String q : queries) {
                String[] queryComponents = q.split("=");
                keyValue.put(queryComponents[0], queryComponents[1]);
            }
            System.out.println(keyValue);

            // may pass the parameters to UDP later
            String path = keyValue.get("path");
            int port = Integer.parseInt(keyValue.get("port"));
            String host = keyValue.get("host");
            int rate = Integer.parseInt(keyValue.get("rate")) * 1000;
            RemoteServerInfo info = new RemoteServerInfo(host, port, rate);
            VodServer.addPeer(path, info);
            // Pass the queries to backend port
            // At this stage, we just print them out
            String html = "<html><body><h1>Peer Added!</h1></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getDateInfo() + " GMT" + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            // sprintf(response, "HTTP/1.1 200 OK\nLast-Modified: %s\nConnection:
            // close\nContent-Type: %s\nAccept-Ranges: bytes\nDate: %s\nContent-Length:
            // %d\n\n", lastModifiedTimeString, contentType, dateTimeString, file_size);
            this.outputStream.writeBytes(response);

        } catch (NumberFormatException | IOException e) {
            sendErrorResponse("invalid query");
            e.printStackTrace();
        }

    }

    private void viewContent(String path) {
        UDPClient udpclient = new UDPClient();

        ArrayList<RemoteServerInfo> infos = VodServer.getRemoteServerInfo(path); // TODO: get chunks from multiple
                                                                                 // remote servers
        if (infos == null) {
            sendErrorResponse("Please add peer first!");
            return;
        }
        VodServer.setRate(infos.get(0).rate);
        udpclient.startClient(path, infos.get(0), this.outputStream);

    }

    private void sendErrorResponse(String msg) {
        try {
            String html = "<html><body><h1>404 Not Found!</h1><p>" + msg + "</p></body></html>";
            String response = "HTTP/1.1 404 Not Found" + this.CRLF +
                    "Date: " + getDateInfo() + " GMT" + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);
            this.outputStream.writeBytes(html);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void configureRate(int rate) {
        // TODO:
        // Add functionality to actually configure the transfer rate
    }

    private void getStatus() {
        try {
            // TODO: Get completeness and bitRate from UDP server
            double completeness = VodServer.getCompleteness();
            int bitRate = VodServer.getBitRate();

            String completenessMsg = Double.toString(completeness) + " %";
            String bitRateMsg = Integer.toString(bitRate) + " bit/s";
            // VodServer.setStatusParams(completeness, bitRate);
            // String completenessMsg = Double.toString(VodServer.getCompleteness());
            // String bitRateMsg = Integer.toString(VodServer.getBitRate());
            String html = "<html><body><h1>Current status: </h1><p>File Complenteness: " + completenessMsg
                    + "<br> Current bit rate: " + bitRateMsg + "</p></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getDateInfo() + " GMT" + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isRangeRequest(String req) {
        return req.contains("Range: ");
    }

    // extract range from request
    private void setRange(String req) {
        String[] lines = req.split("\r\n");
        int start = 0;
        int end = 0;
        this.rangeNum = new int[] { 0, 0 };
        for (String l : lines) {
            // check if the line contains "Range: " field
            if (l.contains("Range: bytes=")) {
                int len = "Range: bytes=".length();
                String range = l.substring(len);
                String startNum = range.split("-")[0];
                String endNum = range.split("-")[1];
                start = Integer.parseInt(startNum);
                end = Integer.parseInt(endNum);
                this.rangeNum[0] = start;
                this.rangeNum[1] = end;
            }
        }
    }

    // String[] acceptableFiles = {"txt", "css", "html", "gif", "jpg", "png", "js",
    // "mp4", "webm", "ogg"};

    private String categorizeFile(String path) {
        try {
            // convert the file name into string
            String MIMEType = "";
            Path p = Paths.get(path);
            MIMEType = Files.probeContentType(p);
            return MIMEType;
        } catch (IOException e) {
            e.printStackTrace();
            return "Unacceptable file found";
        }
    }

    private void sendPartialContent(String MIMEType, int[] numRange, File f) {
        try {
            String date = getDateInfo();
            int rangeEnd = numRange[1];
            int rangeStart = numRange[0];
            int actualLength = rangeEnd - rangeStart + 1;
            String partialResponse = "HTTP/1.1 206 Partial Content" + this.CRLF +
                    "Content-Type: " + MIMEType + this.CRLF +
                    "Content-Length: " + actualLength + this.CRLF +
                    "Date: " + date + " GMT" + this.CRLF +
                    "Content-Range: bytes " + rangeStart + "-" + rangeEnd + "/" + f.length() + this.CRLF +
                    "Connection: close" + this.CRLF +
                    this.CRLF;
            this.outputStream.writeBytes(partialResponse);
            FileInputStream fileInputStream = new FileInputStream(f);
            byte[] buffer = new byte[rangeEnd];
            fileInputStream.read(buffer, rangeStart, rangeEnd);
            this.outputStream.write(buffer, 0, rangeEnd);
            fileInputStream.close();
            // sendPartialFile(f, rangeStart, rangeEnd);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void sendFullContent(String MIMEType, File f) {
        try {
            String date = getDateInfo();
            DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Content-Type: " + MIMEType + this.CRLF +
                    "Content-Length: " + f.length() + this.CRLF +
                    "Date: " + date + " GMT" + this.CRLF +
                    "Last-Modified: " + formatter.format(f.lastModified()) + " GMT" + this.CRLF +
                    "Connection: close" + this.CRLF +
                    this.CRLF;
            // System.out.println(response);
            this.outputStream.writeBytes(response);
            System.out.println("Response header sent ... ");
            int bytes = 0;
            // Open the File
            FileInputStream fileInputStream = new FileInputStream(f);

            // Here we break file into chunks
            byte[] buffer = new byte[1024];
            while ((bytes = fileInputStream.read(buffer)) != -1) {
                // Send the file
                this.outputStream.write(buffer, 0, bytes); // file content
                this.outputStream.flush(); // flush all the contents into stream
            }
            // close the file here
            // System.out.println("File sent");
            fileInputStream.close();
            // sendFileNormal(f);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getDateInfo() {
        // produce day of the week
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
        return formatter.format(cal.getTime());
    }
}

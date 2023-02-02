import java.net.*;
import java.io.*;
import java.lang.Thread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

// ThreadedHTTPWorker class is responsible for all the
// actual string & data transfer
public class ThreadedHTTPWorker extends Thread {
    private Socket client;
    private DataInputStream inputStream = null;
    private DataOutputStream outputStream = null;
    private final String CRLF = "\r\n";
    private Integer reqNum;
    private HashMap<Integer, HashMap<String, String>> parameterMap;
    private ThreadedUDPServerClient udpserver;

    public ThreadedHTTPWorker(Socket client, ThreadedUDPServerClient udpserver) {
        this.client = client;
        this.udpserver = udpserver;
        this.parameterMap = new HashMap<>();
        this.reqNum = 0;
    }

    public HashMap<Integer, HashMap<String, String>> getParameters() {
        return this.parameterMap;
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

        System.out.println("relativeURL: " + "\"" + relativeURL + "\"");
        return relativeURL;
    }

    private void parseURI(String req, String relativeURL) {
        HTTPURIParser parser = new HTTPURIParser(relativeURL);

        // This is just a start page to show that server has started
        // TODO:
        // Add functionality so that only valid URIs can be recognized
        // try {
        if (!parser.hasUDPRequest()) {
            // This is a local request
            String path = parser.getPath();
            viewContent(path);
        } else if (parser.hasAdd()) {
            // store the parameter information
            String[] queries = parser.getQueries();
            addPeer(queries);
        } else if (parser.hasView()) {
            String path = parser.getPath();
            path = path.replace("peer/view/", "");
            byte[] buffer = new byte[1024];

            // assume viewContent would return packetNum
            int count = 0;
            viewContent(path);
        } else if (parser.hasConfig()) {

            // int rate = Integer.parseInt(this.parameterMap.get("rate"));
            // configureRate(rate);
        } else if (parser.hasStatus()) {
            String info = getStatus();
            // this.outputStream.writeBytes(info);
        } else {
            sendErrorResponse();
        }
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
    }

    // store the parameter information
    private void addPeer(String[] queries) {
        try {
            this.reqNum += 1;
            HashMap<String, String> params = new HashMap<>();
            for (String q : queries) {
                String[] queryComponents = q.split("=");
                params.put(queryComponents[0], queryComponents[1]);
            }
            parameterMap.put(this.reqNum, params);
            // pass params to UDP server

            System.out.println(parameterMap);

            // may pass the parameters to UDP later
            // String path = parameterMap.get("path");
            // int portNum = Integer.parseInt(parameterMap.get("port"));
            // String host = parameterMap.get("host");

            // Pass the queries to backend port
            // At this stage, we just print them out
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getDateInfo() + " GMT" + this.CRLF +
                    "Connection: keep-alive" + this.CRLF +
                    this.CRLF;
            this.outputStream.writeBytes(response);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // viewContent extends from the send file functions from project1
    private void viewContent(String path) {
        // TODO:
        // Add functionality to actually receive content from the server

        // System.out.println("viewPath: " + path);
        // File f = new File(path);
        // if (f.exists()) {
        // String fileType = categorizeFile(path);
        // long fileSize = f.length();
        // if (isRangeRequest(req)) {
        // int[] rangeNum = getRange(req);
        // sendPartialContent(fileType, rangeNum[0], rangeNum[1], f, fileSize);
        // } else {
        // sendFullContent(fileType, f, fileSize);
        // }
        // } else {
        // sendErrorResponse();
        // }

    }

    private void sendErrorResponse() {
        try {
            String response = "HTTP/1.1 404 Not Found" + this.CRLF +
                    this.CRLF;
            this.outputStream.writeBytes(response);
            String html = """
                    <html>
                        <body>
                            <p>404 Not Found!</p>
                        </body>
                    </html>
                    """;
            this.outputStream.writeBytes(html);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void configureRate(int rate) {
        // TODO:
        // Add functionality to actually configure the transfer rate
    }

    private String getStatus() {
        // TODO:
        // Add functionality to actually show the status
        return "";
    }

    private boolean isRangeRequest(String req) {
        return req.contains("Range: ");
    }

    // extract range from request
    private int[] getRange(String req) {
        String[] lines = req.split("\r\n");
        int start = 0;
        int end = 0;
        int[] rangeNum = new int[] { 0, 0 };
        for (String l : lines) {
            // check if the line contains "Range: " field
            if (l.contains("Range: bytes=")) {
                int len = "Range: bytes=".length();
                String range = l.substring(len);
                String startNum = range.split("-")[0];
                String endNum = range.split("-")[1];
                start = Integer.parseInt(startNum);
                end = Integer.parseInt(endNum);
                rangeNum[0] = start;
                rangeNum[1] = end;
            }
        }
        return rangeNum;
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

    private void sendPartialContent(String MIMEType, int rangeStart, int rangeEnd, File f, long fileSize) {
        try {
            String date = getDateInfo();
            int actualLength = rangeEnd - rangeStart + 1;
            String partialResponse = "HTTP/1.1 206 Partial Content" + this.CRLF +
                    "Content-Type: " + MIMEType + this.CRLF +
                    "Content-Length: " + actualLength + this.CRLF +
                    "Date: " + date + " GMT" + this.CRLF +
                    "Content-Range: bytes " + rangeStart + "-" + rangeEnd + "/" + fileSize + this.CRLF +
                    "Connection: close" + this.CRLF +
                    this.CRLF;
            this.outputStream.writeBytes(partialResponse);
            sendPartialFile(f, rangeStart, rangeEnd);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void sendPartialFile(File f, int rangeStart, int readLen) {
        try {
            FileInputStream fileInputStream = new FileInputStream(f);
            byte[] buffer = new byte[readLen];
            fileInputStream.read(buffer, rangeStart, readLen);
            this.outputStream.write(buffer, 0, readLen);
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFullContent(String MIMEType, File f, long fileSize) {
        try {
            String date = getDateInfo();
            DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Content-Type: " + MIMEType + this.CRLF +
                    "Content-Length: " + fileSize + this.CRLF +
                    "Date: " + date + " GMT" + this.CRLF +
                    "Last-Modified: " + formatter.format(f.lastModified()) + " GMT" + this.CRLF +
                    "Connection: close" + this.CRLF +
                    this.CRLF;
            // System.out.println(response);
            this.outputStream.writeBytes(response);
            System.out.println("Response header sent ... ");
            sendFileNormal(f);

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

    private void sendFileNormal(File file) {
        try {
            int bytes = 0;
            // Open the File
            FileInputStream fileInputStream = new FileInputStream(file);

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
        } catch (IOException e) {
            System.out.println("File transfer issue");
            e.printStackTrace();
        }
    }
}

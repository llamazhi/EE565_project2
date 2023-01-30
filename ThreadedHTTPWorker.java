import java.net.*;
import java.io.*;
import java.lang.Thread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

// ThreadedHTTPWorker class is responsible for all the
// actual string & data transfer
public class ThreadedHTTPWorker extends Thread {
    private Socket client;
    private DataInputStream inputStream = null;
    private DataOutputStream outputStream = null;
    private final String CRLF = "\r\n";
    private String[] queries;

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
//                System.out.println (inputLine);
                req += inputLine;
                req += "\r\n";
                if (inputLine.length() == 0) {
                    break;
                }
            }
//            System.out.println(req);
            String relativeURL = preprocessReq(req);
            parseURI(relativeURL);
        }
        catch (IOException e) {
            System.out.println("Something wrong with connection");
            e.printStackTrace();
        }
        finally {
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
        int pageURLIndex = 0;
        pageURLIndex = req.indexOf("HTTP");
        String header = req.substring(0, pageURLIndex - 1); // remove HTTP/1.1 or HTTP/1.0
        String relativeURL = header.substring(5); // remove GET / at this version

        System.out.println("relativeURL: " + relativeURL);
        return relativeURL;
    }

    private void parseURI(String relativeURL) {
        HTTPURIParser parser = new HTTPURIParser(relativeURL);
        
        // This is just a start page to show that server has started
        // TODO: 
        // Add functionality so that only valid URIs can be recognized
        // String path = "./index.html";
        // File f = new File("./index.html");
        // sendFullContent(categorizeFile(path), f, f.length());

        if (parser.ifAdd()) {
            String[] queries = parser.getQueries();
            addPeer(queries);

            // TODO: tell the backend node the specified information
            this.queries = queries;
            System.out.println(Arrays.toString(queries));
        }

        if (parser.ifView()) {
            String path = parser.getPath();
            path = path.replace("peer/view/", "");
            viewContent(path);
        }

        if (parser.ifConfig()) {
            configureRate();
        }

        if (parser.ifStatus()) {
            showStatus();
        }
    }

    private void addPeer(String[] queries) {
        try {
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

    private void viewContent(String path) {
        // TODO: 
        // Add functionality to actually receive content from the server
        System.out.println("viewPath: " + path);
        String fileType = categorizeFile(path);
        File f = new File(path);
        sendFullContent(fileType, f, f.length());
    }

    private void configureRate() {
        // TODO:
        // Add functionality to actually configure the transfer rate
    }

    private void showStatus() {
        // TODO:
        // Add functionality to actually show the status
    }

//     private void parseRequest(String req, DataOutputStream out) {
//         System.out.println("Begin to parse request ... ");
//         try {
//             String[] acceptableFiles = {"txt", "css", "html", "gif", "jpg", "png", "js",
//                     "mp4", "webm", "ogg"};

//             int pageURLIndex = 0;
//             pageURLIndex = req.indexOf("HTTP");
//             String header = req.substring(0, pageURLIndex - 1); // remove HTTP/1.1 or HTTP/1.0
//             String relativeURL = header.substring(5); // remove GET / at this version

// //            System.out.println(relativeURL.length());
//             String extension = relativeURL.substring(5);
//             System.out.println("relativeURL: " + relativeURL);

//             if (Arrays.asList(acceptableFiles).contains(extension)) {
//                 String path = "src/Content/" + relativeURL;
//                 System.out.println("Current file path: " + path);
//                 File f = new File(path);
//                 long fileSize = f.length();
//                 String MIMEType = categorizeFile(path);
// //                System.out.println(MIMEType);

//                 // check if it is a partial content request
//                 if(req.contains("Range: ")) {
//                     String[] lines =  req.split("\r\n");
//                     int start = 0;
//                     int end = 0;
//                     for (String l : lines) {
//                         // check if the line contains "Range: " field
//                         if (l.contains("Range: bytes=")) {
//                             int len = "Range: bytes=".length();
//                             String range = l.substring(len);
//                             String startNum = range.split("-")[0];
//                             String endNum = range.split("-")[1];
//                             start = Integer.parseInt(startNum);
//                             end = Integer.parseInt(endNum);
//                         }
//                     }
//                     sendPartialContent(MIMEType, start, end, f, fileSize);
// //                    System.out.println("Partial content request detected");
//                 }
//                 else {
//                     sendFullContent(MIMEType, f, fileSize);
// //                    System.out.println("Full content request detected");
//                 }
//             }
//             else {
//                 String errorResponse = "HTTP/1.1 404 Bad Request" + CRLF + CRLF;
//                 out.writeBytes(errorResponse);
//                 System.out.println("Error Page Found");
//             }

//         } catch (IOException e) {
//             e.printStackTrace();
//         }
//     }

    private String categorizeFile (String path) {
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
            FileInputStream fileInputStream
                    = new FileInputStream(f);
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
                    "Connection: keep-alive" + this.CRLF + // test purpose
                    this.CRLF;
//            System.out.println(response);
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
            FileInputStream fileInputStream
                    = new FileInputStream(file);
//            System.out.println("Begin to send file ... ");

            // Here we  break file into chunks
            byte[] buffer = new byte[1024];
            while((bytes = fileInputStream.read(buffer)) != -1) {
                // Send the file
                this.outputStream.write(buffer, 0, bytes); // file content
                this.outputStream.flush(); // flush all the contents into stream
            }
            // close the file here
//            System.out.println("File sent");
            fileInputStream.close();
        } catch (IOException e) {
            System.out.println("File transfer issue");
            e.printStackTrace();
        }
    }
}

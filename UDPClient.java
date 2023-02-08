import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class UDPClient {
    private final static int bufferSize = 8192;
    private final String CRLF = "\r\n";

    public static void intToByteArray(int value, byte[] buffer) {
        buffer[0] = (byte) (value >>> 24);
        buffer[1] = (byte) (value >>> 16);
        buffer[2] = (byte) (value >>> 8);
        buffer[3] = (byte) value;
    };

    public static int byteArrayToInt(byte[] bytes) {
        return (bytes[0] << 24) & 0xff000000 |
                (bytes[1] << 16) & 0x00ff0000 |
                (bytes[2] << 8) & 0x0000ff00 |
                (bytes[3] & 0xff);
    }

    public String startClient(String path, ArrayList<RemoteServerInfo> remoteServers, DataOutputStream outputStream) {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            // send request packet

            byte[] requestData = new byte[bufferSize];
            byte[] receiveData = new byte[bufferSize];
            intToByteArray(0, requestData);

            for (RemoteServerInfo udpserver : remoteServers) {
                String message = path + " " + udpserver.rate;
                byte[] messageBytes = message.getBytes();
                System.arraycopy(messageBytes, 0, requestData, 4, messageBytes.length);
                DatagramPacket outPkt = new DatagramPacket(requestData, requestData.length, udpserver.host,
                        udpserver.port);
                socket.send(outPkt);
            }

            try {
                DatagramPacket inPkt = new DatagramPacket(receiveData, receiveData.length);

                // wait for first packet, and then process the packet...
                socket.setSoTimeout(1000); // wait for response for 1 seconds
                socket.receive(inPkt);
                String result = new String(inPkt.getData(), 0, inPkt.getLength(), "US-ASCII").trim();
                System.out.println("result: " + result);
                if (result.contains("FileNotExistsError")) {
                    return "FileNotExistsError";
                }
                String[] responseValues = result.split(" ");
                Long fileSize = Long.parseLong(responseValues[0]);
                Long fileLastModified = Long.parseLong(responseValues[1]);
                Integer numChunks = Integer.parseInt(responseValues[2]);
                Integer windowSize = Integer.parseInt(responseValues[3]);
                System.out.println("numChunks: " + numChunks + " windowSize: " + windowSize);

                SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
                formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date currentTime = new Date();
                String MIMEType = URLConnection.getFileNameMap().getContentTypeFor(path);
                String response = "HTTP/1.1 200 OK" + this.CRLF +
                        "Content-Type: " + MIMEType + this.CRLF +
                        "Content-Length: " + String.valueOf(fileSize) + this.CRLF +
                        "Date: " + formatter.format(currentTime) + this.CRLF +
                        "Last-Modified: " + formatter.format(fileLastModified) + this.CRLF +
                        "Connection: close" + this.CRLF +
                        this.CRLF;
                outputStream.writeBytes(response);

                // slide window until the rightmost end hits the end of chunks
                // another condition to start receiving files is numChunks is even smaller
                // than windowSize
                int windowStart = 1;
                int windowEnd = Math.min(windowSize, numChunks);
                byte[][] buffer = new byte[windowSize][bufferSize];
                long startTime = System.currentTimeMillis();
                long bitsReceivedInOneSec = 0;
                Set<Integer> seen = new HashSet<Integer>();

                while (windowStart <= windowEnd && windowEnd <= numChunks) {
                    boolean windowFull = true;

                    // send request for unreceived chunk within the window
                    for (int i = windowStart; i <= windowEnd; i++) {
                        if (!seen.contains(i)) {
                            intToByteArray(i, requestData);
                            RemoteServerInfo udpserver = remoteServers.get(i % remoteServers.size());
                            DatagramPacket outPkt = new DatagramPacket(requestData, requestData.length, udpserver.host,
                                    udpserver.port);
                            socket.send(outPkt);
                        }
                    }

                    // receive packet within the window
                    while (true) {
                        try {
                            inPkt = new DatagramPacket(new byte[bufferSize], bufferSize);
                            socket.setSoTimeout(100);
                            socket.receive(inPkt);
                            int seqNum = byteArrayToInt(inPkt.getData());
                            System.out.println("get No." + seqNum + " packet from: " + inPkt.getPort() + " port");
                            if (seqNum < 0 || seqNum > numChunks || seen.contains(seqNum)) {
                                continue;
                            } else {
                                buffer[seqNum - windowStart] = inPkt.getData();
                                seen.add(seqNum);
                                VodServer.setCompleteness(100.0 * seen.size() / numChunks);
                                VodServer.clientReceiveTimestamps.add(System.currentTimeMillis());
                                bitsReceivedInOneSec += bufferSize * 8;
                                if (VodServer.bitRateChanged) {
                                    bitsReceivedInOneSec = 0;
                                    VodServer.bitRateChanged = false;
                                }
                                // sleep if bitsReceived exceed bit rate
                                while (VodServer.getBitRate() != 0
                                        && bitsReceivedInOneSec >= VodServer.getBitRate() * 1000) {
                                    // double elapsedTime = System.currentTimeMillis() - startTime;
                                    // if (elapsedTime < 1000) {

                                    try {
                                        // double sleepTime = (1000 - elapsedTime) * 2;
                                        double sleepTime = bitsReceivedInOneSec / VodServer.getBitRate();
                                        System.out.println("sleep for: " + sleepTime + " ms");
                                        Thread.sleep((long) (sleepTime));
                                    } catch (InterruptedException e) {
                                        System.out.println("Thread building issue");
                                    }
                                    // }
                                    startTime = System.currentTimeMillis();
                                    bitsReceivedInOneSec = 0;
                                }
                            }
                        } catch (SocketTimeoutException ex) {
                            break;
                        }
                    }

                    // check if receive all the chunks in this window
                    for (int i = windowStart; i <= windowEnd; i++) {
                        windowFull &= seen.contains(i);
                    }

                    // if not, continue the while loop to resend requests
                    if (!windowFull) {
                        continue;
                    }

                    // write chunks into outputStream
                    try {
                        for (int i = 0; i <= windowEnd - windowStart; i++) {
                            outputStream.write(buffer[i], 4, bufferSize - 4);
                            outputStream.flush(); // flush all the contents into stream
                        }
                    } catch (SocketException ex) {
                        System.out.println("Cannot write to outputStream. Client has closed the socket.");
                    }

                    windowStart = windowEnd + 1;
                    windowEnd = Math.min(windowStart + windowSize - 1, numChunks);
                    System.out.printf("%.2f", 100.0 * seen.size() / numChunks);
                    System.out.println(" % complete");
                }
            } catch (SocketTimeoutException ex) {
                socket.close();
                System.err.println("No connection within 1 seconds");
                return "NoResondFromRemoteServerError";
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return "SocketCannotOpenError";
        }
        return "Success";
    }
}
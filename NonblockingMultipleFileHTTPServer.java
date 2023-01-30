import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.net.*;

public class NonblockingMultipleFileHTTPServer {

    private int port = 80;
    private Map<String, ByteBuffer> contentMap = new HashMap<>();

    public NonblockingMultipleFileHTTPServer(int port) {
        this.port = port;
    }

    private ByteBuffer getContentBuffer(String filePath) {
        if (contentMap.containsKey(filePath)) {
            return contentMap.get(filePath);
        }
        try {
            if (filePath == "/") {
                throw new IOException(filePath);
            }

            String contentType = URLConnection.getFileNameMap().getContentTypeFor(filePath);
            filePath = "Content/" + filePath;
            Path file = FileSystems.getDefault().getPath(filePath);
            byte[] fileData = Files.readAllBytes(file);
            ByteBuffer input = ByteBuffer.wrap(fileData);

            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-length: " + input.limit() + "\r\n"
                    + "Content-type: " + contentType + "\r\n\r\n";
            byte[] headerBytes = header.getBytes(Charset.forName("US-ASCII"));

            ByteBuffer buffer = ByteBuffer.allocate(input.limit() + headerBytes.length);
            buffer.put(headerBytes);
            buffer.put(input);
            buffer.flip();
            contentMap.put(filePath, buffer);
            return buffer;
        } catch (IOException ex) {
            if (contentMap.containsKey("404")) {
                return contentMap.get("404");
            }

            ByteBuffer input = ByteBuffer
                    .wrap("<html><body><h1>404 Not Found</h1></body></html>".getBytes());

            String header = "HTTP/1.1 404 Not Found\r\n"
                    + "Content-Type: text/html\r\n"
                    + "Content-length: "
                    + input.limit() + "\r\n\r\n";
            byte[] headerBytes = header.getBytes(Charset.forName("US-ASCII"));
            ByteBuffer buffer = ByteBuffer.allocate(input.limit() + headerBytes.length);

            buffer.put(headerBytes);
            buffer.put(input);
            buffer.flip();
            contentMap.put("404", buffer);

            return buffer;
        }
    }

    public void run() throws IOException {

        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        ServerSocket serverSocket = serverChannel.socket();
        Selector selector = Selector.open();
        InetSocketAddress localPort = new InetSocketAddress(port);
        serverSocket.bind(localPort);
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {

            selector.select();

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {

                SelectionKey key = keys.next();
                keys.remove();
                try {
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel channel = server.accept();
                        channel.configureBlocking(false);
                        channel.register(selector, SelectionKey.OP_READ);
                    } else if (key.isWritable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        ByteBuffer buffer = (ByteBuffer) key.attachment();
                        if (buffer.hasRemaining()) {
                            channel.write(buffer);
                        } else { // we're done
                            channel.close();
                        }
                    } else if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(4096);
                        channel.read(buffer);
                        buffer.flip();
                        String header = new String(buffer.array(), Charset.forName("US-ASCII"));
                        int index = header.indexOf("GET");
                        int nextIndex = header.indexOf("HTTP/");
                        if (index != -1 && nextIndex != -1) {
                            String requestedFile = header.substring(index + 4, nextIndex - 1).trim();
                            System.out.print(channel.getRemoteAddress() + " get " + requestedFile);
                            System.out.println();
                            ByteBuffer input = getContentBuffer(requestedFile);
                            key.interestOps(SelectionKey.OP_WRITE);
                            key.attach(input.duplicate());
                        }
                    }
                } catch (IOException ex) {
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException cex) {
                    }
                }
            }
        }
    }

    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println("Usage: java NonblockingMultipleFileHTTPServer port ");
            return;
        }

        try {
            // set the port to listen on
            int port;
            try {
                port = Integer.parseInt(args[0]);

                if (port < 1 || port > 65535)
                    port = 80;
            } catch (RuntimeException ex) {
                port = 80;

            }

            NonblockingMultipleFileHTTPServer server = new NonblockingMultipleFileHTTPServer(port);
            server.run();
        } catch (IOException ex) {

            System.err.println(ex);
        }

    }
}

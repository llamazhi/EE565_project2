import java.nio.ByteBuffer;
import java.net.*;
import java.util.logging.*;
import java.io.*;
import java.util.*;

public class udpserver {

	private final static int PORT = 7707;
	private final static Logger audit = Logger.getLogger("requests");
	private final static Logger errors = Logger.getLogger("errors");
	private final static int bufferSize = 1024;
	private static int numPackets = 0;

	public static void main(String[] args) {

		try (DatagramSocket socket = new DatagramSocket(PORT)) {
			Map<Integer, byte[]> chunks = new HashMap<>();
			while (true) {
				try {
					DatagramPacket inPkt = new DatagramPacket(new byte[bufferSize], bufferSize);
					socket.receive(inPkt);
					byte[] seqnumBytes = new byte[4];
					System.arraycopy(inPkt.getData(), 0, seqnumBytes, 0, 4);
					int seqnum = ByteBuffer.wrap(seqnumBytes).getInt();
					String requestString = new String(inPkt.getData(), 4, inPkt.getLength() - 4).trim();
					if (!requestString.isEmpty()) {
						audit.info(requestString);
						FileInputStream fis = new FileInputStream("Content/" + requestString);
						int windowSize = 10;
						numPackets = (int) Math.ceil((double) fis.getChannel().size() / (bufferSize - 4));
						byte[] data = (numPackets + " " + windowSize).getBytes();

						audit.info("Begin to send file ... ");
						// Here we break file into chunks
						int chunkNumber = 1;
						byte[] chunk = new byte[bufferSize];
						// int bytesRead;
						while (fis.read(chunk, 4, bufferSize - 4) > 0) {
							byte[] chunkNumberByte = ByteBuffer.allocate(4).putInt(chunkNumber).array();
							System.arraycopy(chunkNumberByte, 0, chunk, 0, 4);
							chunks.put(chunkNumber, chunk);
							chunkNumber++;
							chunk = new byte[bufferSize];
						}
						fis.close();
						DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(),
								inPkt.getPort());
						socket.send(outPkt);
						audit.info(numPackets + " " + windowSize + " " + inPkt.getAddress());
					} else if (seqnum > 0 && seqnum <= numPackets) {
						audit.info("request seqnum: " + seqnum);
						DatagramPacket outPkt = new DatagramPacket(chunks.get(seqnum),
								chunks.get(seqnum).length,
								inPkt.getAddress(),
								inPkt.getPort());
						socket.send(outPkt);

					}
				} catch (IOException | RuntimeException ex) {
					errors.log(Level.SEVERE, ex.getMessage(), ex);

				}
			}
		} catch (IOException ex) {
			errors.log(Level.SEVERE, ex.getMessage(), ex);
		}

	}

}
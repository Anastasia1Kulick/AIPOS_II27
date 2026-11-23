import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class UDPServer {
    public static final int PORT = 666;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("UDP server started on port " + port);
        byte[] buf = new byte[65535];
        try {
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    System.out.println("Received from " + packet.getAddress().getHostAddress() + ":" + packet.getPort() + ": " + received);
                    String reply;
                    if (CreateCommand.isCreate(received)) {
                        long size = CreateCommand.execute(received);
                        reply = size < 0 ? CreateCommand.END_MESSAGE : "File created, size: " + size + " bytes";
                    } else {
                        reply = received;
                    }
                    byte[] data = reply.getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            socket.close();
        }
    }
}

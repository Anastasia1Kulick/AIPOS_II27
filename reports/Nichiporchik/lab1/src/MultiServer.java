import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MultiServer {
    static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        ServerSocket s = new ServerSocket(port);
        System.out.println("Server Started: " + s);
        try {
            while (true) {
                Socket socket = s.accept();
                try {
                    new ServeOne(socket);
                } catch (IOException e) {
                    socket.close();
                }
            }
        } finally {
            s.close();
        }
    }
}

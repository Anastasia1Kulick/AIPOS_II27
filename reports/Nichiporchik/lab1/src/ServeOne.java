import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ServeOne extends Thread {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public ServeOne(Socket s) throws IOException {
        socket = s;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), false);
        start();
    }

    private void send(String message) {
        out.print(message + "\r\n");
        out.flush();
    }

    private static String clean(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\b' || c == 0x7F) {
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                }
            } else if (c == '�' || Character.isISOControl(c)) {
                continue;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public void run() {
        System.out.println("Client connected: " + socket.getRemoteSocketAddress());
        try {
            while (true) {
                String raw = in.readLine();
                if (raw == null) {
                    break;
                }
                String str = clean(raw);
                if (str.equals("END")) {
                    break;
                }
                System.out.println("Received from " + socket.getRemoteSocketAddress() + ": " + str);
                if (CreateCommand.isCreate(str)) {
                    long size = CreateCommand.execute(str);
                    if (size < 0) {
                        send(CreateCommand.END_MESSAGE);
                        break;
                    }
                    send("File created, size: " + size + " bytes");
                } else {
                    System.out.println("Echoing: " + str);
                    send(str);
                }
            }
        } catch (IOException e) {
            System.err.println("IO Exception");
        } finally {
            System.out.println("closing " + socket.getRemoteSocketAddress() + "...");
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Socket not closed");
            }
        }
    }
}

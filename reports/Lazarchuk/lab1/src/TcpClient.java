import java.io.*;
import java.net.*;


public class TcpClient implements ClientFrame.Transport {
    private final String host;
    private final int port;
    private Socket socket;
    private Reader in;
    private Writer out;
    private final char[] buf = new char[1024];

    TcpClient(String host, int port) { this.host = host; this.port = port; }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        in = new InputStreamReader(socket.getInputStream(), Protocol.CHARSET);
        out = new OutputStreamWriter(socket.getOutputStream(), Protocol.CHARSET);
    }
    public void send(String s) throws IOException { out.write(s); out.flush(); }
    public String receive() throws IOException {
        int n = in.read(buf);
        return n == -1 ? null : new String(buf, 0, n);
    }
    public void close() { try { if (socket != null) socket.close(); } catch (IOException ignored) { } }
    public String describe() { return "TCP " + host + ":" + port; }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : Protocol.DEFAULT_HOST;   // адрес по умолчанию
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        ClientFrame.run("TCP-клиент", new TcpClient(host, port), "tcp_client.log");
    }
}

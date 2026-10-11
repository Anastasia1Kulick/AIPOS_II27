package udp;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UdpClientSession {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final InetAddress address;
    private final int port;

    private final char[] buffer = new char[64];
    private int charsRead = 0;
    private final LocalDateTime sessionStart;

    public UdpClientSession(InetAddress address, int port) {
        this.address = address;
        this.port = port;
        this.sessionStart = LocalDateTime.now();
    }

    public InetAddress getAddress() { return address; }
    public int getPort() { return port; }

    public String getKey() {
        return address.getHostAddress() + ":" + port;
    }

    public LocalDateTime getSessionStart() { return sessionStart; }

    public String addChar(char ch) {
        buffer[charsRead++] = ch;
        if (charsRead == buffer.length) {
            String group = new String(buffer, 0, buffer.length);
            charsRead = 0;
            return group;
        }
        return null;
    }

    public String formatStart() {
        return sessionStart.format(FORMATTER);
    }

}
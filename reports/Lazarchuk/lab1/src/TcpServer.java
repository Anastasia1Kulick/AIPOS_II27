import java.io.*;
import java.net.*;
import java.util.List;

/** Лабораторная №1. TCP-сервер (задание №5). Клиент: telnet или TcpClient. */
public class TcpServer {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.DEFAULT_PORT;
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("TCP-сервер запущен, порт " + port);
            while (true) {
                Socket client = server.accept();            // ждем подключения
                new Thread(() -> handle(client)).start();   // отдельный поток на клиента
            }
        }
    }

    private static void handle(Socket client) {
        String who = client.getRemoteSocketAddress().toString();
        System.out.println("Подключился " + who);
        try (client;
             Reader in = new InputStreamReader(client.getInputStream(), Protocol.CHARSET);
             Writer out = new OutputStreamWriter(client.getOutputStream(), Protocol.CHARSET)) {
            Protocol.Session session = new Protocol.Session();
            char[] chars = new char[256];
            int n;
            while (!session.isFinished() && (n = in.read(chars)) != -1) {
                List<String> answers = session.feed(new String(chars, 0, n));
                for (String a : answers) {
                    out.write(a + "\r\n");
                    out.flush();
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка " + who + ": " + e.getMessage());
        }
        System.out.println("Соединение закрыто " + who);   // try-with-resources закрыл сокет
    }
}

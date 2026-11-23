import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class TCPServer {
    private static final int PORT = 6666;
    private static final Path DIRECTORY = Paths.get("server_files");

    public static void main(String[] args) throws IOException {
        Files.createDirectories(DIRECTORY);
        // Сервер работает на локальном компьютере и ожидает клиентов.
        try (ServerSocket server = new ServerSocket(PORT, 50,
                InetAddress.getByName("127.0.0.1"))) {
            System.out.println("Сервер запущен: 127.0.0.1:" + PORT);
            while (true) {
                Socket client = server.accept();
                handleClient(client);
            }
        }
    }

    private static void handleClient(Socket client) {
        System.out.println("Подключился клиент: " + client.getRemoteSocketAddress());
        try (Socket socket = client;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String command;
            while ((command = in.readLine()) != null) {
                System.out.println("Получено: " + command);
                if (!command.startsWith("load ")) {
                    out.println("Используйте команду load fname.txt");
                    continue;
                }

                // Запрашивать можно только текстовые файлы из каталога сервера.
                String name = command.substring(5).trim();
                if (!name.matches("[^/\\\\:]+\\.txt")) {
                    out.println("Недопустимое имя файла. Соединение закрыто.");
                    break;
                }
                Path file = DIRECTORY.resolve(name);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    out.println("Файл " + name + " не найден. Соединение закрыто.");
                    break;
                }
                // Читаем файл построчно и отсылаем содержимое клиенту.
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.println(line);
                    }
                } catch (IOException e) {
                    out.println("Ошибка чтения файла. Соединение закрыто.");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e.getMessage());
        } finally {
            System.out.println("Клиент отключён.");
        }
    }
}

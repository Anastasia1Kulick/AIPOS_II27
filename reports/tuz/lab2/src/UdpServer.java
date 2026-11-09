import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * UDP-сервер задания 2, порт 666.
 * Команда save имя начинает запись. Текст до #end## попадает в файл.
 */
public class UdpServer {

    public static final int PORT = 666;
    private static final String MARKER = "#end##";

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("UDP-сервер запущен, порт " + port);
            byte[] buffer = new byte[4096];
            String fileName = null;
            StringBuilder body = null;
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String line = text(packet).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (body == null) {
                    if (line.startsWith("save ")) {
                        fileName = line.substring(5).trim();
                        if (target(fileName) == null) {
                            reply(socket, packet, "Ошибка: файл \"" + fileName + "\" нельзя создать");
                            continue;
                        }
                        body = new StringBuilder();
                        reply(socket, packet, "Приём файла " + fileName);
                    } else {
                        reply(socket, packet, "Echo: " + line);
                    }
                    continue;
                }
                int marker = line.indexOf(MARKER);
                if (marker < 0) {
                    body.append(line).append('\n');
                    continue;
                }
                body.append(line, 0, marker);
                reply(socket, packet, store(fileName, body.toString()));
                body = null;
            }
        }
    }

    private static String store(String fileName, String content) {
        Path path = target(fileName);
        if (path == null) {
            return "Ошибка: файл \"" + fileName + "\" нельзя создать";
        }
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            int size = content.getBytes(StandardCharsets.UTF_8).length;
            System.out.println("Создан файл " + fileName + ", размер " + size);
            return "Файл создан: " + fileName + ", размер " + size;
        } catch (IOException exception) {
            System.out.println("Не записан файл " + fileName);
            return "Ошибка: файл \"" + fileName + "\" нельзя создать";
        }
    }

    private static Path target(String fileName) {
        if (fileName == null || fileName.isEmpty() || fileName.contains("..")
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0 || fileName.indexOf(':') >= 0) {
            return null;
        }
        Path path = Path.of(fileName);
        return path.getNameCount() == 1 ? path : null;
    }

    private static void reply(DatagramSocket socket, DatagramPacket request, String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket response = new DatagramPacket(
                data, data.length, request.getAddress(), request.getPort());
        socket.send(response);
        System.out.println("Ответ: " + message);
    }

    private static String text(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
    }
}

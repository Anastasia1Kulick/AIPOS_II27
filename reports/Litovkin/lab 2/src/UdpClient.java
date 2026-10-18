import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public class UdpClient {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 666;

    public static void main(String[] args) {

        try {

            setRawMode();

            try (
                    var socket = new DatagramSocket();
                    var log = new PrintWriter(
                            new FileWriter("client.log", true),
                            true
                    );
                    var reader = new InputStreamReader(
                            System.in,
                            StandardCharsets.UTF_8
                    )
            ) {

                socket.connect(InetAddress.getByName(HOST),PORT);

                log.println(LocalDateTime.now() + " | Connection started: " + HOST + ":" + PORT);

                System.out.println("UDP client started: " + HOST + ":" + PORT);

                System.out.println("Введите строку и нажмите HOME для отправки.");

                System.out.println("Для выхода: disconnect " + HOST + " " + PORT);

                System.out.print("> ");

                var input = new StringBuilder();

                while (true) {

                    int key = reader.read();

                    if (key == 27) {

                        if (!isHomePressed(reader)) {
                            continue;
                        }

                        String message = input.toString();

                        System.out.println();

                        if (message.isEmpty()) {System.out.print("> ");
                            continue;
                        }

                        byte[] data = message.getBytes(StandardCharsets.UTF_8);

                        socket.send(new DatagramPacket(data,data.length));

                        log.println(LocalDateTime.now() + " | Sent: "+ message);

                        if (message.equalsIgnoreCase("disconnect " + HOST + " " + PORT)) {
                            break;
                        }

                        byte[] buffer = new byte[2048];

                        var responsePacket = new DatagramPacket(buffer,buffer.length);

                        socket.receive(responsePacket);

                        String response = new String(
                                responsePacket.getData(),
                                0,
                                responsePacket.getLength(),
                                StandardCharsets.UTF_8
                        );

                        System.out.println("Server: " + response);

                        if (response.startsWith("Ошибка: различных символов меньше 3")) {
                            break;
                        }

                        input.setLength(0);

                        System.out.print("> ");

                        continue;
                    }

        
                    if (key == 127 || key == 8) {

                        if (!input.isEmpty()) {input.deleteCharAt(input.length() - 1);
                            System.out.print("\b \b");
                        }
                        continue;
                    }

                    if (key == '\n' || key == '\r') {
                        continue;
                    }

                    input.append((char) key);

                    System.out.print((char) key);
                    System.out.flush();
                }

                log.println(LocalDateTime.now() + " | Connection finished.");

                System.out.println("Client stopped.");
            }

        } catch (IOException | InterruptedException e) {

            System.err.println("Client error: " + e.getMessage());

        } finally {

            restoreTerminal();
        }
    }

    private static boolean isHomePressed(
            Reader reader
    ) throws IOException {

        int second = reader.read();
        int third = reader.read();

        if ((second == '[' || second == 'O') && third == 'H') {
            return true;
        }

        if (second == '[' && (third == '1' || third == '7')) {
            return reader.read() == '~';
        }

        return false;
    }

    private static void setRawMode()
            throws IOException, InterruptedException {

        new ProcessBuilder("sh", "-c", "stty -icanon -echo < /dev/tty"
        )
                .inheritIO()
                .start()
                .waitFor();
    }

    private static void restoreTerminal() {

        try {

            new ProcessBuilder("sh", "-c", "stty sane < /dev/tty"
            )
                    .inheritIO()
                    .start()
                    .waitFor();

        } catch (IOException | InterruptedException ignored) {
        }
    }
}
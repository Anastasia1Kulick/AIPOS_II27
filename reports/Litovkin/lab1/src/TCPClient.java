import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TCPClient {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9000;

    private static final int BLOCK_SIZE = 10;

    public static void main(String[] args) {

        System.out.println("TCP CLIENT");

        try (
                Socket socket = new Socket(HOST, PORT);

                BufferedReader console =
                        new BufferedReader(
                                new InputStreamReader(System.in)
                        );

                BufferedReader serverInput =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream(),
                                        StandardCharsets.US_ASCII
                                )
                        );

                PrintWriter serverOutput =
                        new PrintWriter(
                                new OutputStreamWriter(
                                        socket.getOutputStream(),
                                        StandardCharsets.US_ASCII
                                ),
                                true
                        )
        ) {

            System.out.println("Соединение установлено.");
            System.out.println(
                    "Сервер: " + HOST + ":" + PORT
            );

            System.out.println(
                    "Введите ASCII-текст."
            );

            System.out.println(
                    "Для выхода введите: exit\n"
            );

            int sentCharacters = 0;

            while (true) {

                System.out.print("> ");

                String message = console.readLine();

                if (message == null) {
                    break;
                }

                if (message.equalsIgnoreCase("exit")) {
                    break;
                }

                if (!StandardCharsets.US_ASCII
                        .newEncoder()
                        .canEncode(message)) {

                    System.out.println(
                            "Используйте только ASCII-символы."
                    );

                    continue;
                }

                serverOutput.println(message);

                sentCharacters += message.length();

                while (sentCharacters >= BLOCK_SIZE) {

                    String response =
                            serverInput.readLine();

                    System.out.println(
                            "Server: " + response
                    );

                    sentCharacters -= BLOCK_SIZE;
                }
            }

            System.out.println("Соединение закрыто.");

        } catch (ConnectException e) {

            System.out.println(
                    "Не удалось подключиться к серверу."
            );

            System.out.println(
                    "Убедитесь, что сервер запущен."
            );

        } catch (IOException e) {

            System.out.println(
                    "Ошибка: " + e.getMessage()
            );
        }
    }
}
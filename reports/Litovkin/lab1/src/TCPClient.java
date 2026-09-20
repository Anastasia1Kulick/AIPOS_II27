import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TCPClient {

    private static final String HOST = "localhost";
    private static final int PORT = 9000;
    private static final int BLOCK_SIZE = 10;

    public static void main(String[] args) {

        try (
            Socket socket = new Socket(HOST, PORT);

            BufferedReader serverInput =
                new BufferedReader(
                    new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.US_ASCII
                    )
                );

            PrintWriter output =
                new PrintWriter(
                    new OutputStreamWriter(
                        socket.getOutputStream(),
                        StandardCharsets.US_ASCII
                    ),
                    true
                );

            BufferedReader consoleInput =
                new BufferedReader(
                    new InputStreamReader(System.in)
                )
        ) {

            System.out.println("Соединение установлено.");
            System.out.println(
                "Сервер: " + HOST + ":" + PORT
            );

            System.out.println(
                "Введите ASCII-символы."
            );

            System.out.println(
                "Для завершения программы введите: exit"
            );

            int sentCharacters = 0;

            while (true) {

                System.out.print("\nВведите сообщение: ");

                String message = consoleInput.readLine();

                if (message == null) {
                    break;
                }

                if (message.equalsIgnoreCase("exit")) {
                    break;
                }

                if (message.isEmpty()) {
                    continue;
                }

        
                if (message.chars()
                        .anyMatch(c -> c < 32 || c > 126)) {

                    System.out.println(
                        "Используйте только печатные ASCII-символы."
                    );

                    continue;
                }

                output.print(message);
                output.flush();

                sentCharacters += message.length();

                while (sentCharacters >= BLOCK_SIZE) {

                    String response =
                        serverInput.readLine();

                    if (response == null) {

                        System.out.println(
                            "Сервер закрыл соединение."
                        );

                        return;
                    }

                    System.out.println(
                        "Server: " + response
                    );

                    sentCharacters -= BLOCK_SIZE;
                }
            }

            System.out.println("Клиент завершил работу.");

        } catch (UnknownHostException e) {

            System.out.println(
                "Не удалось найти сервер: "
                + e.getMessage()
            );

        } catch (ConnectException e) {

            System.out.println(
                "Не удалось подключиться к серверу."
            );

            System.out.println(
                "Проверьте, запущен ли TCPServer."
            );

        } catch (IOException e) {

            System.out.println(
                "Ошибка клиента: "
                + e.getMessage()
            );
        }
    }
}
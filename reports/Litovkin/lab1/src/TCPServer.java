import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TCPServer {

    private static final int PORT = 9000;
    private static final int BLOCK_SIZE = 10;

    public static void main(String[] args) {

        System.out.println("Запуск TCP-сервера");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("Сервер запущен.");
            System.out.println("Порт: " + PORT);

            while (true) {

                System.out.println("\nОжидание подключения клиента");

                try (
                    Socket clientSocket = serverSocket.accept();

                    InputStream input = clientSocket.getInputStream();

                    PrintWriter output =
                        new PrintWriter(
                            new OutputStreamWriter(
                                clientSocket.getOutputStream(),
                                StandardCharsets.US_ASCII
                            ),
                            true
                        )
                ) {

                    System.out.println("Клиент подключен: "+ clientSocket.getInetAddress());

                    int count = 0;
                    int checksum = 0;

                    StringBuilder sequence = new StringBuilder();

                    int data;

                    while ((data = input.read()) != -1) {

                
                        if (data < 32 || data > 126) {
                            continue;
                        }

                        char symbol = (char) data;

                        sequence.append(symbol);
                        checksum += data;
                        count++;

                        System.out.println("Получен символ: " + symbol + " | ASCII: " + data + " | count: " + count);

                
                        if (count == BLOCK_SIZE) {

                            System.out.println("Последовательность: " + sequence);

                            System.out.println("Контрольная сумма: " + checksum);

    
                            output.println("Checksum = " + checksum);

                
                            count = 0;
                            checksum = 0;
                            sequence.setLength(0);
                        }
                    }

                    System.out.println("Клиент отключился.");

                } catch (IOException e) {

                    System.out.println("Ошибка при работе с клиентом: "+ e.getMessage());
                }
            }

        } catch (IOException e) {

            System.out.println("Ошибка запуска сервера: "+ e.getMessage()
            );
        }
    }
}
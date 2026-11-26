import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) {
        int port = 12345;
        System.out.println("Запуск сервера...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер ожидает подключения на порту " + port + "...");

            try (Socket clientSocket = serverSocket.accept()) {
                System.out.println("Клиент успешно подключен!");

                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

                char[] buffer = new char[10];
                int charsRead;

                while ((charsRead = reader.read(buffer)) != -1) {
                    String block = new String(buffer, 0, charsRead);
                    System.out.println("Получена цепочка (" + charsRead + " симв.): [" + block + "]");

                    // Проверка на маркер разрыва соединения ".."
                    if (block.contains("..")) {
                        System.out.println("Получен символ окончания сеанса (..). Разрыв соединения.");
                        writer.println("Сеанс окончен. Соединение разорвано сервером.");
                        break;
                    }

                    // Подсчет контрольной суммы ASCII символов для полученной цепочки
                    int checksum = 0;
                    for (int i = 0; i < charsRead; i++) {
                        checksum += (int) buffer[i];
                    }

                    System.out.println("Контрольная сумма ASCII для цепочки: " + checksum);
                    writer.println("Контрольная сумма для цепочки [" + block + "] = " + checksum);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка в работе сервера: " + e.getMessage());
        }
        System.out.println("Сервер остановлен.");
    }
}
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Server {
    public static final int PORT = 8080;
    private static final int GROUP_SIZE = 48;
    private static final String TERMINATION = "~#~";

    public static void main(String[] args) throws IOException {
        ServerSocket s = new ServerSocket(PORT);
        System.out.println("Сервер запущен на порту " + PORT);

        try {
            while (true) {
                Socket socket = s.accept();
                System.out.println("Подключен клиент: " + socket);

                try {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter out = new PrintWriter(
                            new BufferedWriter(
                                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);

                    StringBuilder buffer = new StringBuilder();
                    int totalSessionCount = 0; // Считаем ВСЕ символы от начала сеанса
                    boolean terminated = false;

                    int ch;
                    while ((ch = in.read()) != -1) {
                        char c = (char) ch;
                        
                        if (c == '\r') {
                            continue;
                        }
                        
                        if (c == '\n') {
                            if (buffer.toString().contains(TERMINATION)) {
                                out.println("Сеанс завершён");
                                System.out.println("Получен сигнал завершения ~#~");
                                terminated = true;
                                break;
                            }

                            while (buffer.length() >= GROUP_SIZE) {
                                String group = buffer.substring(0, GROUP_SIZE);
                                buffer.delete(0, GROUP_SIZE);

                                int checksum = 0;
                                for (int i = 0; i < group.length(); i++) {
                                    checksum += (int) group.charAt(i);
                                }

                                String response = "Группа принята. Контрольная сумма: " + checksum 
                                                + ". Всего принято от начала сеанса: " + totalSessionCount + ".";
                                out.println(response);
                                System.out.println("Отправлено: " + response);
                            }
                            continue;
                        }
                        
                        // Добавляем символ и увеличиваем общий счетчик
                        buffer.append(c);
                        totalSessionCount++;

                        // Проверка на завершение без \n в конце
                        if (buffer.toString().contains(TERMINATION)) {
                            out.println("Сеанс завершён");
                            System.out.println("Получен сигнал завершения ~#~");
                            terminated = true;
                            break;
                        }
                    }

                    if (!terminated) {
                        out.println("Соединение закрыто клиентом");
                    }
                } finally {
                    System.out.println("Соединение закрыто");
                    socket.close();
                }
            }
        } finally {
            s.close();
        }
    }
}
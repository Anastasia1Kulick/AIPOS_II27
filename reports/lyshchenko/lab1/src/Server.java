// Лабораторная работа №1. Организация TCP-сервера
// Задание 6: прием групп по 48 символов, контрольная сумма ASCII, разрыв по ~#~
import java.io.*;
import java.net.*;

public class Server {
    // Выбираем номер порта за пределами системных 1-1024
    public static final int PORT = 8080;
    private static final int GROUP_SIZE = 48;
    private static final String TERMINATION = "~#~";

    public static void main(String[] args) throws IOException {
        // Создание серверного сокета для прослушивания порта
        ServerSocket s = new ServerSocket(PORT);
        System.out.println("Started: " + s);

        try {
            // Бесконечный цикл для обслуживания клиентов
            while (true) {
                // Метод accept() блокирует выполнение до момента подключения клиента
                Socket socket = s.accept();
                System.out.println("Connection accepted: " + socket);

                try {
                    // Получение потоков ввода-вывода из сокета
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    // PrintWriter с авто-сбросом буфера (флаг true)
                    PrintWriter out = new PrintWriter(
                            new BufferedWriter(
                                    new OutputStreamWriter(socket.getOutputStream())), true);

                    StringBuilder buffer = new StringBuilder();
                    int totalReceived = 0;
                    boolean terminated = false;

                    // Чтение потока символов от клиента
                    int ch;
                    while ((ch = in.read()) != -1) {
                        char c = (char) ch;
                        buffer.append(c);
                        totalReceived++;

                        // Проверяем наличие последовательности завершения "~#~" в любом месте потока
                        if (buffer.toString().contains(TERMINATION)) {
                            out.println("Сеанс завершён. Всего символов: " + totalReceived);
                            System.out.println("Получен сигнал завершения ~#~");
                            terminated = true;
                            break;
                        }

                        // Обрабатываем группы по 48 символов
                        while (buffer.length() >= GROUP_SIZE) {
                            String group = buffer.substring(0, GROUP_SIZE);
                            buffer.delete(0, GROUP_SIZE);

                            // Вычисляем контрольную сумму ASCII
                            int checksum = 0;
                            for (int i = 0; i < group.length(); i++) {
                                 checksum += (int) group.charAt(i);
                            }

                            String response = "Группа принята. Контрольная сумма: " + checksum 
                                            + ", Всего символов: " + totalReceived;
                            out.println(response);
                            System.out.println("Отправлено: " + response);
                        }
                    }

                    if (!terminated) {
                        out.println("Соединение закрыто клиентом");
                    }
                } finally {
                    // Гарантированное закрытие сокета клиента
                    System.out.println("closing...");
                    socket.close();
                }
            }
        } finally {
            // Гарантированное закрытие серверного сокета при завершении работы
            s.close();
        }
    }
} ///:~
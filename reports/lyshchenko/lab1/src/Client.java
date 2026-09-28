import java.io.*;
import java.net.*;
import java.util.Date;

public class Client {
    private static final String LOG_FILE = "client_log.txt";
    private static PrintWriter logWriter;

    // Потоки вынесены на уровень класса, чтобы создать их один раз при подключении
    private static Socket socket = null;
    private static BufferedReader in = null;
    private static PrintWriter out = null;

    public static void main(String[] args) throws IOException {
        // Инициализация файла протокола событий
        logWriter = new PrintWriter(new FileWriter(LOG_FILE, true), true);
        log("Запуск клиента");

        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("=== TCP Клиент (Вариант 8) ===");
        System.out.println("Команды:");
        System.out.println("  connect <адрес> <порт> - подключиться к серверу");
        System.out.println("  exit                   - завершить работу");
        System.out.println("  (Примечание: отправка по Enter, т.к. стандартная консоль Java");
        System.out.println("   не перехватывает клавишу End без сторонних библиотек)");
        System.out.println("  Для завершения сеанса на сервере введите: ~#~");
        System.out.println();

        try {
            while (true) {
                System.out.print("> ");
                String fromUser = stdIn.readLine();

                if (fromUser == null || fromUser.equalsIgnoreCase("exit")) {
                    break;
                }

                // Обработка команды connect
                if (fromUser.startsWith("connect ")) {
                    String[] parts = fromUser.split("\\s+");
                    if (parts.length == 3) {
                        // Закрытие предыдущего соединения, если было (ИСПРАВЛЕНО: добавлены скобки)
                        if (socket != null && !socket.isClosed()) {
                            log("[" + new Date() + "] Соединение разорвано (переподключение)");
                            socket.close();
                        }
                        String host = parts[1];
                        int port = Integer.parseInt(parts[2]);
                        
                        // Создание нового сокета и потоков ОДИН РАЗ
                        socket = new Socket(InetAddress.getByName(host), port);
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                        
                        // Логирование п.1: время начала соединения
                        log("[" + new Date() + "] Соединение установлено с " + host + ":" + port);
                        System.out.println("Подключено к " + host + ":" + port);
                    } else {
                        System.out.println("Ошибка формата. Используйте: connect <адрес> <порт>");
                    }
                    continue;
                }

                if (socket == null || socket.isClosed()) {
                    System.out.println("Нет активного соединения. Введите: connect <адрес> <порт>");
                    continue;
                }

                // Отправка строки серверу (используем уже созданный поток out)
                out.println(fromUser);
                // Логирование п.2: передаваемая строка и время передачи
                log("[" + new Date() + "] Передано серверу: " + fromUser);
                System.out.println("Отправлено: " + fromUser);

                // Чтение ответа от сервера (используем уже созданный поток in)
                String response = in.readLine();
                if (response != null) {
                    System.out.println("Сервер: " + response);
                    // Логирование п.3: принимаемая строка и время приема
                    log("[" + new Date() + "] Принято от сервера: " + response);
                } else {
                    // Если сервер разорвал соединение
                    System.out.println("Соединение разорвано сервером");
                    log("[" + new Date() + "] Соединение разорвано сервером");
                    socket.close();
                    socket = null;
                }
            }
        } finally {
            // Логирование п.1: время окончания соединения
            if (socket != null && !socket.isClosed()) {
                log("[" + new Date() + "] Соединение разорвано (завершение работы)");
                socket.close();
            }
            log("Клиент завершил работу");
            logWriter.close();
            System.out.println("Соединение закрыто. Лог сохранен в " + LOG_FILE);
        }
    }

    private static void log(String message) {
        if (logWriter != null) {
            logWriter.println(message);
        }
    }
} ///:~
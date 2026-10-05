import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class Client {
    private static final String LOG_FILE = "client_log.txt";
    private static PrintWriter logWriter;
    private static Socket socket = null;
    private static BufferedReader in = null;
    private static PrintWriter out = null;

    public static void main(String[] args) throws IOException {
        logWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(LOG_FILE, true), StandardCharsets.UTF_8), true);
        log("Запуск клиента");

        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        System.out.println(" TCP Клиент ");
        System.out.println(" Команды: ");
        System.out.println("  connect <адрес> <порт> - подключиться к серверу");
        System.out.println("  exit                   - завершить работу");
        System.out.println("  (Для отправки добавьте символ '#' в конце строки и нажмите Enter.");
        System.out.println("  Для завершения сеанса на сервере введите: ~#~");
        System.out.println();

        try {
            while (true) {
                System.out.print("> ");
                String fromUser = stdIn.readLine().trim(); // .trim() убирает случайные пробелы

                if (fromUser == null || fromUser.equalsIgnoreCase("exit")) {
                    break;
                }

                // Обработка команды connect
                if (fromUser.startsWith("connect ")) {
                    String[] parts = fromUser.split("\\s+");
                    if (parts.length == 3) {
                        if (socket != null && !socket.isClosed()) {
                            log("[" + new Date() + "] Соединение разорвано (переподключение)");
                            socket.close();
                        }
                        String host = parts[1];
                        int port = Integer.parseInt(parts[2]);
                        
                        socket = new Socket(InetAddress.getByName(host), port);
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
                        
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

                if (fromUser.equals("~#~") || fromUser.endsWith("#")) {
                    // Если есть #, убираем ее перед отправкой
                    String messageToSend = fromUser.endsWith("#") ? fromUser.substring(0, fromUser.length() - 1) : fromUser;
                    
                    out.println(messageToSend);
                    log("[" + new Date() + "] Передано серверу: " + messageToSend);
                    System.out.println("Отправлено: " + messageToSend);
                } else {
                    System.out.println("[Ожидание] Добавьте символ '#' в конце строки для отправки.");
                    continue;
                }

                // Чтение ответа от сервера
                String response = in.readLine();
                if (response != null) {
                    System.out.println("Сервер: " + response);
                    log("[" + new Date() + "] Принято от сервера: " + response);
                } else {
                    System.out.println("Соединение разорвано сервером");
                    log("[" + new Date() + "] Соединение разорвано сервером");
                    socket.close();
                    socket = null;
                }
            }
        } finally {
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
}
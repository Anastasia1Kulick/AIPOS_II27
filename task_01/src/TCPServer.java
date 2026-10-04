import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Лабораторная работа №1. TCP-эхо-сервер.
 * Вариант 12: задание сервера №4.
 *
 * Сервер принимает поток символов от клиента (клиент - telnet) и возвращает его
 * обратно (эхо). После приёма каждой группы из 64 символов он формирует и отсылает
 * цепочку <'символ1'-количество1, 'символ2'-количество2, ...>, где "символ" -
 * встреченный в группе символ, "количество" - число его вхождений в группе.
 * Если в группе различных символов меньше 3, сервер отсылает сообщение и
 * разрывает соединение.
 *
 * Для одновременного обслуживания нескольких клиентов на каждого клиента
 * создаётся отдельный поток (класс ServeOne), как в примере MultiServer из методички.
 */
public class TCPServer {

    public static int PORT = 8080;
    static final int GROUP = 64;          // размер группы символов
    static final int MIN_DISTINCT = 3;    // минимум различных символов в группе

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            PORT = Integer.parseInt(args[0]);
        }
        ServerSocket s = new ServerSocket(PORT);
        log("Сервер запущен. Порт " + PORT);
        try {
            while (true) {
                // Останавливает выполнение, пока не придёт новое соединение.
                Socket socket = s.accept();
                try {
                    new ServeOne(socket);          // поток обслуживает этого клиента
                } catch (IOException e) {
                    // Если не удалось - закрываем сокет, иначе его закроет поток.
                    socket.close();
                }
            }
        } finally {
            s.close();
        }
    }

    static void log(String msg) {
        System.out.println(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + "  " + msg);
    }

    /** Поток, обслуживающий одного клиента. */
    static class ServeOne extends Thread {
        private final Socket socket;
        private final Reader in;
        private final Writer out;
        private final String client;

        ServeOne(Socket s) throws IOException {
            socket = s;
            client = s.getInetAddress().getHostAddress() + ":" + s.getPort();
            // Входной поток байт -> фильтр служебных команд telnet -> символы UTF-8.
            in = new InputStreamReader(new TelnetFilter(s.getInputStream()), StandardCharsets.UTF_8);
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            start();                               // вызывает run()
        }

        /** Отправка строки клиенту. telnet ожидает конец строки "\r\n". */
        private void send(String text) throws IOException {
            out.write(text);
            out.flush();                           // без сброса буфера данные не уйдут в сеть
        }

        @Override
        public void run() {
            log("Подключился клиент " + client);
            try {
                send("Hello, Student!\r\n");
                StringBuilder group = new StringBuilder();   // текущая группа символов
                int c, prev = 0;
                while ((c = in.read()) != -1) {
                    char ch = (char) c;
                    // Эхо: возвращаем клиенту всё, что он набрал (Enter -> перевод строки).
                    if (ch == '\r' || (ch == '\n' && prev != '\r')) {
                        send("\r\n");
                    } else if (ch != '\n' && ch != '\0') {
                        send(String.valueOf(ch));
                    }
                    prev = ch;
                    // Символы конца строки в группу не входят.
                    if (ch == '\r' || ch == '\n' || ch == '\0') continue;
                    group.append(ch);
                    if (group.length() < GROUP) continue;

                    // Набрана полная группа из 64 символов - считаем статистику.
                    Map<Character, Integer> counts = new LinkedHashMap<>();
                    for (int i = 0; i < group.length(); i++) {
                        counts.merge(group.charAt(i), 1, Integer::sum);
                    }
                    group.setLength(0);
                    if (counts.size() < MIN_DISTINCT) {
                        log("В группе " + counts.size() + " различных символов. Разрыв соединения с " + client);
                        send("\r\nВ группе из " + GROUP + " символов меньше " + MIN_DISTINCT
                                + " различных (найдено " + counts.size() + "). Соединение разорвано.\r\n");
                        break;                     // выходим из цикла -> finally закроет сокет
                    }
                    StringBuilder chain = new StringBuilder("<");
                    for (Map.Entry<Character, Integer> e : counts.entrySet()) {
                        if (chain.length() > 1) chain.append(", ");
                        chain.append('\'').append(e.getKey()).append("'-").append(e.getValue());
                    }
                    chain.append('>');
                    log("Группа от " + client + ": " + chain);
                    send("\r\n" + chain + "\r\n");
                }
            } catch (IOException e) {
                log("Ошибка связи с " + client + ": " + e.getMessage());
            } finally {
                // Сокет закрываем всегда, независимо от того, было исключение или нет.
                try {
                    socket.close();
                } catch (IOException e) {
                    log("Сокет не закрыт");
                }
                log("Соединение с " + client + " закрыто");
            }
        }
    }

    /**
     * Фильтр входного потока: вырезает служебные команды протокола telnet
     * (последовательности, начинающиеся с байта 255 = IAC), которые клиент
     * telnet.exe отправляет при подключении. Остальные байты пропускает как есть.
     */
    static class TelnetFilter extends InputStream {
        private final InputStream in;

        TelnetFilter(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b != 255) return b;                   // обычный байт
            int cmd = in.read();
            if (cmd == -1) return -1;
            if (cmd == 255) return 255;               // IAC IAC - настоящий байт 255
            if (cmd >= 251 && cmd <= 254) {           // WILL/WONT/DO/DONT + код опции
                in.read();
            } else if (cmd == 250) {                  // SB ... IAC SE (подопции)
                int prev = 0, x;
                while ((x = in.read()) != -1) {
                    if (prev == 255 && x == 240) break;
                    prev = x;
                }
            }
            return read();                            // идём дальше к следующему байту
        }

        /** Не ждём заполнения всего буфера: возвращаем то, что уже пришло. */
        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int first = read();
            if (first == -1) return -1;
            buf[off] = (byte) first;
            int n = 1;
            while (n < len && in.available() > 0) {
                int x = read();
                if (x == -1) break;
                buf[off + n++] = (byte) x;
            }
            return n;
        }
    }
}

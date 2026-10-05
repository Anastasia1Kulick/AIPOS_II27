import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Лабораторная работа №3. UDP-сервер.
 * Вариант 12: задание сервера №4.
 *
 * Сервер накапливает символы, присланные клиентом. После приёма каждой группы
 * из 64 символов он формирует и отсылает клиенту цепочку
 * <'символ1'-количество1, 'символ2'-количество2, ...>, где "символ" - встреченный
 * в группе символ, а "количество" - сколько раз он встретился в этой группе.
 * Если в очередной группе различных символов меньше 3, сервер отсылает
 * соответствующее сообщение и разрывает "соединение".
 *
 * UDP не имеет соединений, поэтому "соединение" эмулируется таблицей клиентов
 * (ключ "адрес:порт", значение - буфер ещё не обработанных символов).
 */
public class UDPServerThread {

    /** Порт сервера (по заданию лабораторной работы - 666). */
    public static int PORT = 666;

    /** Размер группы символов, по которой строится статистика. */
    static final int GROUP = 64;

    /** Минимально допустимое число различных символов в группе. */
    static final int MIN_DISTINCT = 3;

    /** Метка в начале ответа: сервер закрыл "соединение" с клиентом. */
    static final String BYE = "[BYE]";

    /** Клиенты, с которыми "установлено соединение": "адрес:порт" -> буфер символов. */
    static final Map<String, StringBuilder> sessions = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            PORT = Integer.parseInt(args[0]);
        }
        DatagramSocket socket = new DatagramSocket(PORT);
        log("Сервер запущен. Порт " + PORT);

        while (true) {
            try {
                // Буфер под входящую датаграмму.
                byte[] buf = new byte[2048];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                // Блокируемся, пока не придёт датаграмма. Пакет "помнит",
                // откуда пришёл: адрес и порт клиента лежат внутри него.
                socket.receive(packet);

                // Берём только реально принятые байты (packet.getLength()),
                // иначе в строку попадёт хвост из нулей буфера.
                String received = new String(packet.getData(), 0,
                        packet.getLength(), StandardCharsets.UTF_8);

                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                String client = address.getHostAddress() + ":" + port;
                log("<- " + client + " : " + received);

                handle(socket, client, address, port, received);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** Разбор одной принятой датаграммы. */
    static void handle(DatagramSocket socket, String client,
                       InetAddress address, int port, String text) throws IOException {
        // 1. Служебное: клиент подключается.
        if (text.equals("connect")) {
            sessions.put(client, new StringBuilder());
            log("Соединение установлено: " + client);
            send(socket, address, port, "Hello, Student! Присылайте текст: статистика отправляется после каждых "
                    + GROUP + " символов.");
            return;
        }
        // 2. Служебное: клиент разорвал соединение командой disconnect - ответа нет.
        if (text.equals("END")) {
            sessions.remove(client);
            log("Соединение закрыто клиентом: " + client);
            return;
        }
        // 3. Данные принимаются только после connect.
        StringBuilder buffer = sessions.get(client);
        if (buffer == null) {
            send(socket, address, port, BYE + " Соединение не установлено. Выполните connect.");
            return;
        }
        // 4. Накапливаем символы и обрабатываем каждую полную группу из 64 символов.
        buffer.append(text);
        while (buffer.codePointCount(0, buffer.length()) >= GROUP) {
            int end = buffer.offsetByCodePoints(0, GROUP);
            String group = buffer.substring(0, end);
            buffer.delete(0, end);

            // Считаем, сколько раз встретился каждый символ (порядок - по первому появлению).
            Map<String, Integer> counts = new LinkedHashMap<>();
            group.codePoints().forEach(cp ->
                    counts.merge(new String(Character.toChars(cp)), 1, Integer::sum));

            if (counts.size() < MIN_DISTINCT) {
                // Разных символов меньше 3: сообщаем и разрываем соединение.
                sessions.remove(client);
                log("В группе только " + counts.size() + " различных символов. Соединение с "
                        + client + " разорвано");
                send(socket, address, port, BYE + " В группе из " + GROUP + " символов меньше "
                        + MIN_DISTINCT + " различных (найдено " + counts.size()
                        + "). Соединение разорвано.");
                return;
            }
            // Формируем цепочку <'a'-5, 'b'-3, ...>.
            StringBuilder chain = new StringBuilder("<");
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (chain.length() > 1) chain.append(", ");
                chain.append('\'').append(e.getKey()).append("'-").append(e.getValue());
            }
            chain.append('>');
            send(socket, address, port, chain.toString());
        }
        // 5. Подтверждение приёма неполной группы (чтобы клиент видел, что сервер жив).
        int left = buffer.codePointCount(0, buffer.length());
        if (left > 0) {
            send(socket, address, port, "Принято. В буфере " + left + " из " + GROUP + " символов.");
        }
    }

    /** Отправка строки клиенту в виде датаграммы на его адрес и порт. */
    static void send(DatagramSocket socket, InetAddress address, int port, String text)
            throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        DatagramPacket reply = new DatagramPacket(data, data.length, address, port);
        socket.send(reply);
        log("-> " + address.getHostAddress() + ":" + port + " : " + text);
    }

    static void log(String msg) {
        System.out.println(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + "  " + msg);
    }
}

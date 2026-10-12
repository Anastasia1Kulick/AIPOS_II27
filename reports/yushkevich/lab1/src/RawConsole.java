import java.io.*;

/**
 * Обёртка над стандартным вводом System.in, работающая в raw-режиме терминала.
 * Зачем нужен этот класс.
 * System.in читает поток байт, а PgDn посылает escape-последовательность ^[[5~.
 * Чтобы её поймать, нужно перевести терминал в raw-режим и парсить байты вручную
 *
 * В raw-режиме:
 *   - каждый нажатый символ сразу передаётся приложению (без ожидания Enter);
 *   - специальные клавиши больше не обрабатываются
 *   - автоматическое эхо отключено, поэтому каждый введённый символ надо
 *     печатать вручную.
 *
 * Именно это и делает класс RawConsole: переводит терминал в raw-режим
 * через утилиту stty, читает байты из System.in, распознаёт PgDn и
 * возвращает накопленную строку. При закрытии восстанавливает исходные
 * настройки терминала.
 */

public class RawConsole implements Closeable {
    // Проверка ОС: stty доступна только на Unix-подобных системах (Linux, macOS, BSD). 
    // На Windows класс работать не будет
    private static final boolean IS_UNIX = System.getProperty("os.name").toLowerCase().matches(".*nux|nix|mac.*");
    // Стандартный ввод — источник байтов от клавиатуры
    private final InputStream in = System.in;
    // Стандартный вывод — используется для ручного эха вводимых символов
    private final OutputStream out = System.out;
    // Буфер текущей набираемой строки.
    // Символы накапливаются здесь до тех пор, пока пользователь не нажмёт PgDn
    private final StringBuilder buffer = new StringBuilder();
    // Сохранённые настройки терминала.
    // Нужны для восстановления исходного состояния при закрытии
    private String savedStty;

    public RawConsole () {
        if (IS_UNIX) {
            try {
                // для восстановления исходного состояния
                savedStty = exec("stty -g").trim();
                // Включаем raw-режим и отключаем автоэхо
                exec("stty raw -echo");
            } catch (IOException e) {
                System.err.println("Can't set raw mode: " + e.getMessage());
            }
        }
    }
    // Запускает shell-команду и возвращает её stdout
    private static String exec (String cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
        // Redirect.INHERIT передаёт дочернему процессу тот же самый stdin, что и у Java-процесса
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        // Читаем весь stdout команды в строку.
        StringBuilder sb = new StringBuilder();

        // Читаем весь stdout команды в строку
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;

            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        }
        // Ждём, чтобы к моменту возврата stty гарантированно применила настройки
        try {
            p.waitFor();
        } catch (InterruptedException ignored) {}
        return sb.toString();
    }

    public String readLine() throws IOException {
        buffer.setLength(0);
        int b;
        // in.read() возвращает -1 при EOF
        while ((b = in.read()) != -1) {
            // Обработка escape-последовательности PgDn
            if (b == 0x1b) {
                if (in.read() == '[' && in.read() == '5' && in.read() == '~') {
                    // переводим курсор на новую строку и возвращаем накопленный буфер
                    out.write('\n');
                    out.flush();
                    return buffer.toString();
                }
                continue;
            }
            // Обработка Enter
            if (b == '\r' || b == '\n') {
                
                out.write('\n');
                out.flush();
                return buffer.toString();
            }
            // Добавляем символ в буфер и эхоим его вручную
            if (b >= 0x20) {
                buffer.append((char) b);
                out.write(b);
                out.flush();
            }
        }
        return null;
    }
     @Override 
        public void close() {
            if (IS_UNIX && savedStty != null) {
                try {
                    // возвращает терминал в то состояние, которое было до включения raw-режима
                    exec("stty " + savedStty);
                } catch (IOException e) {
                    System.err.println("Can't restore terminal: " + e.getMessage());
                }
            }
        }
}
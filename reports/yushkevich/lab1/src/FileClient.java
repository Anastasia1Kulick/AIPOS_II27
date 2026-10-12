import java.io.*;
import java.net.*;
import java.util.logging.*;

/*
 * TCP-клиент для взаимодействия с FileServer.
 *
 * Функциональность согласно варианту задания:
 *   1) автоматическое подключение к серверу по адресу по умолчанию
 *      при запуске клиента;
 *   2) отсылка введённой строки серверу по нажатию клавиши PgDn
 *      (реализовано во вспомогательном классе RawConsole);
 *   3) ведение файла протокола событий server.log:
 *        - время начала и окончания соединения;
 *        - передаваемую серверу строку и время передачи строки;
 *        - принимаемую от сервера строку и время приема строки
 */

public class FileClient {
    // Адрес сервера по умолчанию
    public static final String DEFAULT_HOST = "127.0.0.1";
    // Порт по умолчанию
    public static final int DEFAULT_PORT = FileServer.PORT;
    // Логгер класса. Записи попадают в server.log и в консоль
    private static final Logger logger = Logger.getLogger(FileClient.class.getName());

        public static void main (String[] args) throws IOException{
        // Oпределение адреса и порта сервера
        // Если пользователь передал аргументы командной строки используем их
        // иначе берём значения по умолчанию 
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        // Установка соединения с сервером
        InetAddress addr = InetAddress.getByName(host);
        logger.info("Connect to " + addr + ":" + port);

        Socket socket = new Socket(addr, port);
        logger.info("Connected: " + socket);

        try (RawConsole console = new RawConsole()){
            // Создание потоков ввода/вывода поверх сокета
            // Байтовый InputStream сокета оборачивается в символьный Reader
            // через InputStreamReader, а затем в BufferedReader для
            // построчного чтения
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            // Поток-читатель ответов сервера
            Thread readerThread = new Thread(() -> {
                try {
                    String response;
                    // readLine() возвращает null, когда сервер закрыл соединение
                    while ((response = in.readLine()) != null) {
                        // \r\n — обязательно иначе терминал не будет возвращать каретку
                        System.out.print("[SERVER]" + response + "\r\n");
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Disconnected", e);
                }
            });
            readerThread.start();

            System.out.print("Enter command (example: load fname.txt)\r\n");
            System.out.print("Enter 'exit' to exit\r\n");
            // console.readLine() работает в raw-режиме и возвращает строку по нажатию PgDn
            String line;
            while ((line = console.readLine()) != null) {
                // Локальная команда выхода из клиента.
                if ("exit".equalsIgnoreCase(line)) break;
                out.println(line);
            }
        } finally {
            // finally выполняется всегда: и при нормальном выходе,
            // и при исключении. Закрытие сокета освобождает
            // сетевые ресурсы
            logger.info("Close connection");
            socket.close();
        }
    }
}
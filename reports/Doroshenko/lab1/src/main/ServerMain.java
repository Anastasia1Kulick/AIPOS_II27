package main;

import interfaces.ConnectionHandler;
import server.TcpServer;

/**
 * Точка входа приложения.
 * Реализует интерфейс ConnectionHandler, чтобы получать события
 * от сервера и выводить их в консоль.
 */
public class ServerMain implements ConnectionHandler {

    public static void main(String[] args) {
        // Порт по умолчанию.
        int port = 8080;

        // Если передан аргумент — используем его как порт.
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат порта. Используется 8080.");
            }
        }

        // Создаём обработчик событий и сервер.
        ServerMain app = new ServerMain();
        TcpServer server = new TcpServer(port, app);

        // Запускаем сервер в отдельном потоке, чтобы main не блокировался.
        Thread serverThread = new Thread(server::start);
        serverThread.start();

        System.out.println("Для остановки сервера нажмите Ctrl+C");
    }

    @Override
    public void onConnect(String clientAddress) {
        System.out.println(">>> Клиент подключился: " + clientAddress);
    }

    @Override
    public void onDisconnect(String clientAddress) {
        System.out.println(">>> Клиент отключился: " + clientAddress);
    }

    @Override
    public void onReceive(String clientAddress, String message) {
        // Дополнительная обработка входящих сообщений.
    }
}
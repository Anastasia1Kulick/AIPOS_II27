package server;

import interfaces.ConnectionHandler;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Основной класс TCP-сервера.
 * Задача: слушать входящие подключения и для каждого клиента
 * запускать отдельный поток обработки (ClientSession).
 */
public class TcpServer {

    /** Порт, на котором сервер принимает подключения. */
    private final int port;

    /** Обработчик событий (реализация интерфейса ConnectionHandler). */
    private final ConnectionHandler handler;

    /** Сокет сервера, принимающий входящие соединения. */
    private ServerSocket serverSocket;

    /**
     * Пул потоков для параллельной обработки клиентов.
     * newCachedThreadPool() создаёт новый поток при необходимости
     * и переиспользует уже существующие — это оптимально для сервера,
     * обслуживающего произвольное число клиентов.
     */
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    /** Флаг работы сервера. volatile — для корректной видимости между потоками. */
    private volatile boolean running = false;

    public TcpServer(int port, ConnectionHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    /**
     * Запускает сервер: открывает ServerSocket и в бесконечном цикле
     * принимает подключения, передавая каждое в отдельный поток.
     */
    public void start() {
        try {
            // Открытие слушающего сокета. Если порт занят — будет исключение.
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Сервер запущен на порту " + port);

            // Основной цикл приёма подключений.
            while (running) {
                // accept() блокируется, пока не придёт клиент.
                Socket clientSocket = serverSocket.accept();

                // Каждое соединение обрабатывается в отдельном потоке пула.
                threadPool.execute(new ClientSession(clientSocket, handler));
            }
        } catch (IOException e) {
            // Если исключение возникло не из-за остановки сервера — логируем.
            if (running) {
                System.err.println("Ошибка сервера: " + e.getMessage());
            }
        }
    }

    /**
     * Корректно останавливает сервер: закрывает ServerSocket
     * и завершает пул потоков.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            threadPool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
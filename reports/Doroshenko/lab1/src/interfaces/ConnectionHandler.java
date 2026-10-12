package interfaces;

/**
 * Интерфейс для обработки событий жизненного цикла TCP-соединения.
 * Позволяет отделить логику сервера от логики обработки уведомлений
 * (логирование, статистика, UI и т.д.).
 */
public interface ConnectionHandler {

    /**
     * Вызывается при подключении нового клиента.
     * @param clientAddress строковое представление адреса клиента (IP:порт)
     */
    void onConnect(String clientAddress);

    /**
     * Вызывается при отключении клиента (штатном или аварийном).
     * @param clientAddress строковое представление адреса клиента
     */
    void onDisconnect(String clientAddress);

    /**
     * Вызывается при получении сервером очередной порции данных.
     * @param clientAddress адрес клиента
     * @param message       полученные данные (в нашем случае — группа из 64 символов)
     */
    void onReceive(String clientAddress, String message);
}
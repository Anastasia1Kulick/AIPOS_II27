import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;

public class TaskServer {
    private static final int PORT = 666;
    private static final Logger logger = Logger.getLogger(TaskServer.class.getName());

    public static void main(String[] args) {
        setupLogger("server_log.txt");
        logger.info("Сервер запущен и ожидает подключений на порту " + PORT);
        System.out.println("Сервер запущен на порту " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Новое подключение от: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка сервера", e);
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            int groupCount = 0;      
            int groupChecksum = 0;   
            int totalCount = 0;      
            StringBuilder recent = new StringBuilder(); 

            try (Reader in = new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                int c;
                while ((c = in.read()) != -1) {
                    char ch = (char) c;
                    totalCount++;
                    groupCount++;
                    groupChecksum += ch; 

                    recent.append(ch);
                    if (recent.length() > 3) {
                        recent.delete(0, recent.length() - 3);
                    }

                    if (recent.toString().contains("~#~")) {
                        String endMsg = "SESSION_END: Обнаружена последовательность ~#~. Сеанс завершен.";
                        out.println(endMsg);
                        logger.info("Сеанс завершен маркером ~#~. Всего символов: " + totalCount);
                        break;
                    }

                    if (groupCount == 48) {
                        String ackMsg = "ACK: Checksum=" + groupChecksum + ", TotalReceived=" + totalCount;
                        out.println(ackMsg);
                        logger.info("Отправлено подтверждение: " + ackMsg);
                        groupCount = 0;
                        groupChecksum = 0;
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Клиент отключился или ошибка чтения", e);
            } finally {
                try { socket.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    private static void setupLogger(String fileName) {
    try {
        FileHandler fh = new FileHandler(fileName, true);
        fh.setEncoding("UTF-8"); 
        fh.setFormatter(new SimpleFormatter());
        logger.addHandler(fh);
        logger.setUseParentHandlers(false);
    } catch (IOException e) {
        e.printStackTrace();
    }
  }
}

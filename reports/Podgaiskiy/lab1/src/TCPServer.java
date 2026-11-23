import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TCPServer {
    static final int PORT = 666;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("TCP-сервер запущен и ожидает подключений на порту " + PORT + "...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Клиент подключился: " + clientSocket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Ошибка запуска сервера: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Ошибка создания потоков: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Получено от клиента: " + inputLine);
                
                if (inputLine.startsWith("find ")) {
                    String[] parts = inputLine.split(" ", 3);
                    if (parts.length < 3) {
                        out.println("Ошибка: используйте формат: find <строка> <имя файла>");
                        continue;
                    }
                    String searchString = parts[1];
                    String filename = parts[2];
                    File file = new File(filename);

                    if (!file.exists() || !file.isFile()) {
                        out.println("Файл не найден: " + filename);
                        out.println("Сеанс окончен.");
                        break;
                    }

                    int count = 0;
                    try (BufferedReader fileReader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = fileReader.readLine()) != null) {
                            if (line.contains(searchString)) {
                                count++;
                            }
                        }
                    }

                    if (count > 0) {
                        out.println("Найдено строк: " + count + " (поиск: '" + searchString + "' в файле '" + filename + "')");
                    } else {
                        out.println("Строка не найдена в файле. Сеанс окончен.");
                        break;
                    }
                } else {
                    out.println("Неизвестная команда. Доступна команда: find <строка> <имя файла>");
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода: " + e.getMessage());
        } finally {
            try {
                socket.close();
                System.out.println("Соединение с клиентом закрыто.");
            } catch (IOException e) {
                System.err.println("Ошибка при закрытии сокета: " + e.getMessage());
            }
        }
    }
}
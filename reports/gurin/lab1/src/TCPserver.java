import java.io.*;
import java.net.*;

public class TCPserver {
    public static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client: " + clientSocket);
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } finally {
            serverSocket.close();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

    @Override
    public void run() {
        try {
            int totalChars = 0;
            char[] buffer = new char[48];
            int bufIndex = 0;
            int ch;
            while ((ch = in.read()) != -1) {
                char c = (char) ch;
                // Игнорируем переводы строк — они не являются частью передаваемых данных
                if (c == '\n' || c == '\r') continue;

                // Проверка на завершающий символ '#'
                if (c == '#') {
                    out.println("Session ended by server.");
                    break;
                }

                buffer[bufIndex++] = c;
                totalChars++;

                if (bufIndex == 48) {
                    int checksum = 0;
                    for (char x : buffer) checksum += (int) x;
                    out.println("Checksum: " + checksum + ", Total chars: " + totalChars);
                    bufIndex = 0; // сброс буфера для следующей группы
                }
            }
        } catch (IOException e) {
            System.err.println("IO error: " + e);
        } finally {
            try { socket.close(); } catch (IOException e) { /* ignore */ }
            System.out.println("Client disconnected.");
        }
    }
}



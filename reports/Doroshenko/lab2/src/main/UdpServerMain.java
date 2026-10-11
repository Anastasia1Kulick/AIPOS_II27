package main;

import interfaces.ConnectionHandler;
import udp.UdpServer;

public class UdpServerMain implements ConnectionHandler {

    public static void main(String[] args) {
        int port = 666;

        UdpServerMain app = new UdpServerMain();
        UdpServer server = new UdpServer(port, app);

        Thread serverThread = new Thread(server::start);
        serverThread.start();

        System.out.println("Для остановки сервера нажмите Ctrl+C");
    }

    @Override
    public void onConnect(String clientAddress) {
        System.out.println(">>> Сессия начата: " + clientAddress);
    }

    @Override
    public void onDisconnect(String clientAddress) {
        System.out.println(">>> Сессия завершена: " + clientAddress);
    }

    @Override
    public void onReceive(String clientAddress, String message) {
    
    }
    
}
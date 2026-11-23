package main;

import udp.UdpClient;

public class UdpClientMain {
    public static void main(String[] args) {
        try {
            new UdpClient().start();
        } catch (RuntimeException e) {
            System.err.println("Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
}
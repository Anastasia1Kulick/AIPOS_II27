package main;

import client.TcpClient;

/**
 * Точка входа клиентского приложения.
 * Отдельный файл Main для клиента согласно требованиям лабораторной работы.
 */
public class ClientMain {
    public static void main(String[] args) {
        try {
            new TcpClient().start();
        } catch (RuntimeException e) {
            System.err.println("Критическая ошибка клиента: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
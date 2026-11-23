package interfaces;

public interface ConnectionHandler {

    void onConnect(String clientAddress);
    void onDisconnect(String clientAddress);
    void onReceive(String clientAddress, String message);
    
}
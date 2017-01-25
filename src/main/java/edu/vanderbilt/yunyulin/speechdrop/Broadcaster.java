package edu.vanderbilt.yunyulin.speechdrop;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import edu.vanderbilt.yunyulin.speechdrop.room.Room;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.getLogger;

public class Broadcaster {
    private final SocketIOServer server;
    private final RoomHandler roomHandler;

    public Broadcaster(String hostname, int port, RoomHandler roomHandler) {
        this.roomHandler = roomHandler;
        Configuration config = new Configuration();
        config.setHostname(hostname);
        config.setPort(port);
        config.getSocketConfig().setReuseAddress(true);

        server = new SocketIOServer(config);
    }

    public void start() {
        server.addEventListener("join", String.class,
                (client, roomName, ackRequest) -> {
                    if (roomHandler.roomExists(roomName)) {
                        client.joinRoom(roomName);
                    }
                });
        server.addEventListener("leave", String.class,
                (client, roomName, ackRequest) -> client.leaveRoom(roomName));
        server.startAsync();
    }

    public void stop() {
        server.stop();
    }

    public void publishUpdate(String room, String data) {
        server.getRoomOperations(room).sendEvent("update", data);
    }
}

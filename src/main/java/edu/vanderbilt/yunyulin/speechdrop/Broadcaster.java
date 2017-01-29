package edu.vanderbilt.yunyulin.speechdrop;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;

public class Broadcaster {
    private final SocketIOServer server;
    private final RoomHandler roomHandler;

    private static final String joinEvent = "join";
    private static final String updateEvent = "update";
    private static final String leaveEvent = "leave";

    public Broadcaster(String hostname, int port, RoomHandler roomHandler) {
        this.roomHandler = roomHandler;
        Configuration config = new Configuration();
        config.setHostname(hostname);
        config.setPort(port);
        config.getSocketConfig().setReuseAddress(true);

        server = new SocketIOServer(config);
    }

    public void start() {
        server.addEventListener(joinEvent, String.class,
                (client, roomName, ackRequest) -> {
                    if (roomHandler.roomExists(roomName)) {
                        client.joinRoom(roomName);
                        client.sendEvent(updateEvent, roomHandler.getRoom(roomName).getIndex());
                    }
                });
        server.addEventListener(leaveEvent, String.class,
                (client, roomName, ackRequest) -> client.leaveRoom(roomName));
        server.startAsync();
    }

    public void stop() {
        server.stop();
    }

    public void publishUpdate(String room, String data) {
        server.getRoomOperations(room).sendEvent(updateEvent, data);
    }
}

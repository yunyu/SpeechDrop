package edu.vanderbilt.yunyulin.speechdrop;

import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.LOGGER;

public class Broadcaster {
    private final Vertx vertx;
    private final SockJSHandler sockJSHandler;

    private static final String ADDR_PREFIX = "speechdrop.room.";

    public Broadcaster(Vertx vertx, RoomHandler roomHandler) {
        this.vertx = vertx;
        this.sockJSHandler = SockJSHandler.create(vertx);
        sockJSHandler.bridge(new SockJSBridgeOptions().addOutboundPermitted(
                new PermittedOptions().setAddressRegex("speechdrop\\.room\\..+")
        ), be -> {
            if (be.type() == BridgeEventType.REGISTER) {
                String address = be.getRawMessage().getString("address");
                String roomId = getRoomId(address);
                if (roomId != null && roomHandler.roomExists(roomId)) {
                    roomHandler.getRoom(roomId).getIndex().onComplete(ar -> {
                        if (ar.succeeded()) {
                            // Copies envelope structure from EventBusBridgeImpl##deliverMessage
                            be.socket().write(Buffer.buffer(new JsonObject()
                                    .put("type", "rec")
                                    .put("address", address)
                                    .put("body", ar.result())
                                    .encode()
                            ));
                            be.complete(true);
                        } else {
                            LOGGER.error("Failed to deliver initial index for room " + roomId, ar.cause());
                            be.complete(false);
                        }
                    });
                    return;
                } else {
                    be.complete(false);
                    return;
                }
            } else if (be.type() == BridgeEventType.SOCKET_CREATED) {
                be.socket().exceptionHandler(t -> {
                    if (t instanceof VertxException && "Connection was closed".equals(t.getMessage())) {
                        return;
                    }
                    LOGGER.error("Unhandled exception on SockJS socket", t);
                });
            }
            be.complete(true);
        });
    }

    private String getRoomId(String address) {
        if (address.startsWith(ADDR_PREFIX)) {
            return address.substring(ADDR_PREFIX.length());
        } else {
            return null;
        }
    }

    public SockJSHandler getSockJSHandler() {
        return sockJSHandler;
    }

    public void publishUpdate(String room, String data) {
        vertx.eventBus().publish(ADDR_PREFIX + room, data);
    }
}

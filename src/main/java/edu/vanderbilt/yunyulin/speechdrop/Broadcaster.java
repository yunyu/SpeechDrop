package edu.vanderbilt.yunyulin.speechdrop;

import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.BridgeEventType;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

public class Broadcaster {
    private final Vertx vertx;
    private final SockJSHandler sockJSHandler;

    private static final String ADDR_PREFIX = "speechdrop.room.";

    public Broadcaster(Vertx vertx, RoomHandler roomHandler) {
        this.vertx = vertx;
        this.sockJSHandler = SockJSHandler.create(vertx);
        sockJSHandler.bridge(new BridgeOptions().addOutboundPermitted(
                new PermittedOptions().setAddressRegex("speechdrop\\.room\\..+")
        ), be -> {
            if (be.type() == BridgeEventType.REGISTER) {
                String address = be.getRawMessage().getString("address");
                String roomId = getRoomId(address);
                if (roomId != null && roomHandler.roomExists(roomId)) {
                    roomHandler.getRoom(roomId).getIndex().setHandler(ar -> {
                        // Copies envelope structure from EventBusBridgeImpl##deliverMessage
                        be.socket().write(Buffer.buffer(new JsonObject()
                                .put("type", "rec")
                                .put("address", address)
                                .put("body", ar.result())
                                .encode()
                        ));
                    });
                } else {
                    be.complete(false);
                    return;
                }
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

    public void mount(Router router) {
        router.route("/sock/*").handler(sockJSHandler);
    }

    public void publishUpdate(String room, String data) {
        vertx.setTimer(50, id -> vertx.eventBus().publish(ADDR_PREFIX + room, data));
    }
}

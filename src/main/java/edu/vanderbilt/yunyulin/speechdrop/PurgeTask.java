package edu.vanderbilt.yunyulin.speechdrop;

import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.logger;

@AllArgsConstructor
public class PurgeTask {
    private final RoomHandler roomHandler;
    private final Vertx vertx;

    public void schedule() {
        Handler<Long> runPurge = id -> {
            List<String> toRemove = new ArrayList<>();
            roomHandler.getDataStore().forEach((k, v) -> {
                // 10 min deletion
                if (System.currentTimeMillis() - v.ctime > SpeechDropVerticle.PURGE_INTERVAL * 1000) { // Unix time, so no zoned stuff
                    toRemove.add(k);
                }
            });
            toRemove.forEach(roomHandler::deleteRoom);
            logger().info("Purged " + toRemove.size() + " rooms");
        };
        vertx.setPeriodic(3 * 60 * 60 * 1000, runPurge);
        runPurge.handle(0L);
    }
}

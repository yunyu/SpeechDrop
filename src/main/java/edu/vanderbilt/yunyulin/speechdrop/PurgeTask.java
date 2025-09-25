package edu.vanderbilt.yunyulin.speechdrop;

import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.LOGGER;

@AllArgsConstructor
public class PurgeTask {
    private final RoomHandler roomHandler;
    private final Vertx vertx;
    private final int purgeIntervalInSeconds;

    public void schedule() {
        Handler<Long> runPurge = id -> {
            List<String> toRemove = roomHandler.getDataStore().entrySet().stream()
                    .filter(el -> System.currentTimeMillis() - el.getValue().ctime > purgeIntervalInSeconds * 1000)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            Future.all(
                            toRemove.stream()
                                    .map(roomHandler::queueRoomDeletion)
                                    .collect(Collectors.toList()))
                    .onComplete(res -> {
                        roomHandler.writeRooms();
                        LOGGER.info("Purged " + toRemove.size() + " rooms");
                    });
        };
        vertx.setPeriodic(3 * 60 * 60 * 1000, runPurge);
        runPurge.handle(0L);
    }
}

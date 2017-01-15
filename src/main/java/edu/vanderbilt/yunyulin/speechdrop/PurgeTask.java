package edu.vanderbilt.yunyulin.speechdrop;

import com.google.common.util.concurrent.MoreExecutors;
import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.getLogger;

public class PurgeTask {
    public static final ScheduledExecutorService ses =
            MoreExecutors.getExitingScheduledExecutorService(
                    (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1)
            );
    private final RoomHandler roomHandler;

    public PurgeTask(RoomHandler roomHandler) {
        this.roomHandler = roomHandler;
    }

    public void start() {
        ses.scheduleAtFixedRate(() -> {
            List<String> toRemove = new ArrayList<>();
            roomHandler.getDataStore().forEach((k, v) -> {
                // 10 min deletion
                if (System.currentTimeMillis() - v.ctime > Bootstrap.TWO_MONTHS * 1000) { // Unix time, so no zoned stuff
                    toRemove.add(k);
                }
            });
            toRemove.forEach(roomHandler::deleteRoom);
            getLogger().info("Purged " + toRemove.size() + " rooms");
        }, 0, 3, TimeUnit.HOURS);
    }
}

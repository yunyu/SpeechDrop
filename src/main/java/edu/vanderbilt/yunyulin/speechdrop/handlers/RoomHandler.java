package edu.vanderbilt.yunyulin.speechdrop.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication;
import edu.vanderbilt.yunyulin.speechdrop.room.Room;
import edu.vanderbilt.yunyulin.speechdrop.room.RoomData;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.LOGGER;

public class RoomHandler {
    private static final char[] allowedChars =
            "abcdefgh  k mnop rst  wx zABCDEFGH JKLMN PQR T  WXY 34 6 89".replaceAll(" ", "").toCharArray();
    private static final Random rand = new SecureRandom();
    private static final ObjectMapper mapper = new ObjectMapper();

    @Getter
    private Map<String, RoomData> dataStore;
    private File roomsFile;

    private final Vertx vertx;
    private final LoadingCache<String, Room> roomCache;

    public RoomHandler(Vertx vertx) {
        this.vertx = vertx;
        roomsFile = new File("rooms.json");
        if (!roomsFile.exists()) {
            dataStore = new HashMap<>();
        } else {
            try {
                dataStore = mapper.readValue(vertx.fileSystem().readFileBlocking(roomsFile.getPath()).toString(),
                        mapper.getTypeFactory().constructMapType(HashMap.class, String.class, RoomData.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        roomCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build(new CacheLoader<String, Room>() {
                    @Override
                    public Room load(String key) {
                        return new Room(vertx, key, dataStore.get(key));
                    }
                });
    }

    private String nextSessionId() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            char selectedChar = allowedChars[rand.nextInt(allowedChars.length)];
            stringBuilder.append(selectedChar);
        }
        return stringBuilder.toString();
    }

    public boolean roomExists(String id) {
        return dataStore.containsKey(id);
    }

    public Room makeRoom(String name) {
        String newId = null;
        while (newId == null || roomExists(newId)) {
            newId = nextSessionId();
        }
        dataStore.put(newId, new RoomData(name, System.currentTimeMillis()));
        LOGGER.info("[" + newId + "] Created room with name " + name);
        writeRooms();
        return getRoom(newId);
    }

    public Room getRoom(String id) {
        return roomCache.getUnchecked(id);
    }

    public void writeRooms() {
        try {
            vertx.fileSystem()
                    .writeFile(roomsFile.getPath(), Buffer.buffer(mapper.writeValueAsString(dataStore)))
                    .onFailure(err -> LOGGER.error("Failed to persist rooms metadata", err));
        } catch (JsonProcessingException e) { // This should never happen
            e.printStackTrace();
        }
    }

    public Future<Void> queueRoomDeletion(String id) {
        Promise<Void> promise = Promise.promise();
        if (dataStore.remove(id) != null) {
            roomCache.invalidate(id);
            File toDelete = new File(SpeechDropApplication.BASE_PATH, id);
            vertx.fileSystem().deleteRecursive(toDelete.getPath(), true).onComplete(ar -> {
                if (ar.succeeded()) {
                    promise.complete();
                } else {
                    promise.fail(ar.cause());
                }
            });
        } else {
            promise.complete();
        }
        return promise.future();
    }
}

package edu.vanderbilt.yunyulin.speechdrop.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.MoreExecutors;
import edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication;
import edu.vanderbilt.yunyulin.speechdrop.Util;
import edu.vanderbilt.yunyulin.speechdrop.room.Room;
import edu.vanderbilt.yunyulin.speechdrop.room.RoomData;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.getLogger;

public class RoomHandler {
    private static final char[] allowedChars =
            "abcdefgh  k mnop rst  wx zABCDEFGH JKLMN PQR T  WXY 34 6 89".replaceAll(" ", "").toCharArray();
    private static final Random rand = new SecureRandom();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ExecutorService saveThread =
            MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) Executors.newFixedThreadPool(1));
    private static final ExecutorService deletePool =
            MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) Executors.newFixedThreadPool(4));

    @Getter
    private Map<String, RoomData> dataStore;
    private File roomsFile;

    public RoomHandler() {
        roomsFile = new File("rooms.json");
        if (!roomsFile.exists()) {
            dataStore = new ConcurrentHashMap<>();
        } else {
            try {
                dataStore = mapper.readValue(com.google.common.io.Files.toString(roomsFile, Charsets.UTF_8),
                        mapper.getTypeFactory().constructMapType(ConcurrentHashMap.class, String.class, RoomData.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String nextSessionId() {
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
        getLogger().info("[" + newId + "] Created room with name " + name);
        dispatchSave();
        return getRoom(newId);
    }

    public Room getRoom(String id) {
        return new Room(id, dataStore.get(id));
    }

    public void dispatchSave() {
        saveThread.execute(() -> {
            try {
                mapper.writeValue(roomsFile, dataStore);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public boolean deleteRoom(String id) {
        if (!roomExists(id)) {
            return false;
        }
        deletePool.execute(() -> {
            try {
                Path toDelete = new File(SpeechDropApplication.BASE_PATH, id).toPath();
                Util.deleteFolderRecursive(toDelete);
                dataStore.remove(id);
                dispatchSave();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return true;
    }

}

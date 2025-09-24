package edu.vanderbilt.yunyulin.speechdrop.handlers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.FileUpload;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.LOGGER;

@Data
public class IndexHandler {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Vertx vertx;
    private final File uploadDirectory;
    private final File indexFile;

    public IndexHandler(Vertx vertx, File uploadDirectory) {
        this.vertx = vertx;
        this.uploadDirectory = uploadDirectory;
        this.indexFile = new File(uploadDirectory, "index");
    }

    private List<FileEntry> entries;
    private boolean loaded = false;

    public void load(Handler<IndexHandler> onComplete) {
        vertx.fileSystem().readFile(indexFile.getPath(), res -> {
            if (res.succeeded()) {
                try {
                    entries = new ArrayList<>(Arrays.asList(
                            mapper.readValue(res.result().toString(), FileEntry[].class)
                    ));
                } catch (IOException e) { // This should never happen
                    e.printStackTrace();
                }
            } else {
                entries = new ArrayList<>();
            }
            loaded = true;
            onComplete.handle(this);
        });
    }

    public void addFile(FileUpload uploadedFile, Date creationTime, Handler<String> indexHandler) {
        checkLoad();
        LOGGER.info("[" + uploadDirectory.getName() + "] Processing upload "
                + uploadedFile.fileName()
                + " (" + uploadedFile.size() + ")");
        vertx.fileSystem().mkdir(uploadDirectory.getPath(), uploadDirRes -> {
            File destDir = new File(uploadDirectory, Integer.toString(entries.size()));
            vertx.fileSystem().mkdir(destDir.getPath(), mkdirRes -> {
                File dest = new File(destDir, uploadedFile.fileName());
                vertx.fileSystem().copy(uploadedFile.uploadedFileName(), dest.getPath(),
                        res -> {
                            entries.add(new FileEntry(dest.getName(), creationTime.getTime()));
                            indexHandler.handle(writeIndex());
                        }
                );
            });
        });
    }

    public void deleteFile(int index, Handler<String> indexHandler) {
        checkLoad();
        LOGGER.info("[" + uploadDirectory.getName() + "] Processing delete for index "
                + index);
        String completedIndex;
        if (entries.get(index) != null) {
            entries.set(index, null);
            completedIndex = writeIndex();
            vertx.fileSystem().deleteRecursive(
                    new File(uploadDirectory, Integer.toString(index)).getPath(), true
            );
        } else {
            completedIndex = getIndexString();
        }
        indexHandler.handle(completedIndex);
    }

    public Collection<File> getFiles() {
        checkLoad();
        List<File> files = new ArrayList<>(entries.size());
        int index = 0;
        for (FileEntry entry : entries) {
            if (entry != null) {
                File fileDir = new File(uploadDirectory, Integer.toString(index));
                files.add(new File(fileDir, entry.name));
            }
            index++;
        }
        return files;
    }

    public String getIndexString() {
        checkLoad();
        try {
            return mapper.writeValueAsString(entries);
        } catch (JsonProcessingException e) { // This should never happen
            e.printStackTrace();
            return null;
        }
    }

    private String writeIndex() {
        String indexString = getIndexString();
        vertx.fileSystem().writeFile(indexFile.getPath(),
                Buffer.buffer(indexString));
        return indexString;
    }

    private void checkLoad() {
        if (!loaded) throw new IllegalStateException("Index not loaded");
    }

    private static class FileEntry {
        public String name;
        public long ctime;

        @JsonCreator
        public FileEntry(@JsonProperty("name") String name,
                         @JsonProperty("ctime") long ctime) {
            this.name = name;
            this.ctime = ctime;
        }
    }
}

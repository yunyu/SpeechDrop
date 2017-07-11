package edu.vanderbilt.yunyulin.speechdrop.handlers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.FileUpload;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.logger;
import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.vertx;

@Data
public class IndexHandler {
    private static final ObjectMapper mapper = new ObjectMapper();

    private File uploadDirectory;
    private File indexFile;

    private List<FileEntry> entries;
    private boolean loaded = false;

    public IndexHandler(File uploadDirectory, Handler<IndexHandler> loadHandler) {
        this.uploadDirectory = uploadDirectory;
        indexFile = new File(uploadDirectory, "index");
        if (!indexFile.exists()) {
            entries = new ArrayList<>();
        } else {
            vertx().fileSystem().readFile(indexFile.getPath(), res -> {
                try {
                    entries = new ArrayList<>(Arrays.asList(
                            mapper.readValue(res.result().toString(), FileEntry[].class)
                    ));
                    loaded = true;
                    loadHandler.handle(this);
                } catch (IOException e) { // This should never happen
                    e.printStackTrace();
                }
            });
        }
    }

    public void addFile(FileUpload uploadedFile, Date creationTime, Handler<String> indexHandler) {
        checkLoad();
        logger().info("[" + uploadDirectory.getName() + "] Processing upload "
                + uploadedFile.fileName()
                + " (" + uploadedFile.size() + ")");
        File destDir = new File(uploadDirectory, Integer.toString(entries.size()));
        vertx().fileSystem().mkdir(destDir.getPath(), mkdirRes -> {
            File dest = new File(destDir, uploadedFile.fileName());
            entries.add(new FileEntry(dest.getName(), creationTime.getTime()));
            vertx().fileSystem().copy(uploadedFile.uploadedFileName(), dest.getPath(),
                    res -> indexHandler.handle(writeIndex())
            );
        });
    }

    public void deleteFile(int index, Handler<String> indexHandler) {
        checkLoad();
        logger().info("[" + uploadDirectory.getName() + "] Processing delete for index "
                + index);
        entries.set(index, null);
        vertx().fileSystem().deleteRecursive(
                new File(uploadDirectory, Integer.toString(index)).getPath(), true,
                res -> indexHandler.handle(writeIndex())
        );
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
        vertx().fileSystem().writeFile(indexFile.getPath(),
                Buffer.buffer(indexString),
                null);
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

package edu.vanderbilt.yunyulin.speechdrop.handlers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
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

    public Future<IndexHandler> load() {
        return vertx.fileSystem().readFile(indexFile.getPath())
                .compose(buffer -> {
                    try {
                        entries = new ArrayList<>(Arrays.asList(
                                mapper.readValue(buffer.toString(), FileEntry[].class)
                        ));
                        loaded = true;
                        return Future.succeededFuture(this);
                    } catch (IOException e) {
                        return Future.failedFuture(e);
                    }
                })
                .recover(err -> {
                    entries = new ArrayList<>();
                    loaded = true;
                    return Future.succeededFuture(this);
                });
    }

    public Future<String> addFile(FileUpload uploadedFile, Date creationTime) {
        checkLoad();
        LOGGER.info("[" + uploadDirectory.getName() + "] Processing upload "
                + uploadedFile.fileName()
                + " (" + uploadedFile.size() + ")");
        File destDir = new File(uploadDirectory, Integer.toString(entries.size()));
        File dest = new File(destDir, uploadedFile.fileName());
        return vertx.fileSystem()
                .mkdirs(uploadDirectory.getPath())
                .compose(v -> vertx.fileSystem().mkdirs(destDir.getPath()))
                .compose(v -> vertx.fileSystem().copy(uploadedFile.uploadedFileName(), dest.getPath()))
                .compose(v -> {
                    entries.add(new FileEntry(dest.getName(), creationTime.getTime()));
                    return writeIndex();
                });
    }

    public Future<String> deleteFile(int index) {
        checkLoad();
        LOGGER.info("[" + uploadDirectory.getName() + "] Processing delete for index "
                + index);
        if (entries.get(index) != null) {
            entries.set(index, null);
            String dirToDelete = new File(uploadDirectory, Integer.toString(index)).getPath();
            return writeIndex()
                    .compose(indexString -> vertx.fileSystem()
                            .deleteRecursive(dirToDelete)
                            .recover(err -> Future.succeededFuture())
                            .map(v -> indexString)
                    );
        }
        return Future.succeededFuture(getIndexString());
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

    private Future<String> writeIndex() {
        String indexString = getIndexString();
        return vertx.fileSystem()
                .writeFile(indexFile.getPath(), Buffer.buffer(indexString))
                .map(indexString);
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

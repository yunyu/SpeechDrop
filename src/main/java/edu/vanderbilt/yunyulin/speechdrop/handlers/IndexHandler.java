package edu.vanderbilt.yunyulin.speechdrop.handlers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
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

    public IndexHandler(UploadHandler uploadHandler) {
        this.uploadDirectory = uploadHandler.getUploadDirectory();
        indexFile = new File(uploadDirectory, "index");
        if (!indexFile.exists()) {
            entries = new ArrayList<>();
        } else {
            try {
                entries = new ArrayList<>(Arrays.asList(
                        mapper.readValue(com.google.common.io.Files.toString(indexFile, Charsets.UTF_8), FileEntry[].class)
                ));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addFile(FileUpload uploadedFile, Date creationTime) {
        logger().info("[" + uploadDirectory.getName() + "] Processing upload "
                + uploadedFile.fileName()
                + " (" + uploadedFile.size() + ")");
        File destDir = new File(uploadDirectory, Integer.toString(entries.size()));
        vertx().fileSystem().mkdir(destDir.getPath(), mkdirRes -> {
            File dest = new File(destDir, uploadedFile.fileName());
            entries.add(new FileEntry(dest.getName(), creationTime.getTime()));
            vertx().fileSystem().copy(uploadedFile.uploadedFileName(), dest.getPath(), res -> {
                if (res.succeeded()) writeIndex();
            });
        });
    }

    public void deleteFile(int index) {
        logger().info("[" + uploadDirectory.getName() + "] Processing delete for index "
                + index);
        entries.set(index, null);
        vertx().fileSystem().deleteRecursive(
                new File(uploadDirectory, Integer.toString(index)).getPath(),true, res -> writeIndex()
        );
    }

    public Collection<File> getFiles() {
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

    public String getIndexString() throws JsonProcessingException {
        return mapper.writeValueAsString(entries);
    }

    private void writeIndex() {
        try {
            vertx().fileSystem().writeFile(indexFile.getPath(),
                    Buffer.buffer(mapper.writeValueAsString(entries)),
                    null);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
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

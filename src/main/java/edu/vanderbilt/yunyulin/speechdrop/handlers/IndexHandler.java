package edu.vanderbilt.yunyulin.speechdrop.handlers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.MoreExecutors;
import edu.vanderbilt.yunyulin.speechdrop.OrderedExecutor;
import edu.vanderbilt.yunyulin.speechdrop.Util;
import lombok.Data;
import ro.pippo.core.FileItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication.getLogger;

@Data
public class IndexHandler {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final OrderedExecutor savePool = new OrderedExecutor(
            MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) Executors.newCachedThreadPool())
    );

    private String tag;
    private File uploadDirectory;
    private File indexFile;

    private List<FileEntry> entries;

    public IndexHandler(UploadHandler uploadHandler) {
        this.uploadDirectory = uploadHandler.getUploadDirectory();
        this.tag = uploadDirectory.getName();
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

    public void addFile(FileItem uploadedFile, Date creationTime) {
        getLogger().info("[" + uploadDirectory.getName() + "] Processing upload "
                + uploadedFile.getSubmittedFileName()
                + " (" + uploadedFile.getSize() + ")");
        File destDir = new File(uploadDirectory, Integer.toString(entries.size()));
        destDir.mkdir();
        File dest = new File(destDir, uploadedFile.getSubmittedFileName());
        entries.add(new FileEntry(dest.getName(), creationTime.getTime()));
        savePool.execute(() -> {
            try {
                uploadedFile.write(dest);
                Files.copy(uploadedFile.getInputStream(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                mapper.writeValue(indexFile, entries);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, tag);
    }

    public void deleteFile(int index) {
        getLogger().info("[" + uploadDirectory.getName() + "] Processing delete for index "
                + index);
        entries.set(index, null);
        savePool.execute(() -> {
            try {
                Util.deleteFolderRecursive(new File(uploadDirectory, Integer.toString(index)).toPath());
                mapper.writeValue(indexFile, entries);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, tag);
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

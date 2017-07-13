package edu.vanderbilt.yunyulin.speechdrop.room;

import edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication;
import edu.vanderbilt.yunyulin.speechdrop.handlers.IndexHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import lombok.Getter;

import java.io.File;
import java.util.*;

public class Room {
    @Getter
    private final String id;
    @Getter
    private final RoomData data;

    private final Queue<Handler<IndexHandler>> queuedOperations = new ArrayDeque<>(2);
    private IndexHandler indexHandler;

    public Room(Vertx vertx, String id, RoomData data) {
        this.id = id;
        this.data = data;

        new IndexHandler(vertx, new File(SpeechDropApplication.BASE_PATH, id)).load(loadedIndex -> {
            this.indexHandler = loadedIndex;
            while (!queuedOperations.isEmpty()) {
                queuedOperations.poll().handle(loadedIndex);
            }
        });
    }

    private void scheduleOperation(Handler<IndexHandler> operation) {
        if (indexHandler != null) {
            operation.handle(indexHandler);
        } else {
            queuedOperations.offer(operation);
        }
    }

    public Future<String> handleUpload(RoutingContext ctx) {
        Future<String> uploadFuture = Future.future();

        Iterator<FileUpload> itr = ctx.fileUploads().iterator();
        if (!itr.hasNext()) {
            uploadFuture.fail(new Exception("no_file"));
        }

        FileUpload uploadedFile = itr.next();
        Date now = new Date();

        String mimeType = uploadedFile.contentType();
        if (!SpeechDropApplication.allowedMimeTypes.contains(mimeType)) {
            uploadFuture.fail(new Exception("bad_type"));
        }
        if (uploadedFile.size() > SpeechDropApplication.maxUploadSize) {
            uploadFuture.fail(new Exception("too_large"));
        }

        scheduleOperation(index -> index.addFile(uploadedFile, now, uploadFuture::complete));
        return uploadFuture;
    }

    public Future<String> getIndex() {
        Future<String> indexFuture = Future.future();
        scheduleOperation(index -> indexFuture.complete(index.getIndexString()));
        return indexFuture;
    }

    public Future<Collection<File>> getFiles() {
        Future<Collection<File>> fileFuture = Future.future();
        scheduleOperation(index -> fileFuture.complete(index.getFiles()));
        return fileFuture;
    }

    public Future<String> deleteFile(int fileIndex) {
        Future<String> deleteFuture = Future.future();
        scheduleOperation(index -> index.deleteFile(fileIndex, deleteFuture::complete));
        return deleteFuture;
    }
}

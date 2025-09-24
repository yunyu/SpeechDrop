package edu.vanderbilt.yunyulin.speechdrop.room;

import edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication;
import edu.vanderbilt.yunyulin.speechdrop.handlers.IndexHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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
        Promise<String> uploadPromise = Promise.promise();

        Iterator<FileUpload> itr = ctx.fileUploads().iterator();
        if (!itr.hasNext()) {
            uploadPromise.fail(new Exception("no_file"));
            return uploadPromise.future();
        }

        FileUpload uploadedFile = itr.next();
        Date now = new Date();

        String mimeType = uploadedFile.contentType();
        if (!SpeechDropApplication.allowedMimeTypes.contains(mimeType)) {
            uploadPromise.fail(new Exception("bad_type"));
            return uploadPromise.future();
        }
        if (uploadedFile.size() > SpeechDropApplication.maxUploadSize) {
            uploadPromise.fail(new Exception("too_large"));
            return uploadPromise.future();
        }

        scheduleOperation(index -> index.addFile(uploadedFile, now, uploadPromise::complete));
        return uploadPromise.future();
    }

    public Future<String> getIndex() {
        Promise<String> indexPromise = Promise.promise();
        scheduleOperation(index -> indexPromise.complete(index.getIndexString()));
        return indexPromise.future();
    }

    public Future<Collection<File>> getFiles() {
        Promise<Collection<File>> filesPromise = Promise.promise();
        scheduleOperation(index -> filesPromise.complete(index.getFiles()));
        return filesPromise.future();
    }

    public Future<String> deleteFile(int fileIndex) {
        Promise<String> deletePromise = Promise.promise();
        scheduleOperation(index -> index.deleteFile(fileIndex, deletePromise::complete));
        return deletePromise.future();
    }
}

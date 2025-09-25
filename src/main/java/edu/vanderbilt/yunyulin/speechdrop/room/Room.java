package edu.vanderbilt.yunyulin.speechdrop.room;

import edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication;
import edu.vanderbilt.yunyulin.speechdrop.handlers.IndexHandler;
import io.vertx.core.AsyncResult;
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

    public void handleUpload(RoutingContext ctx, Handler<AsyncResult<String>> handler) {
        Iterator<FileUpload> itr = ctx.fileUploads().iterator();
        if (!itr.hasNext()) {
            handler.handle(Future.failedFuture("no_file"));
            return;
        }

        FileUpload uploadedFile = itr.next();
        Date now = new Date();

        String mimeType = uploadedFile.contentType();
        if (!SpeechDropApplication.allowedMimeTypes.contains(mimeType)) {
            handler.handle(Future.failedFuture("bad_type"));
            return;
        }
        if (uploadedFile.size() > SpeechDropApplication.maxUploadSize) {
            handler.handle(Future.failedFuture("too_large"));
            return;
        }

        scheduleOperation(index -> index.addFile(uploadedFile, now,
                newIndex -> handler.handle(Future.succeededFuture(newIndex))));
    }

    public void getIndex(Handler<String> handler) {
        scheduleOperation(index -> handler.handle(index.getIndexString()));
    }

    public void getFiles(Handler<Collection<File>> handler) {
        scheduleOperation(index -> handler.handle(index.getFiles()));
    }

    public void deleteFile(int fileIndex, Handler<String> handler) {
        scheduleOperation(index -> index.deleteFile(fileIndex, handler));
    }
}

package edu.vanderbilt.yunyulin.speechdrop.room;

import edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication;
import edu.vanderbilt.yunyulin.speechdrop.handlers.IndexHandler;
import io.vertx.core.Future;
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

    private final Future<IndexHandler> indexHandlerFuture;

    public Room(Vertx vertx, String id, RoomData data) {
        this.id = id;
        this.data = data;

        IndexHandler handler = new IndexHandler(vertx, new File(SpeechDropApplication.BASE_PATH, id));
        this.indexHandlerFuture = handler.load();
    }

    public Future<String> handleUpload(RoutingContext ctx) {
        Iterator<FileUpload> itr = ctx.fileUploads().iterator();
        if (!itr.hasNext()) {
            return Future.failedFuture("no_file");
        }

        FileUpload uploadedFile = itr.next();
        Date now = new Date();

        String mimeType = uploadedFile.contentType();
        if (!SpeechDropApplication.allowedMimeTypes.contains(mimeType)) {
            return Future.failedFuture("bad_type");
        }
        if (uploadedFile.size() > SpeechDropApplication.maxUploadSize) {
            return Future.failedFuture("too_large");
        }

        return indexHandlerFuture.compose(index -> index.addFile(uploadedFile, now));
    }

    public Future<String> getIndex() {
        return indexHandlerFuture.map(IndexHandler::getIndexString);
    }

    public Future<Collection<File>> getFiles() {
        return indexHandlerFuture.map(index -> new ArrayList<>(index.getFiles()));
    }

    public Future<String> deleteFile(int fileIndex) {
        return indexHandlerFuture.compose(index -> index.deleteFile(fileIndex));
    }
}

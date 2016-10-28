package edu.vanderbilt.yunyulin.speechdrop.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.vanderbilt.yunyulin.speechdrop.handlers.UploadHandler;
import lombok.Getter;
import ro.pippo.core.route.RouteContext;

public class Room {
    @Getter
    private String id;
    private UploadHandler uploadHandler;
    @Getter
    private RoomData data;

    public Room(String id, RoomData data) {
        this.id = id;
        this.data = data;
        this.uploadHandler = new UploadHandler(id);
    }

    public String handleUpload(RouteContext ctx) throws Exception {
        return uploadHandler.handleUpload(ctx);
    }

    public String getIndex() throws JsonProcessingException {
        return uploadHandler.getIndex().getIndexString();
    }

    public void deleteFile(int index) {
        uploadHandler.getIndex().deleteFile(index);
    }
}

package edu.vanderbilt.yunyulin.speechdrop.handlers;

import edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import lombok.Getter;

import java.io.File;
import java.util.Date;

public class UploadHandler {
    @Getter
    File uploadDirectory;
    @Getter
    IndexHandler index;

    public UploadHandler(String uploadPath) {
        uploadDirectory = new File(SpeechDropApplication.BASE_PATH, uploadPath);
        index = new IndexHandler(this);
    }

    public String handleUpload(RoutingContext ctx) throws Exception {
        FileUpload uploadedFile = ctx.fileUploads().iterator().next();

        String mimeType = uploadedFile.contentType();
        if (!SpeechDropApplication.allowedMimeTypes.contains(mimeType)) {
            throw new Exception("bad_type");
        }
        if (uploadedFile.size() > SpeechDropApplication.maxUploadSize) {
            throw new Exception("too_large");
        } else {
            uploadDirectory.mkdir();
            index.addFile(uploadedFile, new Date());
            return index.getIndexString();
        }
    }
}

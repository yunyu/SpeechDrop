package edu.vanderbilt.yunyulin.speechdrop.handlers;

import edu.vanderbilt.yunyulin.speechdrop.SpeechDropApplication;
import lombok.Getter;
import ro.pippo.core.FileItem;
import ro.pippo.core.route.RouteContext;

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

    public String handleUpload(RouteContext ctx) throws Exception {
        FileItem uploadedFile = ctx.getRequest().getFile("file");

        String mimeType = uploadedFile.getContentType();
        if (!SpeechDropApplication.allowedMimeTypes.contains(mimeType)) {
            throw new Exception("bad_type");
        }
        if (uploadedFile.getSize() > SpeechDropApplication.maxUploadSize) {
            throw new Exception("too_large");
        } else {
            uploadDirectory.mkdir();
            index.addFile(uploadedFile, new Date());
            return index.getIndexString();
        }
    }
}

package edu.vanderbilt.yunyulin.speechdrop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Joiner;
import com.google.common.html.HtmlEscapers;
import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import edu.vanderbilt.yunyulin.speechdrop.logging.ConciseFormatter;
import edu.vanderbilt.yunyulin.speechdrop.room.Room;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import static edu.vanderbilt.yunyulin.speechdrop.Bootstrap.LOCALHOST;
import static edu.vanderbilt.yunyulin.speechdrop.Bootstrap.SIO_PORT;
import static io.vertx.core.http.HttpMethod.*;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class SpeechDropApplication extends AbstractVerticle {
    private static final String EMPTY_INDEX = "[]";
    public static final File BASE_PATH = new File("public" + File.separator + "uploads");
    private static Logger logger;

    // Lombok getter won't work with import static
    public static Logger getLogger() {
        return logger;
    }

    // private static final List<String> allowedExtensions = Arrays.asList("doc", "docx", "odt", "pdf", "txt", "rtf");
    public static final List<String> allowedMimeTypes = Arrays.asList(
            "application/msword", // doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // docx
            "application/vnd.oasis.opendocument.text", // odt
            "application/x-iwork-pages-sffpages", // pages
            "application/pdf", // pdf
            "text/plain", // txt
            "text/rtf", // rtf
            "application/rtf", // rtf
            "text/richtext" // IE11 rtf
    );
    public static final long maxUploadSize = 5 * 1024 * 1024;

    private RoomHandler roomHandler = new RoomHandler();
    private PurgeTask purgeTask = new PurgeTask(roomHandler);
    private Broadcaster broadcaster = new Broadcaster(LOCALHOST, SIO_PORT, roomHandler);

    String mainPage;
    String roomTemplate;
    String aboutPage;

    public SpeechDropApplication(String mainPage, String roomTemplate, String aboutPage) {
        // Initialize logging
        logger = Logger.getLogger("SpeechDrop");
        logger.setUseParentHandlers(false);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new ConciseFormatter());
        logger.addHandler(consoleHandler);

        // Initialize templates
        this.mainPage = mainPage;
        this.roomTemplate = roomTemplate.replace("{% ALLOWED_MIMES %}", Joiner.on(",").join(allowedMimeTypes));
        this.aboutPage = aboutPage.replace("{% VERSION %}", Bootstrap.VERSION);

        // Initialize broadcaster
        broadcaster.start();
    }

    @Override
    public void start() {
        getLogger().info("Starting SpeechDrop (" + Bootstrap.VERSION + ")");
        BASE_PATH.mkdir();
        purgeTask.start();

        String TEXT_HTML = "text/html";
        String APPLICATION_JSON = "application/json";

        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create().setBodyLimit(maxUploadSize));
        router.route("/").method(GET).handler(ctx ->
                ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML).end(mainPage)
        );

        router.route("/about").method(GET).handler(ctx ->
                ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML).end(aboutPage)
        );

        router.route("/makeroom").method(POST).handler(ctx -> {
            String roomName = ctx.request().params().get("name");
            // getLogger().info("CSRF token: " + ctx.getParameter(CSRFHandler.TOKEN));
            if (roomName != null) roomName = roomName.trim();
            if (roomName == null || roomName.length() == 0 || roomName.length() > 60) {
                ctx.reroute("/");
            } else {
                Room r = roomHandler.makeRoom(roomName);
                ctx.reroute("/" + r.getId());
            }
        });

        router.route("/:roomid/index").method(GET).produces(APPLICATION_JSON).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                ctx.response().setStatusCode(404).putHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .end(EMPTY_INDEX);
            } else {
                Room r = roomHandler.getRoom(roomId);
                try {
                    ctx.response().putHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .end(r.getIndex());
                } catch (JsonProcessingException e) {
                    ctx.response().setStatusCode(500).end();
                    e.printStackTrace();
                }
            }
        });

        GET("/{roomid}/archive", ctx -> {
            String roomId = ctx.getParameter("roomid").toString();
            if (!roomHandler.roomExists(roomId)) {
                ctx.status(404);
            } else {
                Room r = roomHandler.getRoom(roomId);
                Collection<File> files = r.getFiles();
                String outFile = r.getData().name.trim() + ".zip";
                Response res = ctx.getResponse();
                if (files.size() == 0) {
                    res.file(outFile, Util.getEmptyZipInputStream());
                } else {
                    OutputStream out = res.chunked(true).filenameHeader(outFile).getOutputStream();
                    try {
                        Util.zip(files, out);
                        res.getHttpServletResponse().flushBuffer();
                    } catch (IOException e) {
                        throw new PippoRuntimeException(e);
                    }
                }
            }
        });

        ALL("/{roomid}/upload", csrfHandler);
        POST("/{roomid}/upload", ctx -> {
            String roomId = ctx.getParameter("roomid").toString();
            // getLogger().info("CSRF token: " + ctx.getParameter(CSRFHandler.TOKEN));
            if (!roomHandler.roomExists(roomId)) {
                getLogger().warning("(Upload) Nonexist " + roomId);
                ctx.status(500);
                ctx.send(EMPTY_INDEX);
            } else {
                Room r = roomHandler.getRoom(roomId);
                try {
                    String index = r.handleUpload(ctx);
                    ctx.send(index);
                    broadcaster.publishUpdate(r.getId(), index);
                } catch (Exception e) {
                    ctx.status(500);
                    ctx.send(e.getMessage());
                }
            }
        });

        ALL("/{roomid}/delete", csrfHandler);
        POST("/{roomid}/delete", ctx -> {
            String roomId = ctx.getParameter("roomid").toString();
            if (!roomHandler.roomExists(roomId)) {
                getLogger().warning("(Upload) Nonexist " + roomId);
                ctx.status(500);
                ctx.send(EMPTY_INDEX);
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.deleteFile(ctx.getParameter("fileIndex").toInt());
                try {
                    String index = r.getIndex();
                    ctx.send(index);
                    broadcaster.publishUpdate(r.getId(), index);
                } catch (JsonProcessingException e) {
                    ctx.status(500);
                    ctx.send(e.getMessage());
                }
            }
        });

        router.route("/:roomid").method(GET).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                ctx.reroute("/");
            } else {
                Room r = roomHandler.getRoom(roomId);
                try {
                    ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML)
                            .end(roomTemplate
                                    .replace("{% INDEX %}", r.getIndex())
                                    .replace("{% ROOM %}", r.getId())
                                    .replace("{% NAME %}",
                                            HtmlEscapers.htmlEscaper().escape(r.getData().name)));
                } catch (JsonProcessingException e) {
                    ctx.response().setStatusCode(500).end();
                    e.printStackTrace();
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        getLogger().info("Shutting down");
        broadcaster.stop();
    }
}
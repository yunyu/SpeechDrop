package edu.vanderbilt.yunyulin.speechdrop;

import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import edu.vanderbilt.yunyulin.speechdrop.room.Room;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static edu.vanderbilt.yunyulin.speechdrop.Util.HTML_ESCAPER;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.core.http.HttpHeaders.LOCATION;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;

public class SpeechDropApplication {
    private static final String EMPTY_INDEX = "[]";
    public static final File BASE_PATH = new File("public" + File.separator + "uploads");
    public static final Logger LOGGER = LoggerFactory.getLogger(SpeechDropApplication.class.getName());

    // private static final List<String> allowedExtensions = Arrays.asList("doc", "docx", "odt", "pdf", "txt", "rtf");
    public static final List<String> allowedMimeTypes = Arrays.asList(
            "application/msword", // doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // docx
            "application/vnd.ms-word.document.macroEnabled.12", //macro-enabled doc
            "application/vnd.oasis.opendocument.text", // odt
            "application/x-iwork-pages-sffpages", // pages
            "application/pdf", // pdf
            "text/plain", // txt
            "text/rtf", // rtf
            "application/rtf", // rtf
            "text/richtext" // IE11 rtf
    );
    public static final long maxUploadSize = 10 * 1024 * 1024;
    private static final String TEXT_HTML = "text/html; charset=utf-8";
    private static final String APPLICATION_JSON_PRODUCES = "application/json";
    private static final String APPLICATION_JSON = "application/json; charset=utf-8";

    private final Vertx vertx;
    private final JsonObject config;
    private final RoomHandler roomHandler;
    private final Broadcaster broadcaster;

    private final String mainPage;
    private final String roomTemplate;
    private final String aboutPage;
    private final String allowedMimeTypesCsv;

    public SpeechDropApplication(Vertx vertx, JsonObject config, String mainPage, String roomTemplate, String aboutPage) {
        this.vertx = vertx;
        this.config = config;

        // Initialize rooms
        roomHandler = new RoomHandler(vertx);
        broadcaster = new Broadcaster(vertx, roomHandler);

        // Initialize templates
        this.allowedMimeTypesCsv = String.join(",", allowedMimeTypes);
        this.mainPage = mainPage;
        this.roomTemplate = roomTemplate;
        this.aboutPage = aboutPage;
    }

    private void sendEmptyIndex(RoutingContext ctx, int errCode) {
        ctx.response().setStatusCode(errCode).putHeader(CONTENT_TYPE, APPLICATION_JSON).end(EMPTY_INDEX);
    }

    private void redirect(RoutingContext ctx, String location) {
        ctx.response().setStatusCode(302).putHeader(LOCATION, location).end();
    }

    public void mount(Router router) throws IOException {
        LOGGER.info("Starting SpeechDrop (" + SpeechDropVerticle.VERSION + ")");
        Files.createDirectories(BASE_PATH.toPath());
        new PurgeTask(roomHandler, vertx, config.getInteger("purgeIntervalInSeconds")).schedule();

        router.route().handler(BodyHandler.create().setBodyLimit(maxUploadSize).setDeleteUploadedFilesOnEnd(true));
        router.route().handler(SessionHandler.create(
                LocalSessionStore.create(vertx, "speechdrop-sessions", 10000L)
        ).setSessionTimeout(6 * 60 * 60 * 1000));
        router.route().handler(CSRFHandler.create(vertx, config.getString("csrfSecret")));

        router.route("/").method(GET).handler(ctx ->
                ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML).end(mainPage)
        );

        router.route("/sock/*").handler(broadcaster.getSockJSHandler());

        router.route("/static/*").handler(StaticHandler.create("static"));

        router.route("/about").method(GET).handler(ctx ->
                ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML).end(aboutPage)
        );

        router.route("/makeroom").method(POST).handler(ctx -> {
            String roomName = ctx.request().getFormAttribute("name");
            // logger().info("CSRF token: " + ctx.getParameter(CSRFHandler.TOKEN));
            if (roomName != null) roomName = roomName.trim();
            if (roomName == null || roomName.length() == 0 || roomName.length() > 60) {
                redirect(ctx, "/");
            } else {
                Room r = roomHandler.makeRoom(roomName);
                redirect(ctx, "/" + r.getId());
            }
        });

        router.route("/:roomid/index").method(GET).produces(APPLICATION_JSON_PRODUCES).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                sendEmptyIndex(ctx, 404);
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.getIndex(index ->
                        ctx.response().putHeader(CONTENT_TYPE, APPLICATION_JSON).end(index)
                );
            }
        });

        router.route("/:roomid/archive").method(GET).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                ctx.response().setStatusCode(404).end();
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.getFiles(files -> {
                    String outFile = r.getData().name.trim() + ".zip";

                    Handler<Buffer> writeBufferToResponse = buf -> ctx.response()
                            .setChunked(true)
                            // See https://stackoverflow.com/a/38324508
                            .putHeader("Content-Disposition", "attachment; filename=\"" + outFile
                                    .replace("\\", "\\\\")
                                    .replace("\"", "\\\"") + "\""
                            )
                            .putHeader(CONTENT_TYPE, "application/octet-stream")
                            .end(buf);

                    if (files.size() == 0) {
                        writeBufferToResponse.handle(Util.getEmptyZipBuffer());
                    } else {
                        vertx.<Buffer>executeBlocking(promise -> {
                            try {
                                promise.complete(Util.zip(files));
                            } catch (IOException e) {
                                promise.fail(e);
                            }
                        }, false).onComplete(res -> {
                            if (res.succeeded()) {
                                writeBufferToResponse.handle(res.result());
                            } else {
                                ctx.response().setStatusCode(500).end();
                            }
                        });
                    }
                });
            }
        });

        router.route("/:roomid/upload").method(POST).produces(APPLICATION_JSON_PRODUCES).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                LOGGER.warn("(Upload) Nonexist " + roomId);
                ctx.response().setStatusCode(404).end(EMPTY_INDEX);
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.handleUpload(ctx, ar -> {
                    if (ar.succeeded()) {
                        String index = ar.result();
                        ctx.response().putHeader(CONTENT_TYPE, APPLICATION_JSON_PRODUCES).end(index);
                        broadcaster.publishUpdate(r.getId(), index);
                    } else {
                        ctx.response().setStatusCode(400).end(
                                new JsonObject().put("err", ar.cause().getMessage()).toString()
                        );
                    }
                });
            }
        });

        router.route("/:roomid/delete").method(POST).produces(APPLICATION_JSON_PRODUCES).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                LOGGER.warn("(Upload) Nonexist " + roomId);
                sendEmptyIndex(ctx, 404);
            } else {
                Room r = roomHandler.getRoom(roomId);
                String fileIndex = ctx.request().getFormAttribute("fileIndex");
                if (fileIndex == null) {
                    sendEmptyIndex(ctx, 400);
                } else {
                    r.deleteFile(Integer.parseInt(fileIndex), index -> {
                        ctx.response().putHeader(CONTENT_TYPE, APPLICATION_JSON).end(index);
                        broadcaster.publishUpdate(r.getId(), index);
                    });
                }
            }
        });

        router.route("/:roomid/").method(GET).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            redirect(ctx, "/" + roomId);
        });

        String mediaUrl;
        if (config.getBoolean("debugMediaDownloads")) {
            mediaUrl = "/media/uploads/";
            router.route("/media/*").handler(StaticHandler.create("public"));
        } else {
            mediaUrl = config.getString("mediaUrl");
        }
        router.route("/:roomid").method(GET).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                redirect(ctx, "/");
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.getIndex(index -> {
                    String escapedRoomName = HTML_ESCAPER.escape(r.getData().name);
                    JsonObject configPayload = new JsonObject()
                            .put("mediaUrl", mediaUrl)
                            .put("roomName", escapedRoomName)
                            .put("roomId", r.getId())
                            .put("allowedMimes", allowedMimeTypesCsv)
                            .put("initialFiles", new JsonArray(index));

                    ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML).end(
                            roomTemplate
                                    .replace("<%= ROOM_CONFIG %>", configPayload.encode())
                    );
                });
            }
        });
    }
}

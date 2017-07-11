package edu.vanderbilt.yunyulin.speechdrop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Joiner;
import com.google.common.html.HtmlEscapers;
import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import edu.vanderbilt.yunyulin.speechdrop.logging.ConciseFormatter;
import edu.vanderbilt.yunyulin.speechdrop.room.Room;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropVerticle.LOCALHOST;
import static edu.vanderbilt.yunyulin.speechdrop.SpeechDropVerticle.SIO_PORT;
import static io.vertx.core.http.HttpHeaders.LOCATION;
import static io.vertx.core.http.HttpMethod.*;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class SpeechDropApplication {
    private static final String EMPTY_INDEX = "[]";
    public static final File BASE_PATH = new File("public" + File.separator + "uploads");
    private static Logger logger;

    // Lombok getter won't work with import static
    public static Logger logger() {
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
    private static final String TEXT_HTML = "text/html";
    private static final String APPLICATION_JSON = "application/json";

    private final Vertx vertx;
    private final RoomHandler roomHandler;
    private final Broadcaster broadcaster;

    private final String mainPage;
    private final String roomTemplate;
    private final String aboutPage;

    public SpeechDropApplication(Vertx vertx, String mainPage, String roomTemplate, String aboutPage) {
        this.vertx = vertx;

        // Initialize logging
        logger = Logger.getLogger("SpeechDrop");
        logger.setUseParentHandlers(false);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new ConciseFormatter());
        logger.addHandler(consoleHandler);

        // Initialize rooms
        roomHandler = new RoomHandler(vertx);
        broadcaster = new Broadcaster(LOCALHOST, SIO_PORT, roomHandler);

        // Initialize templates
        this.mainPage = mainPage;
        this.roomTemplate = roomTemplate.replace("{% ALLOWED_MIMES %}", Joiner.on(",").join(allowedMimeTypes));
        this.aboutPage = aboutPage.replace("{% VERSION %}", SpeechDropVerticle.VERSION);

        // Initialize broadcaster
        broadcaster.start();
    }

    private void sendEmptyIndex(RoutingContext ctx, int errCode) {
        ctx.response().setStatusCode(errCode).putHeader(CONTENT_TYPE, APPLICATION_JSON).end(EMPTY_INDEX);
    }

    private void redirect(RoutingContext ctx, String location) {
        ctx.response().setStatusCode(302).putHeader(LOCATION, location).end();
    }

    public void mount(Router router) {
        logger().info("Starting SpeechDrop (" + SpeechDropVerticle.VERSION + ")");
        BASE_PATH.mkdir();
        new PurgeTask(roomHandler, vertx).schedule();

        router.route().handler(BodyHandler.create().setBodyLimit(maxUploadSize).setDeleteUploadedFilesOnEnd(true));

        router.route("/").method(GET).handler(ctx ->
                ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML).end(mainPage)
        );

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

        router.route("/:roomid/index").method(GET).produces(APPLICATION_JSON).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                sendEmptyIndex(ctx, 404);
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.getIndex().setHandler(ar ->
                        ctx.response().putHeader(CONTENT_TYPE, APPLICATION_JSON).end(ar.result())
                );
            }
        });

        router.route("/:roomid/archive").method(GET).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                ctx.response().setStatusCode(404).end();
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.getFiles().setHandler(ar -> {
                    Collection<File> files = ar.result();
                    String outFile = r.getData().name.trim() + ".zip";

                    Handler<Buffer> writeBufferToResponse = buf -> ctx.response()
                            .setChunked(true)
                            .putHeader("Content-Disposition", "attachment; filename=\"" + outFile + "\"")
                            .putHeader(CONTENT_TYPE, "application/octet-stream")
                            .end(buf);

                    if (files.size() == 0) {
                        writeBufferToResponse.handle(Util.getEmptyZipBuffer());
                    } else {
                        vertx.<Buffer>executeBlocking(fut -> {
                            try {
                                fut.complete(Util.zip(files));
                            } catch (IOException e) {
                                fut.fail(e);
                            }
                        }, false, res -> {
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

        router.route("/:roomid/upload").method(POST).produces(APPLICATION_JSON).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                logger().warning("(Upload) Nonexist " + roomId);
                ctx.response().setStatusCode(404).end(EMPTY_INDEX);
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.handleUpload(ctx).setHandler(ar -> {
                    if (ar.succeeded()) {
                        String index = ar.result();
                        ctx.response().putHeader(CONTENT_TYPE, APPLICATION_JSON).end(index);
                        broadcaster.publishUpdate(r.getId(), index);
                    } else {
                        ctx.response().setStatusCode(400).end(
                                new JsonObject().put("err", ar.cause().getMessage()).toString()
                        );
                    }
                });
            }
        });

        router.route("/:roomid/delete").method(POST).produces(APPLICATION_JSON).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                logger().warning("(Upload) Nonexist " + roomId);
                sendEmptyIndex(ctx, 404);
            } else {
                Room r = roomHandler.getRoom(roomId);
                String fileIndex = ctx.request().getFormAttribute("fileIndex");
                if (fileIndex == null) {
                    sendEmptyIndex(ctx, 400);
                } else {
                    r.deleteFile(Integer.parseInt(fileIndex)).setHandler(ar -> {
                        ctx.response().putHeader(CONTENT_TYPE, APPLICATION_JSON).end(ar.result());
                        broadcaster.publishUpdate(r.getId(), ar.result());
                    });
                }
            }
        });

        router.route("/:roomid").method(GET).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            logger().info("Hitting " + roomId);
            if (!roomHandler.roomExists(roomId)) {
                redirect(ctx, "/");
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.getIndex().setHandler(ar -> {
                    ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML)
                            .end(roomTemplate
                                    .replace("{% INDEX %}", ar.result())
                                    .replace("{% ROOM %}", r.getId())
                                    .replace("{% NAME %}",
                                            HtmlEscapers.htmlEscaper().escape(r.getData().name)));
                });
            }
        });
    }
}
package edu.vanderbilt.yunyulin.speechdrop;

import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import edu.vanderbilt.yunyulin.speechdrop.room.Room;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;

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
import static io.vertx.core.http.HttpMethod.OPTIONS;

public class SpeechDropApplication {
    private static final String EMPTY_INDEX = "[]";
    public static final File BASE_PATH = new File("public" + File.separator + "uploads");
    public static final Logger LOGGER = LoggerFactory.getLogger(SpeechDropApplication.class.getName());

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
        this.allowedMimeTypesCsv = String.join(",", allowedMimeTypes) + ",.cmir";
        this.mainPage = mainPage;
        this.roomTemplate = roomTemplate;
        this.aboutPage = aboutPage;
    }

    private void sendEmptyIndex(RoutingContext ctx, int errCode) {
        ctx.response().setStatusCode(errCode).putHeader(CONTENT_TYPE, APPLICATION_JSON).end(EMPTY_INDEX);
    }

    // JSON error body for the external API: {"err": "<code>"}.
    private void apiError(RoutingContext ctx, int status, String err) {
        if (ctx.response().ended()) return;
        ctx.response().setStatusCode(status).putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(new JsonObject().put("err", err).encode());
    }

    private static void apiCorsHeaders(RoutingContext ctx) {
        ctx.response()
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Expose-Headers", "X-Uploaded-File-Index");
    }

    private static String describeRequest(RoutingContext ctx) {
        String origin = ctx.request().getHeader("Origin");
        return ctx.request().method() + " " + ctx.request().path()
                + " from " + ctx.request().remoteAddress()
                + (origin == null ? "" : " (Origin " + origin + ")");
    }

    private static String uploadSummary(RoutingContext ctx) {
        return ctx.fileUploads().stream().findFirst()
                .map(f -> "\"" + f.fileName() + "\" (" + f.contentType() + ", " + f.size() + " bytes)")
                .orElse("no file part");
    }

    // Room name from a JSON body ({"name": "..."}) or a form field "name".
    private static String requestedRoomName(RoutingContext ctx) {
        String contentType = ctx.request().getHeader(CONTENT_TYPE);
        if (contentType != null && contentType.toLowerCase().startsWith("application/json")) {
            try {
                JsonObject body = ctx.body().asJsonObject();
                Object name = body == null ? null : body.getValue("name");
                return name instanceof String ? (String) name : null;
            } catch (DecodeException | ClassCastException e) {
                return null;
            }
        }
        return ctx.request().getFormAttribute("name");
    }

    private void redirect(RoutingContext ctx, String location) {
        ctx.response().setStatusCode(302).putHeader(LOCATION, location).end();
    }

    public void mount(Router router) throws IOException {
        LOGGER.info("Starting SpeechDrop (" + SpeechDropVerticle.VERSION + ")");
        Files.createDirectories(BASE_PATH.toPath());
        new PurgeTask(roomHandler, vertx, config.getInteger("purgeIntervalInSeconds")).schedule();

        // CORS for the external API only. The API never reads cookies, so allowing any origin
        // exposes nothing the room code does not already gate. Registered before the body handler so
        // that errors it raises (e.g. 413) also carry these headers.
        router.route("/api/v1/*").handler(ctx -> {
            apiCorsHeaders(ctx);
            ctx.next();
        });
        router.route("/api/v1/*").method(OPTIONS).handler(ctx ->
                ctx.response()
                        .putHeader("Access-Control-Allow-Methods", "POST, OPTIONS")
                        .putHeader("Access-Control-Allow-Headers", "Content-Type")
                        .putHeader("Access-Control-Max-Age", "86400")
                        .setStatusCode(204)
                        .end()
        );
        // Failures anywhere under /api/v1 (including the body handler rejecting an oversized upload
        // with 413 before our route runs) come back as JSON with CORS headers, never a bare status.
        router.route("/api/v1/*").failureHandler(ctx -> {
            int status = ctx.statusCode();
            apiCorsHeaders(ctx);
            if (status == 413) {
                LOGGER.warn("(API) Rejected oversized request: " + describeRequest(ctx));
                apiError(ctx, 413, "too_large");
            } else if (status >= 400 && status < 500) {
                LOGGER.warn("(API) Request failed with " + status + ": " + describeRequest(ctx));
                apiError(ctx, status, "bad_request");
            } else {
                LOGGER.error("(API) Internal error handling " + describeRequest(ctx), ctx.failure());
                apiError(ctx, 500, "server_error");
            }
        });

        router.route().handler(BodyHandler.create().setBodyLimit(maxUploadSize).setDeleteUploadedFilesOnEnd(true));
        // SockJS fallback POSTs require parsed bodies but carry no CSRF tokens.
        // This bridge permits outbound room updates only, not application writes.
        router.route("/sock/*").subRouter(broadcaster.getSockJSHandler());

        // External API (e.g. for CardMirror). Mounted BEFORE the CSRF handler on purpose: CSRF
        // protects requests that carry ambient credentials (cookies the browser attaches on its own).
        // These endpoints use no cookies, so a cross-site request has nothing to ride on. As in the
        // web UI, anyone may create a room, and knowing the room code is what authorizes an upload.

        // Create a room. Body: JSON {"name": "..."} or form field "name" (1-60 characters after
        // trimming, the same rule as the website). 201 {roomId, name, url}; 400 bad_name.
        router.route("/api/v1/rooms").method(POST).handler(ctx -> {
            String roomName = requestedRoomName(ctx);
            if (roomName != null) roomName = roomName.trim();
            if (roomName == null || roomName.length() == 0 || roomName.length() > 60) {
                LOGGER.info("(API) Room not created, bad name ("
                        + (roomName == null ? "missing" : roomName.length() + " chars") + "): " + describeRequest(ctx));
                apiError(ctx, 400, "bad_name");
                return;
            }
            Room r = roomHandler.makeRoom(roomName);
            LOGGER.info("(API) [" + r.getId() + "] Room created: " + describeRequest(ctx));
            ctx.response()
                    .setStatusCode(201)
                    .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .putHeader(LOCATION, "/" + r.getId())
                    .end(new JsonObject()
                            .put("roomId", r.getId())
                            .put("name", roomName)
                            .put("url", "/" + r.getId())
                            .encode());
        });

        // Upload a file to a room (multipart, first file part, same type/size checks as the website).
        // 201 {fileIndex, files}; 404 room_not_found; 400 no_file; 415 bad_type; 413 too_large.
        router.route("/api/v1/rooms/:roomid/files").method(POST).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                LOGGER.warn("(API) Upload to nonexistent room " + roomId + ": " + describeRequest(ctx));
                apiError(ctx, 404, "room_not_found");
                return;
            }
            Room r = roomHandler.getRoom(roomId);
            r.handleUpload(ctx).onComplete(ar -> {
                if (ar.succeeded()) {
                    String fileIndex = ctx.response().headers().get("X-Uploaded-File-Index");
                    ctx.response()
                            .setStatusCode(201)
                            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                            .end(new JsonObject()
                                    .put("fileIndex", fileIndex == null ? null : Integer.valueOf(fileIndex))
                                    .put("files", new JsonArray(ar.result()))
                                    .encode());
                    broadcaster.publishUpdate(r.getId(), ar.result());
                    return;
                }
                String reason = String.valueOf(ar.cause().getMessage());
                switch (reason) {
                    case "no_file":
                        LOGGER.info("(API) [" + roomId + "] Upload rejected, no file part: " + describeRequest(ctx));
                        apiError(ctx, 400, "no_file");
                        break;
                    case "bad_type":
                        LOGGER.info("(API) [" + roomId + "] Upload rejected, bad type " + uploadSummary(ctx)
                                + ": " + describeRequest(ctx));
                        apiError(ctx, 415, "bad_type");
                        break;
                    case "too_large":
                        LOGGER.info("(API) [" + roomId + "] Upload rejected, too large " + uploadSummary(ctx)
                                + ": " + describeRequest(ctx));
                        apiError(ctx, 413, "too_large");
                        break;
                    default:
                        LOGGER.error("(API) [" + roomId + "] Upload failed " + uploadSummary(ctx)
                                + ": " + describeRequest(ctx), ar.cause());
                        apiError(ctx, 500, "upload_failed");
                }
            });
        });

        // No authenticated sessions: use signed double-submit CSRF tokens so parallel uploads
        // and multiple tabs do not invalidate each other's tokens.
        router.route().handler(CSRFHandler.create(vertx, config.getString("csrfSecret")))
                .failureHandler(ctx -> {
                    if (ctx.statusCode() == 403) {
                        ctx.response().setStatusCode(403).end();
                    } else {
                        ctx.next();
                    }
                });

        router.route("/").method(GET).handler(ctx ->
                ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML).end(mainPage)
        );


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
                r.getIndex().onComplete(ar ->
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
                r.getFiles().onComplete(ar -> {
                    Collection<File> files = ar.result();
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

        router.route("/:roomid/upload").method(POST).produces(APPLICATION_JSON_PRODUCES).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                LOGGER.warn("(Upload) Nonexist " + roomId);
                ctx.response().setStatusCode(404).end(EMPTY_INDEX);
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.handleUpload(ctx).onComplete(ar -> {
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
                    r.deleteFile(Integer.parseInt(fileIndex)).onComplete(ar -> {
                        ctx.response().putHeader(CONTENT_TYPE, APPLICATION_JSON).end(ar.result());
                        broadcaster.publishUpdate(r.getId(), ar.result());
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
            router.route("/media/*").handler(ctx -> {
                ctx.response().putHeader("Content-Disposition", "attachment")
                        .putHeader("X-Content-Type-Options", "nosniff");
                ctx.next();
            }).handler(StaticHandler.create("public"));
        } else {
            mediaUrl = config.getString("mediaUrl");
        }
        router.route("/:roomid").method(GET).handler(ctx -> {
            String roomId = ctx.request().getParam("roomid");
            if (!roomHandler.roomExists(roomId)) {
                redirect(ctx, "/");
            } else {
                Room r = roomHandler.getRoom(roomId);
                r.getIndex().onComplete(ar -> {
                    String escapedRoomName = HTML_ESCAPER.escape(r.getData().name);
                    JsonObject configPayload = new JsonObject()
                            .put("mediaUrl", mediaUrl)
                            .put("roomName", escapedRoomName)
                            .put("roomId", r.getId())
                            .put("allowedMimes", allowedMimeTypesCsv)
                            .put("purgeIntervalInSeconds", config.getInteger("purgeIntervalInSeconds"))
                            .put("initialFiles", new JsonArray(ar.result()));

                    ctx.response().putHeader(CONTENT_TYPE, TEXT_HTML).end(
                            roomTemplate
                                    .replace("<%= ROOM_CONFIG %>", configPayload.encode())
                    );
                });
            }
        });
    }
}

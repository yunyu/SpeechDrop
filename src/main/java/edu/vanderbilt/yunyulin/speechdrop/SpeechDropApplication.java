package edu.vanderbilt.yunyulin.speechdrop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Joiner;
import com.google.common.html.HtmlEscapers;
import edu.vanderbilt.yunyulin.speechdrop.handlers.RoomHandler;
import edu.vanderbilt.yunyulin.speechdrop.logging.ConciseFormatter;
import edu.vanderbilt.yunyulin.speechdrop.room.Room;
import lombok.Getter;
import ro.pippo.core.Application;
import ro.pippo.core.HttpConstants;
import ro.pippo.core.PippoSettings;
import ro.pippo.core.RuntimeMode;
import ro.pippo.core.route.CSRFHandler;
import ro.pippo.core.route.RouteContext;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

public class SpeechDropApplication extends Application {
    private static final String EMPTY_INDEX = "[]";
    public static final File BASE_PATH = new File("public" + File.separator + "uploads");
    private static Logger logger;

    public static Logger getLogger() {
        return logger;
    }

    // private static final List<String> allowedExtensions = Arrays.asList("doc", "docx", "odt", "pdf", "txt", "rtf");
    public static final List<String> allowedMimeTypes = Arrays.asList(
            "application/msword", // doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // docx
            "application/vnd.oasis.opendocument.text", // odt
            "application/pdf", // pdf
            "text/plain", // txt
            "text/rtf", // rtf
            "application/rtf", // rtf
            "text/richtext" // IE11 rtf
    );
    public static final long maxUploadSize = 5 * 1024 * 1024;

    private RoomHandler roomHandler = new RoomHandler();
    private PurgeTask purgeTask = new PurgeTask(roomHandler);
    private CSRFHandler csrfHandler = new CSRFHandler();

    String mainPage;
    String roomTemplate;
    String aboutPage;

    public SpeechDropApplication(String mainPage, String roomTemplate, String aboutPage, PippoSettings settings) {
        super(settings);

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

    }

    @Override
    protected void onInit() {
        getLogger().info("Starting SpeechDrop (" + Bootstrap.VERSION + ")");

        setMaximumUploadSize(maxUploadSize);
        BASE_PATH.mkdir();

        purgeTask.start();

        ALL("/", csrfHandler);
        GET("/", ctx -> sendWithCSRF(ctx, mainPage));

        GET("/about", ctx -> ctx.send(aboutPage));

        ALL("/makeroom", csrfHandler);
        POST("/makeroom", ctx -> {
            String roomName = ctx.getParameter("name").toString();
            // getLogger().info("CSRF token: " + ctx.getParameter(CSRFHandler.TOKEN));
            if (roomName == null || roomName.length() == 0 || roomName.length() > 60) {
                ctx.redirect("/");
            } else {
                Room r = roomHandler.makeRoom(roomName);
                ctx.redirect("/" + r.getId());
            }
        });

        GET("/{roomid}/", ctx -> ctx.redirect("/" + ctx.getParameter("roomid").toString()));
        ALL("/{roomid}", csrfHandler);
        GET("/{roomid}", ctx -> {
            String roomId = ctx.getParameter("roomid").toString();
            if (!roomHandler.roomExists(roomId)) {
                ctx.redirect("/");
            } else {
                Room r = roomHandler.getRoom(roomId);
                try {
                    sendWithCSRF(ctx, roomTemplate
                            .replace("{% INDEX %}", r.getIndex())
                            .replace("{% ROOM %}", r.getId())
                            .replace("{% NAME %}",
                                    HtmlEscapers.htmlEscaper().escape(r.getData().name)));
                } catch (JsonProcessingException e) {
                    ctx.status(500);
                    e.printStackTrace();
                }
            }
        });

        GET("/{roomid}/index", ctx -> {
            String roomId = ctx.getParameter("roomid").toString();
            if (!roomHandler.roomExists(roomId)) {
                ctx.status(404);
                ctx.send(EMPTY_INDEX);
            } else {
                Room r = roomHandler.getRoom(roomId);
                try {
                    ctx.getResponse().contentType(HttpConstants.ContentType.APPLICATION_JSON);
                    ctx.getResponse().send(r.getIndex());
                } catch (JsonProcessingException e) {
                    ctx.status(500);
                    e.printStackTrace();
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
                    ctx.send(r.handleUpload(ctx));
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
                    ctx.send(r.getIndex());
                } catch (JsonProcessingException e) {
                    ctx.status(500);
                    ctx.send(e.getMessage());
                }
            }
        });
    }

    private void sendWithCSRF(RouteContext ctx, String response) {
        String csrfToken = ctx.getSession(CSRFHandler.TOKEN);
        if (csrfToken == null) { // This should never happen
            csrfToken = "";
        }
        ctx.send(response.replace("{% CSRF_TOKEN %}", csrfToken));
    }
}
package edu.vanderbilt.yunyulin.speechdrop;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.impl.Utils;

public class SpeechDropVerticle extends AbstractVerticle {
    public static String HTTP_HOST = "127.0.0.1";
    public static int HTTP_PORT = 6969;

    public static String VERSION;
    public static int PURGE_INTERVAL = 60 * 24 * 60 * 60;

    @Override
    public void start() throws Exception {
        VERSION = Utils.readFileToString(vertx, "version");
        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setCompressionSupported(true);
        HttpServer httpServer = vertx.createHttpServer(serverOptions);
        Router router = Router.router(vertx);

        new SpeechDropApplication(vertx,
                Utils.readFileToString(vertx, "main.html"),
                Utils.readFileToString(vertx, "room.html"),
                Utils.readFileToString(vertx, "about.html")
        ).mount(router);

        httpServer.requestHandler(router::accept).listen(HTTP_PORT, HTTP_HOST);
    }
}
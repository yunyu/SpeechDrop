package edu.vanderbilt.yunyulin.speechdrop;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;

public class SpeechDropVerticle extends AbstractVerticle {
    public static String VERSION;

    @Override
    public void start() throws Exception {
        VERSION = vertx.fileSystem().readFileBlocking("VERSION").toString();
        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setCompressionSupported(true);
        HttpServer httpServer = vertx.createHttpServer(serverOptions);
        Router router = Router.router(vertx);

        new SpeechDropApplication(vertx, config(),
                vertx.fileSystem().readFileBlocking("main.html").toString(),
                vertx.fileSystem().readFileBlocking("room.html").toString(),
                vertx.fileSystem().readFileBlocking("about.html").toString()
        ).mount(router);

        httpServer.requestHandler(router).listen(config().getInteger("port"), config().getString("host"));
    }
}
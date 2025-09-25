package edu.vanderbilt.yunyulin.speechdrop;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;

public class SpeechDropVerticle extends AbstractVerticle {
    public static String VERSION;

    @Override
    public void start(Promise<Void> startPromise) {
        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setCompressionSupported(true);
        HttpServer httpServer = vertx.createHttpServer(serverOptions);
        Router router = Router.router(vertx);

        try {
            VERSION = readFile("VERSION");
            new SpeechDropApplication(vertx, config(),
                    readFile("main.html"),
                    readFile("room.html"),
                    readFile("about.html")
            ).mount(router);
        } catch (Exception e) {
            startPromise.fail(e);
            return;
        }

        httpServer
                .requestHandler(router)
                .listen(config().getInteger("port"), config().getString("host"))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        startPromise.complete();
                    } else {
                        startPromise.fail(ar.cause());
                    }
                });
    }

    private String readFile(String path) {
        return vertx.fileSystem().readFileBlocking(path).toString();
    }
}
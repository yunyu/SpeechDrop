package edu.vanderbilt.yunyulin.speechdrop;

import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import ro.pippo.core.Pippo;
import ro.pippo.core.PippoRuntimeException;
import ro.pippo.core.PippoSettings;
import ro.pippo.core.RuntimeMode;
import ro.pippo.core.util.IoUtils;
import ro.pippo.jetty.JettyServer;

import java.io.File;
import java.io.IOException;

public class Bootstrap {
    public static String LOCALHOST = "127.0.0.1";
    public static int HTTP_PORT = 6969;
    public static int SIO_PORT = 6970;

    public static String VERSION;
    public static int TWO_MONTHS = 60 * 24 * 60 * 60;

    public static void main(String[] args) throws IOException {
        PippoSettings settings = new PippoSettings(RuntimeMode.PROD);

        Class<?> bootstrapClass = Bootstrap.class;
        VERSION = IoUtils.toString(bootstrapClass.getResourceAsStream("/version"));
        SpeechDropApplication app = new SpeechDropApplication(
                IoUtils.toString(bootstrapClass.getResourceAsStream("/main.html")),
                IoUtils.toString(bootstrapClass.getResourceAsStream("/room.html")),
                IoUtils.toString(bootstrapClass.getResourceAsStream("/about.html")),
                settings);
        Pippo pippo = new Pippo(app).setServer(new PersistentJettyServer());
        pippo.getServer().getSettings().port(HTTP_PORT);
        pippo.getServer().getSettings().host(LOCALHOST);
        pippo.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.destroy();
            pippo.stop();
        }));
    }

    public static class PersistentJettyServer extends JettyServer {
        @Override
        protected ServletContextHandler createPippoHandler() {
            ServletContextHandler handler = super.createPippoHandler();

            // set session manager with persistence
            HashSessionManager sessionManager = new HashSessionManager();
            try {
                sessionManager.setStoreDirectory(new File("sessions-storage"));
                sessionManager.setMaxInactiveInterval(TWO_MONTHS);
            } catch (IOException e) {
                throw new PippoRuntimeException(e);
            }
            // sessionManager.setLazyLoad(true);
            handler.setSessionHandler(new SessionHandler(sessionManager));

            return handler;
        }
    }
}
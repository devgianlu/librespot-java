package xyz.gianlu.librespot.player;


import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import xyz.gianlu.librespot.ZeroconfServer;
import xyz.gianlu.librespot.common.Log4JUncaughtExceptionHandler;
import xyz.gianlu.librespot.core.Session;
import xyz.gianlu.librespot.mercury.MercuryClient;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * @author Gianlu
 */
public class Main {

    public static void main(String[] args) throws IOException, GeneralSecurityException, Session.SpotifyAuthenticationException, MercuryClient.MercuryException {
        FileConfiguration conf = new FileConfiguration(args);
        Configurator.setRootLevel(conf.loggingLevel());
        Thread.setDefaultUncaughtExceptionHandler(new Log4JUncaughtExceptionHandler());

        if (conf.authStrategy() == FileConfiguration.AuthStrategy.ZEROCONF) {
            ZeroconfServer server = conf.initZeroconfBuilder().create();
            server.addSessionListener(new ZeroconfServer.SessionListener() {
                Player lastPlayer = null;

                {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        if (lastPlayer != null) lastPlayer.close();
                    }));
                }

                @Override
                public void sessionChanged(@NotNull Session session) {
                    if (lastPlayer != null) lastPlayer.close();
                    lastPlayer = new Player(conf.toPlayer(), session);
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.closeSession();
                    server.close();
                } catch (IOException ignored) {
                }
            }));
        } else {
            Session session = conf.initSessionBuilder().create();
            Player player = new Player(conf.toPlayer(), session);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    player.close();
                    session.close();
                } catch (IOException ignored) {
                }
            }));
        }
    }
}

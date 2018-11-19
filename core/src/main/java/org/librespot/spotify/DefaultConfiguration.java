package org.librespot.spotify;

import org.jetbrains.annotations.NotNull;
import org.librespot.spotify.player.TrackHandler;

import java.io.File;

/**
 * @author Gianlu
 */
public final class DefaultConfiguration extends AbsConfiguration {

    //****************//
    //---- PLAYER ----//
    //****************//

    @NotNull
    @Override
    public TrackHandler.AudioQuality preferredQuality() {
        return TrackHandler.AudioQuality.VORBIS_320;
    }

    @Override
    public float normalisationPregain() {
        return 0;
    }

    //****************//
    //---- CACHE -----//
    //****************//

    @Override
    public boolean cacheEnabled() {
        return true;
    }

    @Override
    public @NotNull File cacheDir() {
        return new File("./cache/");
    }

    @Override
    public boolean doCleanUp() {
        return true;
    }
}
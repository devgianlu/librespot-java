package xyz.gianlu.librespot.player;

import xyz.gianlu.librespot.player.codecs.AudioQuality;

import java.io.File;

/**
 * @author devgianlu
 */
public final class Configuration {
    // Audio
    public final AudioQuality preferredQuality;
    public final boolean enableNormalisation;
    public final float normalisationPregain;
    public final boolean autoplayEnabled;
    public final int crossfadeDuration;

    // Output
    public final AudioOutput output;
    public final File outputPipe;
    public final File metadataPipe;
    public final String[] mixerSearchKeywords;
    public final boolean logAvailableMixers;
    public final int releaseLineDelay;

    // Volume
    public final int initialVolume;
    public final int volumeSteps;

    // Behaviour
    public final boolean preloadEnabled;
    public final boolean retryOnChunkError;

    private Configuration(AudioQuality preferredQuality, boolean enableNormalisation, float normalisationPregain, boolean autoplayEnabled, int crossfadeDuration,
                          AudioOutput output, File outputPipe, File metadataPipe, String[] mixerSearchKeywords, boolean logAvailableMixers, int releaseLineDelay,
                          int initialVolume, int volumeSteps,
                          boolean preloadEnabled, boolean retryOnChunkError) {
        this.preferredQuality = preferredQuality;
        this.enableNormalisation = enableNormalisation;
        this.normalisationPregain = normalisationPregain;
        this.autoplayEnabled = autoplayEnabled;
        this.crossfadeDuration = crossfadeDuration;
        this.output = output;
        this.outputPipe = outputPipe;
        this.metadataPipe = metadataPipe;
        this.mixerSearchKeywords = mixerSearchKeywords;
        this.logAvailableMixers = logAvailableMixers;
        this.releaseLineDelay = releaseLineDelay;
        this.initialVolume = initialVolume;
        this.volumeSteps = volumeSteps;
        this.preloadEnabled = preloadEnabled;
        this.retryOnChunkError = retryOnChunkError;
    }

    public final static class Builder {
        // Audio
        private AudioQuality preferredQuality = AudioQuality.NORMAL;
        private boolean enableNormalisation = true;
        private float normalisationPregain = 3.0f;
        private boolean autoplayEnabled = true;
        private int crossfadeDuration = 0;

        // Output
        private AudioOutput output = AudioOutput.MIXER;
        private File outputPipe;
        private File metadataPipe;
        private String[] mixerSearchKeywords;
        private boolean logAvailableMixers = true;
        private int releaseLineDelay = 20;

        // Volume
        private int initialVolume = Player.VOLUME_MAX;
        private int volumeSteps = 64;

        // Behaviour
        private boolean preloadEnabled = true;
        private boolean retryOnChunkError = true;

        public Builder() {
        }

        public Builder setPreferredQuality(AudioQuality preferredQuality) {
            this.preferredQuality = preferredQuality;
            return this;
        }

        public Builder setEnableNormalisation(boolean enableNormalisation) {
            this.enableNormalisation = enableNormalisation;
            return this;
        }

        public Builder setNormalisationPregain(float normalisationPregain) {
            this.normalisationPregain = normalisationPregain;
            return this;
        }

        public Builder setAutoplayEnabled(boolean autoplayEnabled) {
            this.autoplayEnabled = autoplayEnabled;
            return this;
        }

        public Builder setCrossfadeDuration(int crossfadeDuration) {
            this.crossfadeDuration = crossfadeDuration;
            return this;
        }

        public Builder setOutput(AudioOutput output) {
            this.output = output;
            return this;
        }

        public Builder setOutputPipe(File outputPipe) {
            this.outputPipe = outputPipe;
            return this;
        }

        public Builder setMetadataPipe(File metadataPipe) {
            this.metadataPipe = metadataPipe;
            return this;
        }

        public Builder setMixerSearchKeywords(String[] mixerSearchKeywords) {
            this.mixerSearchKeywords = mixerSearchKeywords;
            return this;
        }

        public Builder setLogAvailableMixers(boolean logAvailableMixers) {
            this.logAvailableMixers = logAvailableMixers;
            return this;
        }

        public Builder setReleaseLineDelay(int releaseLineDelay) {
            this.releaseLineDelay = releaseLineDelay;
            return this;
        }

        public Builder setInitialVolume(int initialVolume) {
            this.initialVolume = initialVolume;
            return this;
        }

        public Builder setVolumeSteps(int volumeSteps) {
            this.volumeSteps = volumeSteps;
            return this;
        }

        public Builder setPreloadEnabled(boolean preloadEnabled) {
            this.preloadEnabled = preloadEnabled;
            return this;
        }

        public Builder setRetryOnChunkError(boolean retryOnChunkError) {
            this.retryOnChunkError = retryOnChunkError;
            return this;
        }

        public Configuration build() {
            return new Configuration(preferredQuality, enableNormalisation, normalisationPregain, autoplayEnabled, crossfadeDuration,
                    output, outputPipe, metadataPipe, mixerSearchKeywords, logAvailableMixers, releaseLineDelay,
                    initialVolume, volumeSteps,
                    preloadEnabled, retryOnChunkError);
        }
    }
}

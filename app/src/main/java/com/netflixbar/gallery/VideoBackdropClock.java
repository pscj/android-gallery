package com.netflixbar.gallery;

import com.netflixbar.gallery.atmosphere.SmallBackdropProcessor;

/** Cadence and stale-frame rejection across pause/resume boundaries. */
final class VideoBackdropClock {
    private boolean playing;
    private long epoch, started;

    void setPlaying(boolean value) {
        if (playing != value) epoch++;
        playing = value;
    }

    long begin(long now) {
        started = now;
        return epoch;
    }

    boolean accepts(long ticket) { return playing && epoch == ticket; }

    long nextDelay(long now) {
        return Math.max(0, SmallBackdropProcessor.UPDATE_MS - (now - started));
    }
}

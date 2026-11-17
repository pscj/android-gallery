package com.netflixbar.gallery;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoBackdropClockTest {
    @Test public void processingTimeCountsTowardTheEightyMillisecondPeriod() {
        VideoBackdropClock clock = new VideoBackdropClock();
        clock.setPlaying(true);
        clock.begin(1000);
        assertEquals(65, clock.nextDelay(1015));
        assertEquals(0, clock.nextDelay(1090));
    }

    @Test public void pauseRejectsAnInFlightBackground() {
        VideoBackdropClock clock = new VideoBackdropClock();
        clock.setPlaying(true);
        long ticket = clock.begin(1000);
        assertTrue(clock.accepts(ticket));
        clock.setPlaying(false);
        assertFalse(clock.accepts(ticket));
    }

    @Test public void resumeCannotCommitACopyFromBeforePause() {
        VideoBackdropClock clock = new VideoBackdropClock();
        clock.setPlaying(true);
        long old = clock.begin(1000);
        clock.setPlaying(false);
        clock.setPlaying(true);
        assertFalse(clock.accepts(old));
        assertTrue(clock.accepts(clock.begin(2000)));
    }

    @Test public void finishedOrNotYetPlayingVideoDoesNotAcceptUpdates() {
        VideoBackdropClock clock = new VideoBackdropClock();
        assertFalse(clock.accepts(clock.begin(0)));
        clock.setPlaying(true);
        long ticket = clock.begin(100);
        clock.setPlaying(false);
        assertFalse(clock.accepts(ticket));
    }
}

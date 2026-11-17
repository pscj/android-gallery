package com.netflixbar.gallery;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.netflixbar.gallery.atmosphere.SmallBackdropProcessor;
import com.netflixbar.gallery.atmosphere.BackdropPolicy;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Fits moving photos exactly like still photos; only their tiny backdrop texture is refreshed. */
final class MediaAtmosphereController implements AutoCloseable {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            runnable.run();
        }, "media-backdrop");
        thread.setDaemon(true);
        return thread;
    });
    private final SmallBackdropProcessor processor = new SmallBackdropProcessor();
    private final Map<ViewGroup, Session> sessions = new IdentityHashMap<>();
    private boolean closed;

    private final class Session {
        final ViewGroup host;
        final MediaAtmosphereView background;
        final Runnable tick = () -> capture(this);
        final View.OnAttachStateChangeListener attachListener;
        View.OnLayoutChangeListener layoutListener;
        PlayerView playerView;
        ExoPlayer player;
        Player.Listener playerListener;
        double aspect, gap;
        boolean blur, active = true, busy;
        final VideoBackdropClock clock = new VideoBackdropClock();
        Future<?> work;
        SmallBackdropProcessor.Frame lastFrame;
        int renderedWidth, renderedHeight;
        boolean renderedBlur;
        Runnable refreshPhoto;

        Session(ViewGroup host) {
            this.host = host;
            background = new MediaAtmosphereView(host.getContext());
            host.addView(background, 0, new ViewGroup.LayoutParams(-1, -1));
            attachListener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { }
                @Override public void onViewDetachedFromWindow(View view) { remove(Session.this); }
            };
            host.addOnAttachStateChangeListener(attachListener);
        }
    }

    void showPhoto(View foreground, Bitmap bitmap, String key) {
        if (bitmap == null) { clear(foreground); return; }
        // Cropped photos already fill their slot; never add a blurred band behind them.
        if (foreground instanceof android.widget.ImageView
                && ((android.widget.ImageView) foreground).getScaleType()
                == android.widget.ImageView.ScaleType.CENTER_CROP) {
            clear(foreground);
            return;
        }
        Session session = replace(foreground);
        if (session == null) return;
        session.aspect = FitGeometry.aspect(bitmap.getWidth(), bitmap.getHeight(), 1, 0);
        session.refreshPhoto = () -> {
            if (!valid(session) || session.busy || session.host.getWidth() <= 0 || session.host.getHeight() <= 0) return;
            configure(session);
            if (session.lastFrame == null || photoDimensionsChanged(session)) render(session, bitmap, false, 0);
        };
        session.layoutListener = (view, l, t, r, b, ol, ot, or, ob) -> {
            if (valid(session) && r > l && b > t) {
                session.refreshPhoto.run();
            }
        };
        session.host.addOnLayoutChangeListener(session.layoutListener);
        if (session.host.getWidth() > 0 && session.host.getHeight() > 0) {
            session.refreshPhoto.run();
        }
    }

    void showVideo(PlayerView view, ExoPlayer player, VideoPreparation.Prepared prepared) {
        Session session = replace(view);
        if (session == null) return;
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setShutterBackgroundColor(Color.TRANSPARENT);
        session.playerView = view;
        session.player = player;
        session.clock.setPlaying(player.isPlaying());
        session.aspect = prepared.displayAspect;
        session.lastFrame = prepared.preview;
        session.layoutListener = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (valid(session) && r > l && b > t) configure(session);
        };
        session.host.addOnLayoutChangeListener(session.layoutListener);
        session.playerListener = new Player.Listener() {
            @Override public void onRenderedFirstFrame() { if (player.isPlaying()) schedule(session, 0); }
            @Override public void onIsPlayingChanged(boolean playing) {
                session.clock.setPlaying(playing);
                if (playing) schedule(session, 0); else freeze(session);
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) freeze(session);
            }
            @Override public void onVideoSizeChanged(VideoSize size) {
                session.aspect = FitGeometry.aspect(size.width, size.height,
                        size.pixelWidthHeightRatio, size.unappliedRotationDegrees);
                configure(session);
            }
        };
        player.addListener(session.playerListener);
        configure(session);
        if (player.isPlaying()) schedule(session, 0);
    }

    private void configure(Session session) {
        if (!valid(session) || session.host.getWidth() <= 0 || session.host.getHeight() <= 0) return;
        FitGeometry.Rect foreground = FitGeometry.fit(session.host.getWidth(), session.host.getHeight(), session.aspect);
        session.gap = foreground.gapFraction;
        session.blur = BackdropPolicy.useBlur(session.player != null, session.gap);
        if (session.lastFrame != null) session.background.show(session.lastFrame, session.blur);
        if (session.player != null && session.player.isPlaying() && session.gap > 0.000001) schedule(session, 0);
    }

    void clear(View foreground) {
        if (foreground != null && foreground.getParent() instanceof ViewGroup) {
            Session session = sessions.get((ViewGroup) foreground.getParent());
            if (session != null) remove(session);
        }
    }

    void stopVideos() {
        for (Session session : new ArrayList<>(sessions.values())) if (session.player != null) remove(session);
    }

    private Session replace(View foreground) {
        if (closed || foreground == null || !(foreground.getParent() instanceof ViewGroup)) return null;
        ViewGroup host = (ViewGroup) foreground.getParent();
        Session old = sessions.get(host);
        if (old != null) remove(old);
        for (int id : new int[]{R.id.portraitImageBackground, R.id.portraitImageBackgroundRight, R.id.singleImageBackground}) {
            View layer = host.findViewById(id);
            if (layer instanceof android.widget.ImageView) {
                ((android.widget.ImageView) layer).setImageDrawable(null);
                layer.setVisibility(View.GONE);
            }
        }
        Session session = new Session(host);
        sessions.put(host, session);
        return session;
    }

    private boolean valid(Session session) {
        return !closed && session.active && sessions.get(session.host) == session;
    }

    private void schedule(Session session, long delay) {
        main.removeCallbacks(session.tick);
        if (valid(session) && session.player.isPlaying()) main.postDelayed(session.tick, delay);
    }

    private void freeze(Session session) {
        session.clock.setPlaying(false);
        main.removeCallbacks(session.tick);
        // In-flight copies may finish, but the epoch check prevents them replacing the frozen frame.
    }

    private void capture(Session session) {
        if (!valid(session) || session.playerView.getPlayer() != session.player || !session.player.isPlaying()) return;
        if (session.gap <= 0.000001) return;
        if (session.busy || !session.host.isShown()) { schedule(session, SmallBackdropProcessor.UPDATE_MS); return; }
        View surface = session.playerView.getVideoSurfaceView();
        if (surface == null || surface.getWidth() <= 0 || surface.getHeight() <= 0) {
            schedule(session, SmallBackdropProcessor.UPDATE_MS); return;
        }
        long epoch = session.clock.begin(SystemClock.uptimeMillis());
        int[] size = VideoBackgroundSizing.sample(surface.getWidth(), surface.getHeight());
        Bitmap frame = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        session.busy = true;
        if (surface instanceof SurfaceView && Build.VERSION.SDK_INT >= 24) {
            try {
                PixelCopy.request((SurfaceView) surface, frame, result -> {
                    if (!valid(session) || !session.clock.accepts(epoch) || !session.player.isPlaying()) {
                        frame.recycle(); finished(session);
                    } else if (result == PixelCopy.SUCCESS) {
                        render(session, frame, true, epoch);
                    } else {
                        frame.recycle(); finished(session);
                    }
                }, main);
                return;
            } catch (IllegalArgumentException ignored) { }
        } else if (surface instanceof TextureView && ((TextureView) surface).isAvailable()) {
            Bitmap captured = ((TextureView) surface).getBitmap(frame);
            if (captured != null) {
                if (captured != frame) frame.recycle();
                render(session, captured, true, epoch);
                return;
            }
        }
        frame.recycle();
        finished(session);
    }

    private void render(Session session, Bitmap bitmap, boolean owned, long epoch) {
        if (!valid(session)) { if (owned) bitmap.recycle(); return; }
        session.busy = true;
        boolean blur = session.blur;
        boolean photo = session.player == null;
        int width = Math.max(1, session.host.getWidth()), height = Math.max(1, session.host.getHeight());
        session.renderedWidth = width;
        session.renderedHeight = height;
        session.renderedBlur = blur;
        session.work = worker.submit(() -> {
            try {
                SmallBackdropProcessor.Frame frame = photo
                        ? processor.processPhoto(bitmap, blur, width, height) : processor.process(bitmap, blur);
                main.post(() -> {
                    if (photo && (session.host.getWidth() != width || session.host.getHeight() != height
                            || session.blur != blur)) {
                        if (frame.bitmap != null) frame.bitmap.recycle();
                        return;
                    }
                    if (valid(session) && (session.player == null || session.clock.accepts(epoch))
                            && (session.player == null || session.player.isPlaying())) {
                        session.lastFrame = frame;
                        session.background.show(frame, session.blur);
                    } else if (frame.bitmap != null) {
                        // Never submitted to a View/RenderThread: safe to release immediately.
                        frame.bitmap.recycle();
                    }
                });
            } catch (RuntimeException e) {
                if (!Thread.currentThread().isInterrupted()) android.util.Log.w("MediaBackdrop", "Frame processing failed", e);
            } finally {
                if (owned) bitmap.recycle();
                finished(session);
            }
        });
    }

    private void finished(Session session) {
        main.post(() -> {
            if (!valid(session)) return;
            session.busy = false;
            if (session.player != null && session.player.isPlaying()) {
                schedule(session, session.clock.nextDelay(SystemClock.uptimeMillis()));
            } else if (session.player == null && photoDimensionsChanged(session)) {
                session.refreshPhoto.run();
            }
        });
    }

    private boolean photoDimensionsChanged(Session session) {
        return session.renderedWidth != session.host.getWidth()
                || session.renderedHeight != session.host.getHeight()
                || session.renderedBlur != session.blur;
    }

    private void remove(Session session) {
        session.active = false;
        sessions.remove(session.host);
        main.removeCallbacks(session.tick);
        if (session.work != null) session.work.cancel(true);
        if (session.player != null && session.playerListener != null) session.player.removeListener(session.playerListener);
        if (session.layoutListener != null) session.host.removeOnLayoutChangeListener(session.layoutListener);
        session.host.removeOnAttachStateChangeListener(session.attachListener);
        session.background.clear();
        session.host.removeView(session.background);
    }

    @Override public void close() {
        closed = true;
        for (Session session : new ArrayList<>(sessions.values())) remove(session);
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}

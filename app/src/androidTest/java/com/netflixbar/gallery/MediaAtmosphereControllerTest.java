package com.netflixbar.gallery;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MediaAtmosphereControllerTest {
    @Test public void everyPhotoLayoutInstallsBackgroundBehindItsForegroundWithoutChangingScaleType() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.content.Context context = new android.view.ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Gallery_NoActionBar);
            int[] layouts = {R.layout.fragment_first, R.layout.layout_media_two, R.layout.layout_media_grid,
                    R.layout.layout_media_three_portrait, R.layout.layout_media_two_portrait, R.layout.layout_media_portrait_left};
            int[] ids = {R.id.singleImageView, R.id.imgview1, R.id.imgview2, R.id.imgview3, R.id.imgview4};
            android.graphics.Bitmap photo = android.graphics.Bitmap.createBitmap(240, 135, android.graphics.Bitmap.Config.ARGB_8888);
            for (int layout : layouts) {
                android.view.View root = android.view.LayoutInflater.from(context).inflate(layout, null, false);
                try (MediaAtmosphereController controller = new MediaAtmosphereController()) {
                    for (int id : ids) {
                        android.widget.ImageView foreground = root.findViewById(id);
                        if (foreground == null) continue;
                        android.widget.ImageView.ScaleType scale = foreground.getScaleType();
                        controller.showPhoto(foreground, photo, "photo");
                        android.view.ViewGroup host = (android.view.ViewGroup) foreground.getParent();
                        if (scale == android.widget.ImageView.ScaleType.CENTER_CROP) {
                            assertNull(host.findViewById(R.id.media_atmosphere));
                        } else {
                            assertTrue(host.getChildAt(0) instanceof MediaAtmosphereView);
                        }
                        assertEquals(scale, foreground.getScaleType());
                    }
                }
            }
            photo.recycle();
        });
    }

    @Test public void twoPortraitLayoutLandscapePhotosFillSlotsWithoutBlurBackgrounds() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            android.view.View root = android.view.LayoutInflater.from(context)
                    .inflate(R.layout.layout_media_two_portrait, null, false);
            android.graphics.Bitmap photo = android.graphics.Bitmap.createBitmap(240, 135,
                    android.graphics.Bitmap.Config.ARGB_8888);
            try (MediaAtmosphereController controller = new MediaAtmosphereController()) {
                for (int id : new int[]{R.id.imgview3, R.id.imgview4}) {
                    android.widget.ImageView image = root.findViewById(id);
                    controller.showPhoto(image, photo, "landscape");
                    assertEquals(android.widget.ImageView.ScaleType.CENTER_CROP, image.getScaleType());
                    assertEquals(-1, image.getLayoutParams().width);
                    assertEquals(-1, image.getLayoutParams().height);
                    assertNull(((android.view.ViewGroup) image.getParent()).findViewById(R.id.media_atmosphere));
                }
            }
            photo.recycle();
        });
    }

    @Test public void videoKeepsFitAspectModeAndBackgroundIsBehindPlayer() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            FrameLayout host = new FrameLayout(context);
            PlayerView view = new PlayerView(context);
            host.addView(view, new FrameLayout.LayoutParams(1920, 1080));
            view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            ExoPlayer player = new ExoPlayer.Builder(context).build();
            view.setPlayer(player);
            try (MediaAtmosphereController controller = new MediaAtmosphereController()) {
                android.graphics.Bitmap poster = android.graphics.Bitmap.createBitmap(240, 135, android.graphics.Bitmap.Config.ARGB_8888);
                VideoPreparation.Prepared prepared = new VideoPreparation.Prepared("/unused.mp4", "video/mp4",
                        1920, 1080, 0, 1, 1000,
                        new com.netflixbar.gallery.atmosphere.SmallBackdropProcessor().process(poster, true));
                controller.showVideo(view, player, prepared);
                assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, view.getResizeMode());
                assertTrue(host.getChildAt(0) instanceof MediaAtmosphereView);
                assertSame(view, host.getChildAt(1));
                assertEquals(1920, view.getLayoutParams().width);
                assertEquals(1080, view.getLayoutParams().height);
                assertEquals(Color.TRANSPARENT, ((ColorDrawable) view.getBackground()).getColor());
                controller.stopVideos();
                assertSame(view, host.getChildAt(0));
            } finally {
                view.setPlayer(null);
                player.release();
            }
        });
    }
}

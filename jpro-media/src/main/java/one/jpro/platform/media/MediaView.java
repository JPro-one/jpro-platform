package one.jpro.platform.media;

import com.jpro.webapi.WebAPI;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import one.jpro.platform.media.player.MediaPlayer;
import one.jpro.platform.media.player.impl.FXMediaPlayer;
import one.jpro.platform.media.player.impl.FXMediaPlayerView;
import one.jpro.platform.media.player.impl.WebMediaPlayer;
import one.jpro.platform.media.player.impl.WebMediaPlayerView;
import one.jpro.platform.media.recorder.MediaRecorder;
import one.jpro.platform.media.recorder.NativeMediaRecorder;
import one.jpro.platform.media.recorder.NativeMediaRecorderView;
import one.jpro.platform.media.recorder.WebMediaRecorder;
import one.jpro.platform.media.recorder.WebMediaRecorderView;

/**
 * Provides a view of {@link MediaSource} being played by a {@link MediaPlayer}
 * or being recorded by a {@link MediaRecorder}.
 *
 * <p>The following code snippet provides a simple example of how to display a video:</p>
 *
 * <pre>{@code
 * public void start(Stage stage) {
 *     // Get the media source as an application argument.
 *     String source = getParameters().getRaw().get(0);
 *     MediaSource mediaSource = new MediaSource(source);
 *
 *     // Create the player and set to play automatically.
 *     MediaPlayer mediaPlayer = MediaPlayer.create(stage, mediaSource);
 *     mediaPlayer.setAutoPlay(true);
 *
 *     // Create the view.
 *     MediaView mediaView = MediaView.create(mediaPlayer);
 *
 *     // Create the Scene and add the view.
 *     Scene scene = new Scene(mediaView, 640, 480);
 *     stage.setScene(scene);
 *
 *     // Name and display the Stage.
 *     stage.setTitle("Hello JPro Media Player");
 *     stage.show();
 * }
 * }</pre>
 *
 * <p>The next code snippet provides a simple example of how to display the video
 * from the camera while recording:</p>
 *
 * <pre>{@code
 * public void start(Stage stage) {
 *     // Create the camera recorder.
 *     MediaRecorder mediaRecorder = MediaRecorder.create(stage);
 *
 *     // Create the enable camera button and add it to a pane.
 *     Button enableCamButton = new Button("Enable Camera");
 *     StackPane previewPane = new StackPane(enableCamButton);
 *
 *     // Create the camera view.
 *     MediaView cameraView = MediaView.create(mediaRecorder);
 *
 *     // Set an event handler to enable the camera recording.
 *     enableCamButton.setOnAction(event -> {
 *          mediaRecorder.enable();
 *          previewPane.getChildren().setAll(cameraView);
 *     });
 *
 *     // Create the Scene and add the preview pane.
 *     Scene scene = new Scene(previewPane, 640, 480);
 *     stage.setScene(scene);
 *
 *     // Name and display the Stage.
 *     stage.setTitle("Hello JPro Media Player");
 *     stage.show();
 * }
 * }</pre>
 *
 * @see MediaPlayer
 * @see MediaRecorder
 *
 * @author Besmir Beqiri
 */
public abstract class MediaView extends Region {

    public static final String DEFAULT_STYLE_CLASS = "media-view";

    /**
     * Creates a media view. If the application is running in a
     * browser via JPro server, then a web version of {@link MediaView}
     * is returned. If the application is not running inside the browser
     * than a desktop/mobile version of the {@link MediaView} is returned.
     *
     * @param stage the application stage
     * @return a {@link MediaView} object.
     */
    public static MediaView create(Stage stage) {
        if (WebAPI.isBrowser()) {
            final WebAPI webAPI = WebAPI.getWebAPI(stage);
            return new WebMediaPlayerView(webAPI);
        }
        return new FXMediaPlayerView();
    }

    /**
     * Creates a media view for the given media player. If the application
     * is running in a browser via JPro server, then a web version of {@link MediaView}
     * is returned. If the application is not running inside the browser
     * than a desktop/mobile version of the {@link MediaView} is returned.
     *
     * @param mediaPlayer the media player
     * @return a {@link MediaView} object.
     */
    public static MediaView create(MediaPlayer mediaPlayer) {
        if (mediaPlayer instanceof FXMediaPlayer fxMediaPlayer) {
            return new FXMediaPlayerView(fxMediaPlayer);
        } else if (mediaPlayer instanceof WebMediaPlayer webMediaPlayer) {
            return new WebMediaPlayerView(webMediaPlayer);
        } else {
            throw new IllegalArgumentException("Unsupported MediaPlayer implementation: " +
                    mediaPlayer.getClass().getName());
        }
    }

    /**
     * Creates a media view for the given media recorder. If the application
     * is running in a browser via JPro server, then a web version of {@link MediaView}
     * is returned. If the application is not running inside the browser
     * than a desktop/mobile version of the {@link MediaView} is returned.
     *
     * @param mediaRecorder the media recorder
     * @return a {@link MediaView} object.
     */
    public static MediaView create(MediaRecorder mediaRecorder) {
        if (mediaRecorder instanceof NativeMediaRecorder fxMediaRecorder) {
            return new NativeMediaRecorderView(fxMediaRecorder);
        } else if (mediaRecorder instanceof WebMediaRecorder webMediaRecorder) {
            return new WebMediaRecorderView(webMediaRecorder);
        } else {
            throw new IllegalArgumentException("Unsupported MediaRecorder implementation: " +
                    mediaRecorder.getClass().getName());
        }
    }

    // media player property
    protected ObjectProperty<MediaEngine> mediaEngine;

    /**
     * Return the current media engine.
     *
     * @return the current media engine
     */
    public final MediaEngine getMediaEngine() {
        return mediaEngine == null ? null : mediaEngine.get();
    }

    /**
     * Sets the current media engine, like a {@link MediaPlayer} or a {@link MediaRecorder}.
     *
     * @param value a {@link MediaEngine} implementation object, like
     *              a {@link MediaPlayer} or {@link MediaRecorder}
     */
    public final void setMediaEngine(MediaEngine value) {
        mediaEngineProperty().set(value);
    }

    /**
     * Attach the media engine that could be a {@link MediaPlayer} or {@link MediaRecorder}.
     * A specific {@link MediaView} is created for the given media engine, depending
     * on whenever the application is running on desktop/mobile or web via the JPro server.
     */
    public abstract ObjectProperty<MediaEngine> mediaEngineProperty();

    // preserve ratio property
    protected BooleanProperty preserveRatio;

    /**
     * Returns whether the media aspect ratio is preserved when scaling.
     *
     * @return whether the media aspect ratio is preserved.
     */
    public final boolean isPreserveRatio() {
        return preserveRatio == null || preserveRatio.get();
    }

    /**
     * Sets whether to preserve the media aspect ratio when scaling.
     *
     * @param value whether to preserve the media aspect ratio.
     */
    public final void setPreserveRatio(boolean value) {
        preserveRatioProperty().set(value);
    }

    /**
     * Whether to preserve the aspect ratio (width / height) of the media when
     * scaling it to fit the view. If the aspect ratio is not preserved, the
     * media will be stretched or sheared in both dimensions to fit the
     * dimensions of the node. The default value is <code>true</code>.
     */
    public abstract BooleanProperty preserveRatioProperty();

    // fitWidth property
    protected DoubleProperty fitWidth;

    /**
     * Retrieves the width of the bounding box of the resized media.
     * Default value is <code>-1.0</code>.
     *
     * @return the height of the resized media.
     */
    public final double getFitWidth() {
        return fitWidth == null ? -1.0 : fitWidth.get();
    }

    /**
     * Sets the width of the bounding box of the resized media.
     * If <code>value &lt; 0</code>, than the width of the
     * bounding box will match the width of the parent container node.
     *
     * @param value the width of the resized media.
     */
    public final void setFitWidth(double value) {
        fitWidthProperty().set(value);
    }

    /**
     * Determines the width of the bounding box within which the source media is
     * resized as necessary to fit. If <code>value &lt; 0</code>, than the width
     * of the bounding box will match the width of the parent container node.
     * <p>
     * See {@link #preserveRatioProperty preserveRatio} for information on interaction
     * between media views <code>fitWidth</code>, <code>fitHeight</code> and
     * <code>preserveRatio</code> attributes.
     */
    public abstract DoubleProperty fitWidthProperty();

    // fitHeight property
    protected DoubleProperty fitHeight;

    /**
     * Retrieves the height of the bounding box of the resized media.
     * Default value is <code>-1.0</code>.
     *
     * @return the height of the resized media.
     */
    public final double getFitHeight() {
        return fitHeight == null ? -1.0 : fitHeight.get();
    }

    /**
     * Sets the height of the bounding box of the resized media.
     * If <code>value &lt; 0</code>, than the height of the
     * bounding box will match the height of the parent container node.
     *
     * @param value the height of the resized media.
     */
    public final void setFitHeight(double value) {
        fitHeightProperty().set(value);
    }

    /**
     * Determines the height of the bounding box within which the source media is
     * resized as necessary to fit. If <code>value &lt; 0</code>, than the height
     * of the bounding box will match the height of the parent container node.
     * <p>
     * See {@link #preserveRatioProperty preserveRatio} for information on interaction
     * between media views <code>fitWidth</code>, <code>fitHeight</code> and
     * <code>preserveRatio</code> attributes.
     * </p>
     */
    public abstract DoubleProperty fitHeightProperty();

    @Override
    protected void layoutChildren() {
        for (Node child : getManagedChildren()) {
            if (child instanceof javafx.scene.media.MediaView fxMediaView) {
                if (getFitWidth() < 0) {
                    fxMediaView.setFitWidth(getWidth());
                }
                if (getFitHeight() < 0) {
                    fxMediaView.setFitHeight(getHeight());
                }
            }
            layoutInArea(child, 0.0, 0.0, getWidth(), getHeight(),
                    0.0, HPos.CENTER, VPos.CENTER);
        }
    }
}

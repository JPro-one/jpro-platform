package one.jpro.platform.media.recorder;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.scene.image.ImageView;
import one.jpro.platform.media.MediaEngine;
import one.jpro.platform.media.MediaView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MediaView} implementation for desktop/mobile {@link MediaRecorder}.
 *
 * @author Besmir Beqiri
 */
public class NativeMediaRecorderView extends MediaView {

    private static final Logger logger = LoggerFactory.getLogger(NativeMediaRecorderView.class);

    private ImageView fxFrameView;

    public NativeMediaRecorderView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    public NativeMediaRecorderView(NativeMediaRecorder mediaRecorder) {
        this();
        setMediaEngine(mediaRecorder);
    }

    @Override
    public final ObjectProperty<MediaEngine> mediaEngineProperty() {
        if (mediaEngine == null) {
            mediaEngine = new SimpleObjectProperty<>(this, "mediaEngine") {

                @Override
                protected void invalidated() {
                    sceneProperty().removeListener(weakUpdateViewContainerListener);
                    updateViewContainer();
                    sceneProperty().addListener(weakUpdateViewContainerListener);
                }
            };
        }
        return mediaEngine;
    }

    @Override
    public final DoubleProperty fitWidthProperty() {
        if (fitWidth == null) {
            fitWidth = new SimpleDoubleProperty(this, "fitWidth") {

                @Override
                protected void invalidated() {
                    setInternalFitWidth(get());
                }
            };
        }
        return fitWidth;
    }

    @Override
    public final DoubleProperty fitHeightProperty() {
        if (fitHeight == null) {
            fitHeight = new SimpleDoubleProperty(this, "fitHeight") {

                @Override
                protected void invalidated() {
                    setInternalFitHeight(get());
                }
            };
        }
        return fitHeight;
    }

    @Override
    public final BooleanProperty preserveRatioProperty() {
        if (preserveRatio == null) {
            preserveRatio = new SimpleBooleanProperty(this, "preserveRatio", true) {

                @Override
                protected void invalidated() {
                    if (fxFrameView != null) {
                        fxFrameView.setPreserveRatio(get());
                    }
                    logger.trace("preserve ratio: {}", isPreserveRatio());
                }
            };
        }
        return preserveRatio;
    }

    private void setInternalFitWidth(double fitWidth) {
        if (fxFrameView != null) {
            if (fitWidth == 0) {
                getChildren().remove(fxFrameView);
            } else if (!getChildren().contains(fxFrameView)) {
                getChildren().add(fxFrameView);
            }
            fxFrameView.setFitWidth(fitWidth);
            logger.trace("video width: {}", fitWidth);
        }
    }

    private void setInternalFitHeight(double fitHeight) {
        if (fxFrameView != null) {
            if (fitHeight == 0) {
                getChildren().remove(fxFrameView);
            } else if (!getChildren().contains(fxFrameView)) {
                getChildren().add(fxFrameView);
            }
            fxFrameView.setFitHeight(fitHeight);
            logger.trace("video height: " + fitHeight);
        }
    }

    private final InvalidationListener updateViewContainerListener = observable -> updateViewContainer();
    private final WeakInvalidationListener weakUpdateViewContainerListener =
            new WeakInvalidationListener(updateViewContainerListener);

    private void updateViewContainer() {
        if (getScene() != null && getMediaEngine() instanceof NativeMediaRecorder fxMediaRecorder) {
            fxFrameView = fxMediaRecorder.getCameraView();
            fxFrameView.setPreserveRatio(isPreserveRatio());
            setInternalFitWidth(getFitWidth());
            setInternalFitHeight(getFitHeight());
        }
    }
}

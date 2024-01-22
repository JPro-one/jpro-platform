package one.jpro.platform.media;

import com.jpro.webapi.HTMLView;
import com.jpro.webapi.JSVariable;
import com.jpro.webapi.WebAPI;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import one.jpro.platform.media.recorder.WebMediaRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * {@link MediaView} base implementation for the web.
 *
 * @author Besmir Beqiri
 */
public abstract class WebMediaView extends MediaView {

    private static final Logger log = LoggerFactory.getLogger(WebMediaView.class);

    private final WebAPI webAPI;
    private JSVariable mediaContainerElement;

    public WebMediaView(WebAPI webAPI) {
        this.webAPI = Objects.requireNonNull(webAPI, "WebAPI must not be null.");
        initialize();
    }

    protected void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        final HTMLView viewContainer = new HTMLView();
        mediaContainerElement = webAPI.getHTMLViewElement(viewContainer);
        getChildren().setAll(viewContainer);
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

    // disable controls property
    private BooleanProperty showControls;

    public final boolean isShowControls() {
        return showControls != null && showControls.get();
    }

    public final void setShowControls(boolean showControls) {
        showControlsProperty().set(showControls);
    }

    public final BooleanProperty showControlsProperty() {
        if (showControls == null) {
            showControls = new SimpleBooleanProperty(this, "showControls") {
                @Override
                protected void invalidated() {
                    if (getMediaEngine() instanceof WebMediaEngine webMediaEngine) {
                        webAPI.executeScript("""
                                %s.controls = $controls;
                                """.formatted(webMediaEngine.getVideoElement().getName())
                                .replace("$controls", String.valueOf(get())));
                    }
                }
            };
        }
        return showControls;
    }

    @Override
    public final DoubleProperty fitWidthProperty() {
        if (fitWidth == null) {
            fitWidth = new SimpleDoubleProperty(this, "fitWidth") {

                @Override
                protected void invalidated() {
                    if (getMediaEngine() instanceof WebMediaEngine webMediaEngine) {
                        updateVideoElementWidth(webMediaEngine);
                    }
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
                    if (getMediaEngine() instanceof WebMediaEngine webMediaEngine) {
                        updateVideoElementHeight(webMediaEngine);
                    }
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
                    final boolean preserveRatio = get();
                    if (getMediaEngine() instanceof WebMediaEngine webMediaEngine) {
                        if (preserveRatio) {
                            webAPI.executeScript("""
                                    %s.style.objectFit = 'contain';
                                    """.formatted(webMediaEngine.getVideoElement().getName()));
                        } else {
                            webAPI.executeScript("""
                                    %s.style.objectFit = 'fill';
                                    """.formatted(webMediaEngine.getVideoElement().getName()));
                        }
                        log.trace("preserve ratio: " + preserveRatio);
                    }
                }
            };
        }
        return preserveRatio;
    }

    @Override
    protected void layoutChildren() {
        for (Node child : getManagedChildren()) {
            if (getMediaEngine() instanceof WebMediaEngine webMediaEngine) {
                updateVideoElementSize(webMediaEngine);
            }
            layoutInArea(child, 0.0, 0.0, getWidth(), getHeight(),
                    0.0, HPos.CENTER, VPos.CENTER);
        }
    }

    private void updateVideoElementWidth(WebMediaEngine webMediaEngine) {
        if (getFitWidth() < 0) {
            webAPI.executeScript("""
                    %s.width = "%s";
                    """.formatted(webMediaEngine.getVideoElement().getName(), getWidth()));
        } else {
            webAPI.executeScript("""
                    %s.width = "%s";
                    """.formatted(webMediaEngine.getVideoElement().getName(), getFitWidth()));
        }
    }

    private void updateVideoElementHeight(WebMediaEngine webMediaEngine) {
        if (getFitHeight() < 0) {
            webAPI.executeScript("""
                    %s.height = "%s";
                    """.formatted(webMediaEngine.getVideoElement().getName(), getHeight()));
        } else {
            webAPI.executeScript("""
                    %s.height = "%s";
                    """.formatted(webMediaEngine.getVideoElement().getName(), getFitHeight()));
        }
    }

    private void updateVideoElementSize(WebMediaEngine webMediaEngine) {
        updateVideoElementWidth(webMediaEngine);
        updateVideoElementHeight(webMediaEngine);
    }

    private final InvalidationListener updateViewContainerListener = observable -> updateViewContainer();
    private final WeakInvalidationListener weakUpdateViewContainerListener =
            new WeakInvalidationListener(updateViewContainerListener);

    private void updateViewContainer() {
        if (getScene() != null && getMediaEngine() instanceof WebMediaEngine webMediaEngine) {
            webAPI.executeScript("""
                    // clear all elements
                    while ($mediaContainer.firstChild) {
                        $mediaContainer.removeChild($mediaContainer.firstChild);
                    }
                    // add new element
                    let videoElement = %s;
                    videoElement.controls = $controls;
                    $mediaContainer.appendChild(videoElement);
                    """.replace("$mediaContainer", mediaContainerElement.getName())
                    .replace("$controls", String.valueOf(isShowControls()))
                    .formatted(webMediaEngine.getVideoElement().getName()));

            updateVideoElementSize(webMediaEngine);

            if (webMediaEngine instanceof WebMediaRecorder) {
                webAPI.executeScript("""
                        let videoElement = %s;
                        videoElement.play();
                        """.formatted(webMediaEngine.getVideoElement().getName()));
            }
        }
    }
}

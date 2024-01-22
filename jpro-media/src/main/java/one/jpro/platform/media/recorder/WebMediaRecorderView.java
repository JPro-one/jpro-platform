package one.jpro.platform.media.recorder;

import com.jpro.webapi.WebAPI;
import one.jpro.platform.media.MediaView;
import one.jpro.platform.media.WebMediaView;

/**
 * {@link MediaView} implementation for a web {@link MediaRecorder}.
 *
 * @author Besmir Beqiri
 */
public class WebMediaRecorderView extends WebMediaView {

    public WebMediaRecorderView(WebAPI webAPI) {
        super(webAPI);
    }

    public WebMediaRecorderView(WebMediaRecorder webMediaRecorder) {
        this(webMediaRecorder.getWebAPI());
        setMediaEngine(webMediaRecorder);
    }
}

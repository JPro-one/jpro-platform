package one.jpro.platform.media.recorder;

import com.jpro.webapi.JSVariable;
import com.jpro.webapi.WebAPI;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.Event;
import one.jpro.platform.media.MediaSource;
import one.jpro.platform.media.WebMediaEngine;
import one.jpro.platform.media.event.MediaRecorderEvent;
import org.json.JSONObject;

import java.util.Objects;

/**
 * {@link MediaRecorder} implementation for the web.
 *
 * @author Besmir Beqiri
 */
public class WebMediaRecorder extends BaseMediaRecorder implements WebMediaEngine {

    private static final String DEFAULT_MIME_TYPE = "video/webm";

    private final WebAPI webAPI;

    private final String mediaRecorderRef;
    private final String blobsRecordedRef;

    private final JSVariable recorderVideoElement;

    /**
     * Creates a new MediaRecorder object.
     *
     * @param webAPI JPro WebAPI
     */
    public WebMediaRecorder(WebAPI webAPI) {
        this.webAPI = Objects.requireNonNull(webAPI, "WebAPI must not be null.");

        final String recorderId = webAPI.createUniqueJSName("recorder_");
        mediaRecorderRef = "media_" + recorderId;
        blobsRecordedRef = "blobs_" + recorderId;
        recorderVideoElement = createRecorderVideoElement("video_elem_" + recorderId);

        webAPI.registerJavaFunction(mediaRecorderRef + "_ready", result -> {
            recorderReady = true;

            // Set status to ready
            setStatus(Status.READY);

            // Fire ready event
            Event.fireEvent(WebMediaRecorder.this,
                    new MediaRecorderEvent(WebMediaRecorder.this,
                            MediaRecorderEvent.MEDIA_RECORDER_READY));
        });

        webAPI.registerJavaFunction(mediaRecorderRef + "_onstart", result -> {
            // Set status
            Status.fromJS(result).ifPresent(this::setStatus);

            // Fire start event
            Event.fireEvent(WebMediaRecorder.this,
                    new MediaRecorderEvent(WebMediaRecorder.this,
                            MediaRecorderEvent.MEDIA_RECORDER_START));
        });

        webAPI.registerJavaFunction(mediaRecorderRef + "_onpause", result -> {
            // Set status
            Status.fromJS(result).ifPresent(this::setStatus);

            // Fire pause event
            Event.fireEvent(WebMediaRecorder.this,
                    new MediaRecorderEvent(WebMediaRecorder.this,
                            MediaRecorderEvent.MEDIA_RECORDER_PAUSE));
        });

        webAPI.registerJavaFunction(mediaRecorderRef + "_onresume", result -> {
            // Set status
            Status.fromJS(result).ifPresent(this::setStatus);

            // Fire resume event
            Event.fireEvent(WebMediaRecorder.this,
                    new MediaRecorderEvent(WebMediaRecorder.this,
                            MediaRecorderEvent.MEDIA_RECORDER_RESUME));
        });

        webAPI.registerJavaFunction(mediaRecorderRef + "_onstop", result -> {
            JSONObject json = new JSONObject(result
                    .substring(1, result.length() - 1)
                    .replace("\\", ""));
            final String objectUrl = json.getString("objectUrl");
            final long fileSize = json.getLong("fileSize");

            // Update ObjectURL value;
            setMediaSource(new MediaSource(webAPI.createJSFile(objectUrl, "RecordedVideo", fileSize)));

            // Set status to inactive
            setStatus(Status.INACTIVE);

            // Fire stop event
            Event.fireEvent(WebMediaRecorder.this,
                    new MediaRecorderEvent(WebMediaRecorder.this, MediaRecorderEvent.MEDIA_RECORDER_STOP));
        });

        webAPI.registerJavaFunction(mediaRecorderRef + "_ondataavailable", result -> {
            // Fire data available event
            Event.fireEvent(WebMediaRecorder.this,
                    new MediaRecorderEvent(WebMediaRecorder.this,
                            MediaRecorderEvent.MEDIA_RECORDER_DATA_AVAILABLE));
        });

        webAPI.registerJavaFunction(mediaRecorderRef + "_onerror", result -> {
            // Set status to inactive
            setStatus(Status.INACTIVE);

            // Set error
            setError(MediaRecorderException.fromJSON(result));
        });
    }

    @Override
    public final WebAPI getWebAPI() {
        return webAPI;
    }

    @Override
    public final JSVariable getVideoElement() {
        return recorderVideoElement;
    }

    /**
     * Returns a boolean which is <code>true</code> if the MIME type specified is one that this media recorder should
     * be able to successfully record.
     *
     * @param mimeType the MIME media type to check
     * @return <code>true</code> if this MediaRecorder implementation is capable of recording Blob objects for the
     * specified MIME type. Recording may still fail if there are insufficient resources to support the recording
     * and encoding process. If the value is <code>false</code>, the user agent is incapable of recording the
     * specified format.
     * @throws Exception
     */
    public final boolean isTypeSupported(String mimeType) throws Exception {
        return Boolean.getBoolean(webAPI.executeScriptWithReturn("""
                MediaRecorder.isTypeSupported("%s")
                """.formatted(mimeType)));
    }

    // mimeType property (read-only)
    private ReadOnlyStringWrapper mimeType;

    public final String getMimeType() {
        return (mimeType == null) ? DEFAULT_MIME_TYPE : mimeType.get();
    }

    private void setMimeType(String value) {
        mimeTypePropertyImpl().set(value);
    }

    public final ReadOnlyStringProperty mimeTypeProperty() {
        return mimeTypePropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyStringWrapper mimeTypePropertyImpl() {
        if (mimeType == null) {
            mimeType = new ReadOnlyStringWrapper(this, "mimeType", DEFAULT_MIME_TYPE);
        }
        return mimeType;
    }

    // Recorder controller methods
    @Override
    public final void enable() {
        final var mediaRecorderOptions = new MediaRecorderOptions().mimeType(getMimeType());
        webAPI.executeScript("""
                $blobsRecorded = []; // stream buffer
                navigator.mediaDevices.getUserMedia({ video: true, audio: true })
                    .then((stream) => {
                        $recorderVideoElem.srcObject = stream;
                        $mediaRecorder = new MediaRecorder($recorderVideoElem.srcObject, $videoRecorderOptions);
                        
                        // recorder is ready
                        jpro.$mediaRecorder_ready();
                        
                        // event : new recorded video blob available
                        $mediaRecorder.addEventListener('dataavailable', function(e) {
                            $blobsRecorded.push(e.data);
                            jpro.$mediaRecorder_ondataavailable(e.timecode);
                        });
                        
                        // event : recording stopped & all blobs sent
                        $mediaRecorder.onstop = (event) => {
                            // create local object URL from the recorded video blobs
                            let recordedBlob = new Blob($blobsRecorded, { type: "video/webm" });
                            let videoUrl = URL.createObjectURL(recordedBlob);
                            // pas object url and file size as json format
                            jpro.$mediaRecorder_onstop(JSON.stringify({
                                objectUrl: videoUrl,
                                fileSize: recordedBlob.size
                            }));
                        }
                    
                        $mediaRecorder.onerror = (event) => {
                            // pas error type and message as json format
                            jpro.$mediaRecorder_onerror(JSON.stringify({
                                type: event.error.code,
                                message: event.error.message
                            }));
                        }
                                                                               
                        $mediaRecorder.onstart = (event) => jpro.$mediaRecorder_onstart($mediaRecorder.state)
                        $mediaRecorder.onpause = (event) => jpro.$mediaRecorder_onpause($mediaRecorder.state)
                        $mediaRecorder.onresume = (event) => jpro.$mediaRecorder_onresume($mediaRecorder.state)
                    });
                """
                .replace("$recorderVideoElem", recorderVideoElement.getName())
                .replace("$videoRecorderOptions", mediaRecorderOptions.toJSON().toString())
                .replace("$blobsRecorded", blobsRecordedRef)
                .replace("$mediaRecorder", mediaRecorderRef));
    }

    @Override
    public final void start() {
        if (recorderReady) {
            if (getStatus().equals(Status.INACTIVE) || getStatus().equals(Status.READY)) {
                webAPI.executeScript("""
                        $blobsRecorded = []; // clear recorded buffer
                        $mediaRecorder.start(1000); // start recording with a timeslice of 1 second
                        """
                        .replace("$blobsRecorded", blobsRecordedRef)
                        .replace("$mediaRecorder", mediaRecorderRef));
            } else if (getStatus().equals(Status.PAUSED)) {
                webAPI.executeScript("""
                        if ($mediaRecorder.state === "paused") {
                            $mediaRecorder.resume();
                        }
                        """.replace("$mediaRecorder", mediaRecorderRef));
            }
        }
    }

    @Override
    public final void pause() {
        if (recorderReady) {
            webAPI.executeScript("""
                    if ($mediaRecorder.state === "recording") {
                        $mediaRecorder.pause();
                    }
                    """.replace("$mediaRecorder", mediaRecorderRef));
        }
    }

    @Override
    public final void stop() {
        if (recorderReady) {
            webAPI.executeScript("""
                    $mediaRecorder.stop();
                    """.replace("$mediaRecorder", mediaRecorderRef));
        }
    }

    private JSVariable createRecorderVideoElement(String recorderVideoElem) {
        webAPI.executeScript("""
                $recorderVideoElem = document.createElement("video");
                $recorderVideoElem.controls = false;
                $recorderVideoElem.autoplay = true;
                $recorderVideoElem.muted = true;
                $recorderVideoElem.setAttribute("webkit-playsinline", 'webkit-playsinline');
                $recorderVideoElem.setAttribute("playsinline", 'playsinline');
                """.replace("$recorderVideoElem", recorderVideoElem));
        return new JSVariable(webAPI, recorderVideoElem);
    }
}

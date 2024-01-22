package one.jpro.platform.media.recorder;

import org.json.JSONObject;

import java.util.Optional;

/**
 * Media recorder options for the {@link WebMediaRecorder}.
 *
 * @author Besmir Beqiri
 */
public class MediaRecorderOptions {

    private String mimeType;
    private Number audioBitsPerSecond;
    private Number videoBitsPerSecond;
    private Number bitsPerSecond;

    /**
     * Returns a MIME type specifying the format for the resulting media.
     *
     * @return a string form of the MIME type
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * A MIME type specifying the format for the resulting media; you may specify the container format
     * (the browser will select its preferred codecs for audio and/or video), or you may use the codecs
     * parameter and/or the profiles parameter to provide detailed information about which codecs to use
     * and how to configure them. Applications can check in advance if a mimeType is supported by
     * the user agent by calling MediaRecorder.isTypeSupported().
     */
    public MediaRecorderOptions mimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    /**
     * Returns the audio bitrate of the media.
     *
     * @return the audio bitrate per second
     */
    public Number getAudioBitsPerSecond() {
        return audioBitsPerSecond;
    }

    /**
     * The chosen bitrate for the audio component of the media.
     */
    public MediaRecorderOptions audioBitsPerSecond(int audioBitsPerSecond) {
        this.audioBitsPerSecond = audioBitsPerSecond;
        return this;
    }

    /**
     * Returns the audio bitrate of the media.
     *
     * @return the audio bitrate per second
     */
    public Number getVideoBitsPerSecond() {
        return videoBitsPerSecond;
    }

    /**
     * The chosen bitrate for the video component of the media.
     */
    public MediaRecorderOptions videoBitsPerSecond(int videoBitsPerSecond) {
        this.videoBitsPerSecond = videoBitsPerSecond;
        return this;
    }

    public Number getBitsPerSecond() {
        return bitsPerSecond;
    }

    /**
     * The chosen bitrate for the audio and video components of the media.
     * This can be specified instead of the above two properties. If this is specified along with one
     * or the other of the above properties, this will be used for the one that isn't specified.
     *
     * @return a number value.
     */
    public MediaRecorderOptions bitsPerSecond(int bitsPerSecond) {
        this.bitsPerSecond = bitsPerSecond;
        return this;
    }

    /**
     * Return the JSON representation for this object.
     *
     * @return a json object
     */
    public JSONObject toJSON() {
        final JSONObject json = new JSONObject();
        Optional.ofNullable(getMimeType())
                .ifPresent(mimeType -> json.put("mimeType", mimeType));
        Optional.ofNullable(getAudioBitsPerSecond())
                .ifPresent(audioBitsPerSecond -> json.put("audioBitsPerSecond", audioBitsPerSecond));
        Optional.ofNullable(getVideoBitsPerSecond())
                .ifPresent(videoBitsPerSecond -> json.put("videoBitsPerSecond", videoBitsPerSecond));
        Optional.ofNullable(getBitsPerSecond())
                .ifPresent(bitsPerSecond -> json.put("bitsPerSecond", bitsPerSecond));
        return json;
    }
}

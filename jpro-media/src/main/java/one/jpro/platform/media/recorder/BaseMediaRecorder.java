package one.jpro.platform.media.recorder;

import com.sun.javafx.event.EventHandlerManager;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventHandler;
import javafx.util.Duration;
import one.jpro.platform.media.MediaSource;
import one.jpro.platform.media.event.MediaRecorderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Base class for {@link MediaRecorder} implementations.
 *
 * @author Besmir Beqiri
 */
abstract class BaseMediaRecorder implements MediaRecorder {

    private static final Logger logger = LoggerFactory.getLogger(BaseMediaRecorder.class);

    private RecorderTimerTask recorderTimerTask;
    volatile boolean recorderReady;
    volatile boolean isUpdateDurationEnabled;
    long startRecordingTime = 0;
    private double pauseDurationTime = 0;

    // media source property (read-only)
    ReadOnlyObjectWrapper<MediaSource> mediaSource;

    @Override
    public final MediaSource getMediaSource() {
        return (mediaSource == null) ? null : mediaSource.get();
    }

    final void setMediaSource(MediaSource value) {
        mediaResourcePropertyImpl().set(value);
    }

    @Override
    public final ReadOnlyObjectProperty<MediaSource> mediaSourceProperty() {
        return mediaResourcePropertyImpl().getReadOnlyProperty();
    }

    ReadOnlyObjectWrapper<MediaSource> mediaResourcePropertyImpl() {
        if (mediaSource == null) {
            mediaSource = new ReadOnlyObjectWrapper<>(this, "mediaSource");
        }
        return mediaSource;
    }

    // status property
    private ReadOnlyObjectWrapper<Status> status;

    @Override
    public final Status getStatus() {
        return (status == null) ? Status.INACTIVE : status.get();
    }

    final void setStatus(Status value) {
        statusPropertyImpl().set(value);
    }

    /**
     * The current status of the MediaRecorder object (inactive, recording, or paused.)
     */
    @Override
    public final ReadOnlyObjectProperty<Status> statusProperty() {
        return statusPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyObjectWrapper<Status> statusPropertyImpl() {
        if (status == null) {
            status = new ReadOnlyObjectWrapper<>(this, "status", Status.INACTIVE) {

                @Override
                protected void invalidated() {
                    switch (get()) {
                        case READY -> {
                            startRecordingTime = 0;
                            pauseDurationTime = 0;
                        }
                        case RECORDING -> {
                            startRecordingTime = 0;
                            createRecorderTimer();
                        }
                        case PAUSED -> {
                            isUpdateDurationEnabled = false;
                            pauseDurationTime += System.currentTimeMillis() - startRecordingTime;
                        }
                        case INACTIVE -> {
                            startRecordingTime = 0;
                            pauseDurationTime = 0;
                            destroyerRecorderTimer();
                        }
                    }
                }
            };
        }
        return status;
    }

    // duration property
    private ReadOnlyObjectWrapper<Duration> duration;

    @Override
    public Duration getDuration() {
        return (duration == null) ? Duration.ZERO : duration.get();
    }

    final void setDuration(Duration value) {
        durationPropertyImpl().set(value);
    }

    @Override
    public final ReadOnlyObjectProperty<Duration> durationProperty() {
        return durationPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyObjectWrapper<Duration> durationPropertyImpl() {
        if (duration == null) {
            duration = new ReadOnlyObjectWrapper<>(this, "duration", Duration.ZERO) {

                @Override
                protected void invalidated() {
                    logger.trace("Recording duration: {} s", get().toSeconds());
                }
            };
        }
        return duration;
    }

    // On ready event handler
    private ObjectProperty<EventHandler<MediaRecorderEvent>> onReady;

    @Override
    public final EventHandler<MediaRecorderEvent> getOnReady() {
        return onReady == null ? null : onReady.get();
    }

    @Override
    public final void setOnReady(EventHandler<MediaRecorderEvent> value) {
        onReadyProperty().set(value);
    }

    @Override
    public final ObjectProperty<EventHandler<MediaRecorderEvent>> onReadyProperty() {
        if (onReady == null) {
            onReady = new SimpleObjectProperty<>(this, "onReady") {
                @Override
                protected void invalidated() {
                    eventHandlerManager.setEventHandler(MediaRecorderEvent.MEDIA_RECORDER_READY, get());
                }
            };
        }
        return onReady;
    }

    // On data available event handler
    private ObjectProperty<EventHandler<MediaRecorderEvent>> onDataAvailable;

    @Override
    public final EventHandler<MediaRecorderEvent> getOnDataAvailable() {
        return (onDataAvailable == null) ? null : onDataAvailable.get();
    }

    @Override
    public final void setOnDataAvailable(EventHandler<MediaRecorderEvent> value) {
        onDataAvailableProperty().set(value);
    }

    @Override
    public final ObjectProperty<EventHandler<MediaRecorderEvent>> onDataAvailableProperty() {
        if (onDataAvailable == null) {
            onDataAvailable = new SimpleObjectProperty<>(this, "onDataAvailable") {

                @Override
                protected void invalidated() {
                    eventHandlerManager.setEventHandler(MediaRecorderEvent.MEDIA_RECORDER_DATA_AVAILABLE, get());
                }
            };
        }
        return onDataAvailable;
    }

    // On start event handler
    private ObjectProperty<EventHandler<MediaRecorderEvent>> onStart;

    @Override
    public final EventHandler<MediaRecorderEvent> getOnStart() {
        return (onStart == null) ? null : onStart.get();
    }

    @Override
    public final void setOnStart(EventHandler<MediaRecorderEvent> value) {
        onStartProperty().set(value);
    }

    @Override
    public final ObjectProperty<EventHandler<MediaRecorderEvent>> onStartProperty() {
        if (onStart == null) {
            onStart = new SimpleObjectProperty<>(this, "onStart") {

                @Override
                protected void invalidated() {
                    eventHandlerManager.setEventHandler(MediaRecorderEvent.MEDIA_RECORDER_START, get());
                }
            };
        }
        return onStart;
    }

    // On pause event handler
    private ObjectProperty<EventHandler<MediaRecorderEvent>> onPaused;

    @Override
    public final EventHandler<MediaRecorderEvent> getOnPaused() {
        return (onPaused == null) ? null : onPaused.get();
    }

    @Override
    public final void setOnPaused(EventHandler<MediaRecorderEvent> value) {
        onPausedProperty().set(value);
    }

    @Override
    public final ObjectProperty<EventHandler<MediaRecorderEvent>> onPausedProperty() {
        if (onPaused == null) {
            onPaused = new SimpleObjectProperty<>(this, "onPaused") {

                @Override
                protected void invalidated() {
                    eventHandlerManager.setEventHandler(MediaRecorderEvent.MEDIA_RECORDER_PAUSE, get());
                }
            };
        }
        return onPaused;
    }

    // On resume event handler
    private ObjectProperty<EventHandler<MediaRecorderEvent>> onResume;

    @Override
    public final EventHandler<MediaRecorderEvent> getOnResume() {
        return (onResume == null) ? null : onResume.get();
    }

    @Override
    public final void setOnResume(EventHandler<MediaRecorderEvent> value) {
        onResumeProperty().set(value);
    }

    @Override
    public final ObjectProperty<EventHandler<MediaRecorderEvent>> onResumeProperty() {
        if (onResume == null) {
            onResume = new SimpleObjectProperty<>(this, "onResume") {

                @Override
                protected void invalidated() {
                    eventHandlerManager.setEventHandler(MediaRecorderEvent.MEDIA_RECORDER_RESUME, get());
                }
            };
        }
        return onResume;
    }

    // On stopped event handler
    private ObjectProperty<EventHandler<MediaRecorderEvent>> onStopped;

    @Override
    public final EventHandler<MediaRecorderEvent> getOnStopped() {
        return (onStopped == null) ? null : onStopped.get();
    }

    @Override
    public final void setOnStopped(EventHandler<MediaRecorderEvent> value) {
        onStoppedProperty().set(value);
    }

    @Override
    public final ObjectProperty<EventHandler<MediaRecorderEvent>> onStoppedProperty() {
        if (onStopped == null) {
            onStopped = new SimpleObjectProperty<>(this, "onStopped") {

                @Override
                protected void invalidated() {
                    eventHandlerManager.setEventHandler(MediaRecorderEvent.MEDIA_RECORDER_STOP, get());
                }
            };
        }
        return onStopped;
    }

    // On error event handler
    private ObjectProperty<EventHandler<MediaRecorderEvent>> onError;

    @Override
    public final EventHandler<MediaRecorderEvent> getOnError() {
        return (onError == null) ? null : onError.get();
    }

    @Override
    public final void setOnError(EventHandler<MediaRecorderEvent> value) {
        onErrorProperty().set(value);
    }

    @Override
    public final ObjectProperty<EventHandler<MediaRecorderEvent>> onErrorProperty() {
        if (onError == null) {
            onError = new SimpleObjectProperty<>(this, "onError") {

                @Override
                protected void invalidated() {
                    eventHandlerManager.setEventHandler(MediaRecorderEvent.MEDIA_RECORDER_ERROR, get());
                }
            };
        }
        return onError;
    }

    // Error property
    private ReadOnlyObjectWrapper<MediaRecorderException> error;

    @Override
    public final MediaRecorderException getError() {
        return (error == null) ? null : error.get();
    }

    final void setError(MediaRecorderException error) {
        errorPropertyImpl().set(error);
    }

    @Override
    public final ReadOnlyObjectProperty<MediaRecorderException> errorProperty() {
        return errorPropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyObjectWrapper<MediaRecorderException> errorPropertyImpl() {
        if (error == null) {
            error = new ReadOnlyObjectWrapper<>(this, "error") {

                @Override
                protected void invalidated() {
                    final MediaRecorderException exception = get();
                    if (exception != null) {
                        // Fire error event
                        Event.fireEvent(BaseMediaRecorder.this,
                                new MediaRecorderEvent(BaseMediaRecorder.this,
                                        MediaRecorderEvent.MEDIA_RECORDER_ERROR));
                    }
                }
            };
        }
        return error;
    }

    private final EventHandlerManager eventHandlerManager = new EventHandlerManager(this);

    @Override
    public final EventDispatchChain buildEventDispatchChain(EventDispatchChain tail) {
        return tail.prepend(eventHandlerManager);
    }

    void createRecorderTimer() {
        synchronized (RecorderTimerTask.timerLock) {
            if (recorderTimerTask == null) {
                recorderTimerTask = new RecorderTimerTask(this);
                recorderTimerTask.start();
            }
            isUpdateDurationEnabled = true;
        }
    }

    void destroyerRecorderTimer() {
        synchronized (RecorderTimerTask.timerLock) {
            if (recorderTimerTask != null) {
                isUpdateDurationEnabled = false;
                recorderTimerTask.stop();
                recorderTimerTask = null;
            }
        }
    }

    /**
     * Called periodically to update the current duration of the recording.
     */
    void updateDuration() {
        if (recorderReady && isUpdateDurationEnabled) {
            if (startRecordingTime == 0) startRecordingTime = System.currentTimeMillis();
            long recordingTime = System.currentTimeMillis() - startRecordingTime;
            if (recordingTime >= 0) {
                setDuration(Duration.millis(pauseDurationTime + recordingTime));
            }
        }
    }

    static class RecorderTimerTask extends TimerTask {
        
        static final Object timerLock = new Object();
        
        private Timer recoderTimer;
        private final WeakReference<BaseMediaRecorder> recorderRef;

        RecorderTimerTask(BaseMediaRecorder recorder) {
            recorderRef = new WeakReference<>(recorder);
        }

        void start() {
            if (recoderTimer == null) {
                recoderTimer = new Timer("RecorderTimerTask", true);
                recoderTimer.scheduleAtFixedRate(this, 0, 100 /* period in ms */);
            }
        }

        void stop() {
            if (recoderTimer != null) {
                recoderTimer.cancel();
                recoderTimer = null;
            }
        }

        @Override
        public void run() {
            synchronized (timerLock) {
                BaseMediaRecorder recorder = recorderRef.get();
                if (recorder != null) {
                    Platform.runLater(() -> {
                        synchronized (timerLock) {
                            recorder.updateDuration();
                        }
                    });
                } else {
                    cancel();
                }
            }
        }
    }
}

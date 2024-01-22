package one.jpro.platform.media.recorder;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import one.jpro.platform.media.MediaSource;
import one.jpro.platform.media.event.MediaRecorderEvent;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * {@link MediaRecorder} implementation for the desktop.
 *
 * @author Besmir Beqiri
 */
public class NativeMediaRecorder extends BaseMediaRecorder {

    private static final Logger logger = LoggerFactory.getLogger(NativeMediaRecorder.class);

    private static final Path RECORDING_PATH = Path.of(System.getProperty("user.home"),
            ".jpro", "video", "capture");
    private static final String DIVIDER_LINE =
            CharBuffer.allocate(80).toString().replace('\0', '*');

    // Video resources
    private static final int CAMERA_DEVICE_INDEX = 0; // Use the default system camera
    private static final int FRAME_RATE = 30;
    private static final String MP4_FILE_EXTENSION = ".mp4";
    private FrameGrabber cameraGrabber;
    private FFmpegFrameGrabber micGrabber;
    private final JavaFXFrameConverter cameraFrameConverter;
    private final ImageView cameraView;
    private double frameRate;

    // Audio resources
    private static final int DEFAULT_AUDIO_CHANNELS = 1; // Mono audio
    private static final int DEFAULT_AUDIO_SAMPLE_RATE = 44100; // 44.1 KHz

    // Storage resources
    private FFmpegFrameRecorder recorder;
    private Path tempVideoFile;

    // Recording state
    private volatile long startTime = 0;
    private volatile boolean recording = false;

    // Concurrency and locking resources
    private final ThreadGroup scheduledThreadGroup = new ThreadGroup("Media Recorder thread pool");
    private int threadCounter;
    private final ExecutorService videoExecutorService;
    private final ExecutorService audioExecutorService;
    private final ExecutorService startStopRecordingExecutorService;
    private final Semaphore semaphore = new Semaphore(2);

    /**
     * Default constructor for FXMediaRecorder.
     * Initializes the video and audio capture resources.
     */
    public NativeMediaRecorder() {
        // Set native log level to error
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);

        final ThreadFactory threadFactory = run -> {
            final Thread thread = new Thread(scheduledThreadGroup, run);
            thread.setName("Media Recorder Thread " + threadCounter++);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            return thread;
        };
        videoExecutorService = Executors.newSingleThreadExecutor(threadFactory);
        audioExecutorService = Executors.newSingleThreadExecutor(threadFactory);
        startStopRecordingExecutorService = Executors.newSingleThreadExecutor(threadFactory);

        // Initialize camera frame grabber
        cameraGrabber = (isOsWindows()) ? new VideoInputFrameGrabber(CAMERA_DEVICE_INDEX)
                : new OpenCVFrameGrabber(CAMERA_DEVICE_INDEX);
        // Frame to JavaFX image converter
        cameraFrameConverter = new JavaFXFrameConverter();
        // Use ImageView to show camera grabbed frames
        cameraView = new ImageView();

        // Initialize audio frame grabber
        micGrabber = new FFmpegFrameGrabber(getDefaultAudioInputDevice());
        micGrabber.setFormat(getDefaultAudioInputFormat());
        micGrabber.setAudioChannels(DEFAULT_AUDIO_CHANNELS);
        micGrabber.setSampleRate(DEFAULT_AUDIO_SAMPLE_RATE);

        // Stop and release native resources on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopRecording();
            release();

            // delete temporary video files
            if (Files.exists(RECORDING_PATH)) {
                try (Stream<Path> pathStream = Files.walk(RECORDING_PATH)) {
                    pathStream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }
        }));
    }

    /**
     * Returns the {@link ImageView} instance used to display video frames captured from the camera.
     *
     * @return <code>ImageView</code> instance showing the captured video frames
     */
    public final ImageView getCameraView() {
        return cameraView;
    }

    @Override
    public final void enable() {
        if (recorderReady) {
            logger.info("Media recorder is already enabled.");
        } else {
            logger.debug("Enabling media recorder...");
            enableVideoCapture();
            enableAudioCapture();
        }

        try {
            // Wait for the video and audio capture to be enabled
            semaphore.acquire();
        } catch (InterruptedException ex) {
            setError("Exception during the enabling of video and audio capture.", ex);
        }

        // Set recorder ready
        recorderReady = true;

        // Set status to ready
        setStatus(Status.READY);

        // Fire ready event
        Event.fireEvent(NativeMediaRecorder.this,
                new MediaRecorderEvent(NativeMediaRecorder.this,
                        MediaRecorderEvent.MEDIA_RECORDER_READY));
    }

    /**
     * Initializes and starts video capture from the camera.
     */
    private void enableVideoCapture() {
        logger.debug("Enabling video capture...");
        // Camera frame grabber runnable
        final Runnable frameGrabber = () -> {
            try {
                // Acquire the semaphore
                semaphore.acquire();

                // start the video capture
                cameraGrabber.start();
                frameRate = (cameraGrabber.getFrameRate() < FRAME_RATE) ? FRAME_RATE : cameraGrabber.getFrameRate();
                printCaptureDeviceDescription();
            } catch (FrameGrabber.Exception | InterruptedException ex) {
                setError("Exception during the enabling of video stream from the camera.", ex);
                release();
            } finally {
                // Release the semaphore
                semaphore.release();
                logger.debug("Video capture enabled.");
            }

            // Start the camera frame grabbing, showing and recording task
            try {
                while (recorderReady) {
                    // effectively grab a single frame
                    final Frame videoFrame = cameraGrabber.grabFrame();
                    if (videoFrame != null) {
                        // convert and show the frame
                        updateCameraView(cameraView, cameraFrameConverter.convert(videoFrame));
                        // write the webcam frame if recording started
                        writeVideoFrame(videoFrame);
                    }
                }
            } catch (FrameGrabber.Exception ex) {
                setError("Exception during camera stream frame grabbing.", ex);
                release();
            }
        };

        videoExecutorService.execute(frameGrabber);
    }

    /**
     * Initializes and starts audio capture from the microphone.
     */
    private void enableAudioCapture() {
        logger.debug("Enabling audio capture...");
        final Runnable audioSampleGrabber = () -> {
            try {
                // Acquire the semaphore
                semaphore.acquire();

                // start the audio capture
                micGrabber.start();
            } catch (FrameGrabber.Exception | InterruptedException ex) {
                setError("Exception during the enabling of audio stream from the microphone.", ex);
                release();
            } finally {
                // Release the semaphore
                semaphore.release();
                logger.debug("Audio capture enabled.");
            }

            try {
                while (recorderReady) {
                    // effectively grab a single frame
                    final Frame audioFrame = micGrabber.grabSamples();
                    if (audioFrame != null) {
                        ShortBuffer audioData = (ShortBuffer) audioFrame.samples[0];
                        short[] shorts = new short[audioData.limit()];
                        audioData.get(shorts);
                        audioData.flip();

                        writeAudioSamples(audioData);
                    }
                }
            } catch (FrameGrabber.Exception ex) {
                setError("Exception during microphone stream frame grabbing.", ex);
                release();
            }
        };

        audioExecutorService.execute(audioSampleGrabber);
    }

    /**
     * Records a single video frame.
     *
     * @param frame the video frame to be recorded
     */
    private void writeVideoFrame(Frame frame) {
        if (recorder != null && recording) {
            try {
                recorder.record(frame);
            } catch (FFmpegFrameRecorder.Exception ex) {
                setError("Exception during video frame recording.", ex);
            }
        }
    }

    /**
     * Records audio samples. This method is called for each audio sample captured from the microphone.
     *
     * @param audioSamples the buffer containing audio samples to be recorded
     */
    private void writeAudioSamples(ShortBuffer audioSamples) {
        if (recorder != null && recording) {
            try {
                recorder.recordSamples(audioSamples);
            } catch (FFmpegFrameRecorder.Exception ex) {
                setError("Exception during audio samples recording.", ex);
            }
        }
    }

    @Override
    public final void start() {
        if (getStatus().equals(Status.INACTIVE) || getStatus().equals(Status.READY)) {
            final Runnable startRecordingRunnable = () -> {
                // Start the recording if the camera is enabled
                if (recorderReady) {
                    tempVideoFile = createTempFilename("video_", MP4_FILE_EXTENSION);

                    recorder = new FFmpegFrameRecorder(tempVideoFile.toString(), cameraGrabber.getImageWidth(),
                            cameraGrabber.getImageHeight(), micGrabber.getAudioChannels());
                    recorder.setInterleaved(true);
                    recorder.setVideoOption("tune", "zerolatency"); // low latency for webcam streaming
                    recorder.setVideoOption("preset", "ultrafast"); // low cpu usage for the encoder
                    recorder.setVideoOption("crf", "28");           // video quality
                    recorder.setVideoBitrate(8 * 1024 * 1024);      // 8 Mbps for 1080p
                    recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                    recorder.setFormat("mp4");
                    recorder.setFrameRate(frameRate);
                    recorder.setGopSize((int) (frameRate * 2));
                    recorder.setAudioOption("crf", "0");            // no variable bitrate audio
                    recorder.setAudioQuality(0);                    // highest quality
                    recorder.setAudioBitrate(192000);               // 192 kbps
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                    recorder.setSampleRate(micGrabber.getSampleRate());

                    try {
                        // Enable recording
                        recorder.start();
                        startTime = System.currentTimeMillis();
                        recording = true;

                        Platform.runLater(() -> {
                            // Set status to recording
                            setStatus(Status.RECORDING);

                            // Fire start event
                            Event.fireEvent(NativeMediaRecorder.this,
                                    new MediaRecorderEvent(NativeMediaRecorder.this,
                                            MediaRecorderEvent.MEDIA_RECORDER_START));
                        });
                    } catch (FFmpegFrameRecorder.Exception ex) {
                        setError("Exception on starting the audio/video recorder.", ex);
                    }
                } else {
                    logger.info("Please, enable the camera first!");
                }
            };

            startStopRecordingExecutorService.execute(startRecordingRunnable);
        } else if (getStatus().equals(Status.PAUSED)) {
            if (recorderReady) {
                // enable recording
                recording = true;

                // Set status to recording
                setStatus(Status.RECORDING);

                // Fire start event
                Event.fireEvent(NativeMediaRecorder.this,
                        new MediaRecorderEvent(NativeMediaRecorder.this,
                                MediaRecorderEvent.MEDIA_RECORDER_RESUME));
            }
        }
    }

    @Override
    public final void pause() {
        // Disable recording
        recording = false;

        // Set status to paused
        setStatus(Status.PAUSED);

        // Fire start event
        Event.fireEvent(NativeMediaRecorder.this,
                new MediaRecorderEvent(NativeMediaRecorder.this,
                        MediaRecorderEvent.MEDIA_RECORDER_PAUSE));
    }

    @Override
    public final void stop() {
        final Runnable startRecordingRunnable = () -> {
            stopRecording();

            Platform.runLater(() -> {
                // Set the media source
                setMediaSource(new MediaSource(tempVideoFile.toUri().toString()));

                // Set status to inactive
                setStatus(Status.INACTIVE);

                // Fire start event
                Event.fireEvent(NativeMediaRecorder.this,
                        new MediaRecorderEvent(NativeMediaRecorder.this,
                                MediaRecorderEvent.MEDIA_RECORDER_STOP));
            });
        };
        startStopRecordingExecutorService.execute(startRecordingRunnable);
    }

    /**
     * Stop the recording and closes the file.
     */
    private void stopRecording() {
        // Stop recording
        recording = false;

        // Release video recorder resources
        if (recorder != null) {
            try {
                recorder.close(); // This call stops the recorder and releases all resources used by it.
            } catch (FrameRecorder.Exception ex) {
                setError("Exception on stopping the audio/video recorder", ex);
            }
        }
    }

    /**
     * Stop the acquisition from the camera and microphone and release all the resources.
     */
    public final void release() {
        recorderReady = false;
        recording = false;
        releaseVideoResources();
        releaseAudioResources();
    }

    /**
     * Releases the resources associated with the video capture.
     */
    private void releaseVideoResources() {
        if (videoExecutorService != null && !videoExecutorService.isShutdown()) {
            try {
                // release video grabber
                if (cameraGrabber != null) {
                    cameraGrabber.close();
                    cameraGrabber = null;
                }

                // stop the video recoding service
                videoExecutorService.shutdown();
                //noinspection ResultOfMethodCallIgnored
                videoExecutorService.awaitTermination(100, TimeUnit.MILLISECONDS);
            } catch (FrameGrabber.Exception | InterruptedException ex) {
                setError("Exception in stopping the video frame capture service.", ex);
            }
        }
    }

    /**
     * Releases the resources associated with the audio capture.
     */
    private void releaseAudioResources() {
        if (audioExecutorService != null && !audioExecutorService.isShutdown()) {
            try {
                // release audio grabber
                if (micGrabber != null) {
                    micGrabber.close();
                    micGrabber = null;
                }

                // stop the audio recording service
                audioExecutorService.shutdown();
                //noinspection ResultOfMethodCallIgnored
                audioExecutorService.awaitTermination(100, TimeUnit.MILLISECONDS);
            } catch (FrameGrabber.Exception | InterruptedException ex) {
                setError("Exception in stopping the audio frame capture service.", ex);
            }
        }
    }

    /**
     * Update the {@link ImageView} in the JavaFX main thread
     *
     * @param view  the {@link ImageView} to update
     * @param image the {@link Image} to show
     */
    private void updateCameraView(ImageView view, Image image) {
        Platform.runLater(() -> view.setImage(image));
    }

    /**
     * Create a random filename with the given prefix and postfix.
     *
     * @param prefix  the filename prefix
     * @param postfix the filename postfix
     * @return a string containing the entire path with the created filename
     */
    private Path createTempFilename(final String prefix, final String postfix) {
        // Generate a random filename with the given prefix and postfix
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        String filename = "vid_" + random.nextInt(0, Integer.MAX_VALUE);
        if (prefix != null) {
            filename = prefix + random.nextInt(0, Integer.MAX_VALUE);
        }
        if (postfix != null) {
            filename += postfix;
        }

        final Path tempFile = RECORDING_PATH.resolve(filename);
        final Path parentDir = tempFile.getParent();
        if (Files.notExists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }
        return tempFile;
    }

    /**
     * Checks if the operating system is Windows.
     *
     * @return true if the OS is Windows, false otherwise
     */
    private boolean isOsWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Set the error message and exception.
     * This method makes sure that {@link #errorProperty()} is updated in the JavaFX Application Thread.
     *
     * @param message the error message
     * @param ex      the exception
     */
    private void setError(String message, Exception ex) {
        if (Platform.isFxApplicationThread()) {
            setError(new MediaRecorderException(message, ex));
        } else {
            Platform.runLater(() -> setError(new MediaRecorderException(message, ex)));
        }
        logger.error(message, ex);
    }

    /**
     * Prints to terminal the capture device description. This includes details about the camera and
     * microphone being used for recording.
     */
    private void printCaptureDeviceDescription() {
        logger.debug(DIVIDER_LINE);

        // Print camera device description
        if (cameraGrabber != null) {
            try {
                if (isOsWindows()) {
                    logger.debug("Camera Device Description: " +
                            Arrays.toString(VideoInputFrameGrabber.getDeviceDescriptions()));
                }

                logger.debug("Image Width: " + cameraGrabber.getImageWidth());
                logger.debug("Image Height: " + cameraGrabber.getImageHeight());
                logger.debug("Frame Rate: " + frameRate);
            } catch (FrameGrabber.Exception ex) {
                logger.error("Error getting camera device description: " + ex.getMessage(), ex);
            }
        }

        // Print microphone device description
        if (micGrabber != null) {
            String microphoneDescription = "format: " + micGrabber.getFormat()
                    + " - device: " + getDefaultAudioInputDevice();
            logger.debug("Microphone Device Description: " + microphoneDescription);
            logger.debug("Audio Channels: " + micGrabber.getAudioChannels());
            logger.debug("Sample Rate: " + micGrabber.getSampleRate());
        }
    }

    /**
     * Adjusts the timestamp of the recording to match the system time. Ensures synchronization
     * between video and audio streams.
     */
    private void adjustTimestamp() {
        long t = 1000 * (System.currentTimeMillis() - startTime);
        if (t > recorder.getTimestamp()) {
            logger.debug("Correct recorder timestamp: " + t);
            recorder.setTimestamp(t);
        }
    }

    /**
     * Gets the default audio input device name based on the operating system.
     * <p>
     * This method determines the appropriate audio input device for use with {@link FFmpegFrameGrabber},
     * based on the operating system where the application is running:
     * </p>
     * <ul>
     * <li>Windows: Returns "null", which typically lets the system select the default device.
     *     Note: This might need to be adjusted based on the system configuration.</li>
     * <li>macOS: Returns ":0" to refer to the default audio input device as per AVFoundation's standard.</li>
     * <li>Linux: Returns "default", which typically refers to the default ALSA audio input device.</li>
     * <li>Other: Returns "default" as a generic fallback. This should be validated or adjusted
     *     based on the specific requirements or environment.</li>
     * </ul>
     *
     * @return the name of the default audio input device
     */
    public static String getDefaultAudioInputDevice() {
        String OS = System.getProperty("os.name").toLowerCase();

        String audioDevice;
        if (OS.contains("win")) {
            // Windows - use device name
            audioDevice = "audio=" + getAudioInputDevices().get(1).getName();
        } else if (OS.contains("mac")) {
            // macOS - default device
            audioDevice = ":0";
        } else if (OS.contains("nix") || OS.contains("nux") || OS.contains("aix")) {
            // Linux - default device
            audioDevice = "default";
        } else {
            // Unknown OS - fallback
            audioDevice = "default";
        }

        return audioDevice;
    }

    /**
     * Retries the list of available audio input devices used as microphones.
     *
     * @return the list of available audio input devices
     */
    private static List<Mixer.Info> getAudioInputDevices() {
        final Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        List<Mixer.Info> result = new ArrayList<>();
        for (Mixer.Info info : mixerInfos) {
            final Mixer mixer = AudioSystem.getMixer(info);
            final Line.Info[] lineInfos = mixer.getTargetLineInfo();
            // Only prints out info is it is a Microphone
            if (lineInfos.length >= 1 && lineInfos[0].getLineClass().equals(TargetDataLine.class)) {
                logger.debug(DIVIDER_LINE);
                for (Line.Info lineInfo : lineInfos) {
                    logger.debug("Mic Line Name: " + info.getName()); // The audio device name
                    logger.debug("Mic Line Description: " + info.getDescription()); // The type of audio device
                    logger.debug("Supported Audio Formats:");
                    if (lineInfo instanceof final DataLine.Info dataLineInfo) {
                        Arrays.stream(dataLineInfo.getFormats()).forEach(format -> logger.debug("{}", format));
                    }
                }
                logger.debug(DIVIDER_LINE);
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Gets the default audio input format based on the operating system.
     * <p>
     * This method determines the appropriate audio input format for use with {@link FFmpegFrameGrabber},
     * based on the operating system where the application is running:
     * </p>
     * <ul>
     * <li>Windows: Returns "dshow" for DirectShow.</li>
     * <li>macOS: Returns "avfoundation" for AVFoundation.</li>
     * <li>Linux: Returns "alsa" for ALSA.</li>
     * <li>Other: Returns "default" as a fallback,
     * but this should be adjusted based on specific requirements or environment.</li>
     * </ul>
     *
     * @return the format string for audio input format
     */
    public static String getDefaultAudioInputFormat() {
        String OS = System.getProperty("os.name").toLowerCase();
        String format;

        if (OS.contains("win")) {
            // Windows - DirectShow
            format = "dshow";
        } else if (OS.contains("mac")) {
            // macOS - AVFoundation
            format = "avfoundation";
        } else if (OS.contains("nix") || OS.contains("nux") || OS.contains("aix")) {
            // Linux - ALSA
            format = "alsa";
        } else {
            // Unknown OS - fallback
            format = "default";
        }

        return format;
    }

}

# JPro Media
JPro Media is a Java Library for playing and recording audio and video files in JavaFX applications that run
on both desktop/mobile devices and in the browser via [JPro](https://www.jpro.one) with exactly the same code. 
Building cross-platform applications with JPro Media is as easy as using the 
[JavaFX Media](https://openjfx.io/javadoc/17/javafx.media/module-summary.html) API.

## Features
- Write Once Run Anywhere (the same code runs on desktop/mobile devices and in the web browser)
- Playback of audio and video files or streams
- Recording the audio and video stream from a camera device
- Support for all common audio and video formats
- Unified media source API for local and remote media (inside the client's browser)

## Supported Platforms
- Web (via [JPro](https://www.jpro.one))
- Desktop 
  - Windows
  - Linux
  - macOS (version 11.7 and later)
- Android (The `MediaRecorder` still is under final testing)
- iOS (The `MediaRecorder` still is under final testing)

## Getting Started
To get started with JPro Media, we need to add the following configuration to your project.
### Gradle
1. In `plugin` section, we need to add the `org.bytedeco.gradle-javacpp-platform` plugin in order to select from 
existing artifacts the ones corresponding to the current platform or to the specified platforms. For more
information go to the following [link](https://github.com/bytedeco/gradle-javacpp#the-platform-plugin).
2. Add `jpro-media` and `javacv-platform` dependencies. 

*NOTE: When deploying with JPro server, `compileOnly` configuration 
must be used instead of `implementation` in order to prevent the inclusion of platform specific artifacts inside 
the `release` file. Even the `jproRun` task is faster since these files are not needed at `runtime`.*
```groovy
plugins {
    id 'org.bytedeco.gradle-javacpp-platform' version "1.5.9"
}

dependencies {
    implementation 'one.jpro.platform:jpro-media:0.2.11-SNAPSHOT'

    // use compileOnly configuration when running/deploying with JPro, 
    // since the platform specific libraries are no more needed
    // compileOnly "org.bytedeco:javacv-platform:1.5.9"
    implementation "org.bytedeco:javacv-platform:1.5.9"
}
```
3. Provide the following `jvm argument` inside the `run` task:
```groovy
run {
    doFirst {
        jvmArgs = [
                '--add-exports', 'javafx.base/com.sun.javafx.event=one.jpro.platform.media'
        ]
    }
}
```
or as an application default `jvm argument`:
```groovy
applicationDefaultJvmArgs = [
        "--add-exports", "javafx.base/com.sun.javafx.event=one.jpro.platform.media"
]
```
### Maven
1. Add `jpro-media` and `javacv-platform` dependencies to the `pom.xml` file. 

*NOTE: When deploying with JPro server, 
`compile` scope must be used in order to prevent the inclusion of platform specific artifacts inside
the `release` zipped file. Even the `jpro:run` task is faster since these files are not needed at `runtime`.*
```xml
<dependencies>
    <dependency>
        <groupId>one.jpro.platform</groupId>
        <artifactId>jpro-media</artifactId>
        <version>0.2.11-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>org.bytedeco</groupId>
        <artifactId>javacv-platform</artifactId>
        <version>1.5.9</version>
<!--        use compile scope when running/deploying with JPro,-->
<!--        since the platform related libraries are no more needed-->
<!--        <scope>compile</scope>-->
    </dependency>
</dependencies>
```
2. Provide the following configuration `option` inside the `javafx-maven-plugin`:
```xml
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.8</version>
    <configuration>
        <mainClass>one.jpro.example.App</mainClass>
        <options>
            <option>--add-exports</option>
            <option>javafx.base/com.sun.javafx.event=one.jpro.platform.media</option>
        </options>
    </configuration>
</plugin>
```

### Platform specific modules configuration
When we run the application on the desktop/device, the following modules are required and must be added the module 
descriptor (module-info.java) depending on the operating system and architecture currently in use.

***It is important to note here that when we run/deploy the application as a web application via the JPro server,
these modules are not required and no further configuration is needed.***
* Apple Silicon Macs
```java
module com.example.app {
    
    requires org.bytedeco.opencv.macosx.arm64;
    requires org.bytedeco.ffmpeg.macosx.arm64;
    requires org.bytedeco.openblas.macosx.arm64;
    
}
```
* Intel-based Macs
```java
module com.example.app {
    
    requires org.bytedeco.opencv.macosx.x86_64;
    requires org.bytedeco.ffmpeg.macosx.x86_64;
    requires org.bytedeco.openblas.macosx.x86_64;
    
}
```
* Windows 64-bit
```java
module com.example.app {

    requires org.bytedeco.ffmpeg.windows.x86_64;
    requires org.bytedeco.opencv.windows.x86_64;
    requires org.bytedeco.openblas.windows.x86_64;
    requires org.bytedeco.videoinput.windows.x86_64;
    
}
```
* Windows 32-bit
```java
module com.example.app {

    requires org.bytedeco.ffmpeg.windows.x86;
    requires org.bytedeco.opencv.windows.x86;
    requires org.bytedeco.openblas.windows.x86;
    requires org.bytedeco.videoinput.windows.x86;
    
}
```
* Linux 64-bit
```java
module com.example.app {

    requires org.bytedeco.opencv.linux.x86_64;
    requires org.bytedeco.ffmpeg.linux.x86_64;
    requires org.bytedeco.openblas.linux.x86_64;
    
}
```
* Linux 32-bit
```java
module com.example.app {

    requires org.bytedeco.opencv.linux.x86;
    requires org.bytedeco.ffmpeg.linux.x86;
    requires org.bytedeco.openblas.linux.x86;
    
}
```
*Please note that other operating systems and architectures are also supported like `linux.arm64`, `linux.armhf`, `android.arm`, 
`android.arm64`, `android.x86`, `android.x86_64`, `ios.arm64` and `ios.x86_64`, but are still under final testing.*

### Similarities and differences to JavaFX Media
- The `MediaSource` class is very similar to the `Media` class from the JavaFX Media API.
- A `MediaPlayer` instance can be created by calling `MediaPlayer#create(Stage, MediaSource)`,
   while in JavaFX Media we would use `new MediaPlayer(Media)`.
- The `MediaView` class is very similar to the `MediaView` class from the JavaFX Media API.
  It can be created by calling `MediaView#create(Stage, MediaPlayer)`, while in JavaFX Media we 
  would use `new MediaView(MediaPlayer)`.
- The `MediaRecorder` can be created by calling `MediaRecorder#create(Stage)`,
   while in JavaFX Media doesn't provide a recorder.

## Usage
### Media Player API
For the playback functionality, the JPro Media API is very similar to the JavaFX Media API.
The main difference is that the `Media` class is replaced by the `MediaSource` class.
The following example shows how to play a video file:
```java
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import one.jpro.platform.media.MediaSource;
import one.jpro.platform.media.MediaView;
import one.jpro.platform.media.player.MediaPlayer;

public class MediaPlayerApplication extends Application {
    
    public void start(Stage stage) {
        stage.setTitle("JPro Media Player");

        // Provide a media source
        String source = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        MediaSource mediaSource = new MediaSource(source);

        // Create the media player and the media view.
        MediaPlayer mediaPlayer = MediaPlayer.create(stage, mediaSource);
        MediaView mediaView = MediaView.create(mediaPlayer);

        // Media controls
        Button playPauseButton = new Button("Play");
        playPauseButton.setDisable(true);
        Button stopButton = new Button("Stop");
        stopButton.setDisable(true);

        // Control events
        stopButton.setOnAction(event -> mediaPlayer.stop());

        // Event handlers
        mediaPlayer.setOnReady(event -> {
            playPauseButton.setOnAction(event2 -> mediaPlayer.play());
            playPauseButton.setDisable(false);
            stopButton.setDisable(false);
        });
        mediaPlayer.setOnPlaying(event -> {
            playPauseButton.setText("Pause");
            playPauseButton.setOnAction(event2 -> mediaPlayer.pause());
        });
        mediaPlayer.setOnPaused(event -> {
            playPauseButton.setText("Play");
            playPauseButton.setOnAction(event2 -> mediaPlayer.play());
        });
        mediaPlayer.setOnStopped(event -> {
            playPauseButton.setText("Play");
            playPauseButton.setOnAction(event2 -> mediaPlayer.play());
        });
        mediaPlayer.setOnError(event -> System.out.println("Error: " + mediaPlayer.getError()));

        // User interface
        FlowPane controlsPane = new FlowPane(playPauseButton, stopButton);
        controlsPane.setAlignment(Pos.CENTER);
        controlsPane.setHgap(8);
        controlsPane.setVgap(8);
        controlsPane.setPadding(new Insets(8));
        VBox rootPane = new VBox(mediaView, controlsPane);
        VBox.setVgrow(mediaView, Priority.ALWAYS);

        Scene scene = new Scene(rootPane, 1140, 640);
        stage.setScene(scene);
        stage.show();
    }
}
```

### Media Recorder API
The JPro Media also provides a MediaRecorder API for recording the audio and video stream from a camera device.
The following example shows how to record from the default camera device:

```java
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import one.jpro.platform.media.MediaView;
import one.jpro.platform.media.recorder.MediaRecorder;
import one.jpro.platform.media.util.MediaUtil;

import java.io.IOException;

public class MediaRecorderApplication extends Application {

    public void start(Stage stage) {
        stage.setTitle("JPro Camera Recorder");

        // Create the media recorder and the media view.
        MediaRecorder mediaRecorder = MediaRecorder.create(stage);
        MediaView cameraView = MediaView.create(mediaRecorder);

        // Controls
        Button enableCamButton = new Button("Enable Cam");
        Button startButton = new Button("Start");
        startButton.setDisable(true);
        Button stopButton = new Button("Stop");
        stopButton.setDisable(true);
        Button saveButton = new Button("Save As...");
        saveButton.setDisable(true);

        StackPane previewPane = new StackPane(enableCamButton);

        FlowPane controlsPane = new FlowPane(startButton, stopButton, saveButton);
        controlsPane.setAlignment(Pos.CENTER);
        controlsPane.setHgap(8);
        controlsPane.setVgap(8);
        controlsPane.setPadding(new Insets(8));

        // Control events
        enableCamButton.setOnAction(event -> mediaRecorder.enable());
        startButton.setOnAction(event -> mediaRecorder.start());
        stopButton.setOnAction(event -> mediaRecorder.stop());
        saveButton.setOnAction(event -> {
            try {
                MediaUtil.retrieve(stage, mediaRecorder.getMediaSource());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        // Event handlers
        mediaRecorder.setOnReady(event -> {
            startButton.setDisable(false);
            previewPane.getChildren().setAll(cameraView);
        });
        mediaRecorder.setOnStart(event -> {
            startButton.setDisable(true);
            stopButton.setDisable(false);
            saveButton.setDisable(true);
        });
        mediaRecorder.setOnStopped(event -> {
            startButton.setDisable(false);
            stopButton.setDisable(true);
            saveButton.setDisable(false);
        });
        mediaRecorder.setOnError(event -> System.out.println(mediaRecorder.getError().toString()));

        VBox rootPane = new VBox(previewPane, controlsPane);
        VBox.setVgrow(previewPane, Priority.ALWAYS);
        cameraView.fitWidthProperty().bind(rootPane.widthProperty());
        cameraView.fitHeightProperty().bind(rootPane.heightProperty()
                .subtract(controlsPane.heightProperty()));

        Scene scene = new Scene(rootPane, 760, 540);
        stage.setScene(scene);
        stage.show();
    }
}
```
### More examples
For more examples, please take a look at the [JPro Media Examples](https://github.com/JPro-one/jpro-platform/tree/main/jpro-media/example/src/main/java/one/jpro/platform/media/example).
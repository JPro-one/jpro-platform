package one.jpro.platform.imagemanager.source;

import one.jpro.platform.imagemanager.Utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ImageSourceResource implements ImageSource {

    private final String resourcePath;

    public ImageSourceResource(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public BufferedImage loadImage() {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            return ImageIO.read(is);
        } catch (Exception e) {
            throw new RuntimeException("Error loading resource: " + resourcePath, e);
        }
    }

    @Override
    public long identityHashValue() {
        try {
            URL resourceUrl = getClass().getResource(resourcePath);
            if (resourceUrl == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            URLConnection conn = resourceUrl.openConnection();
            long lastModified = conn.getLastModified();
            if (lastModified == 0) { // fallback to binary data hash
                byte[] resourceData = Files.readAllBytes(Paths.get(resourceUrl.toURI()));
                return Utils.computeHashValue(resourceData);
            }
            return lastModified;
        } catch (Exception e) {
            throw new RuntimeException("Error obtaining modification date for resource: " + resourcePath, e);
        }
    }

    @Override
    public String toJson() {
        // Escaping might be necessary depending on the structure of resourcePath.
        return "{\"type\":\"ImageSourceResource\",\"resourcePath\":\"" + Utils.escapeJson(resourcePath) + "\"}";
    }

    @Override
    public String fileName() {
        return resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
    }
}
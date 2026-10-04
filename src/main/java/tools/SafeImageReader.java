package tools;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Dimension-aware decoder that limits decompression-bomb memory use. */
public final class SafeImageReader {
    private SafeImageReader() { }

    public static BufferedImage read(File file, long maxPixels) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            return read(input, maxPixels);
        }
    }

    public static BufferedImage read(byte[] data, long maxPixels) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            return read(input, maxPixels);
        }
    }

    private static BufferedImage read(ImageInputStream input, long maxPixels) throws IOException {
        if (input == null) return null;
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) return null;
        ImageReader reader = readers.next();
        try {
            reader.setInput(input, true, true);
            // Header-only inspection: the pixel buffer is allocated only after this check.
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
                throw new IOException("Image dimensions exceed the configured limit");
            }
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    /**
     * Output format for re-encoded uploads: PNG keeps transparency for PNG/GIF sources,
     * everything else becomes JPEG. Only formats ImageIO can always write are returned.
     */
    public static String outputFormat(String requestedExtension) {
        if (requestedExtension == null) return "jpg";
        String ext = requestedExtension.toLowerCase(java.util.Locale.ROOT);
        return ("png".equals(ext) || "gif".equals(ext)) ? "png" : "jpg";
    }

    /** JPEG cannot store alpha; ImageIO silently writes nothing for ARGB images. */
    public static BufferedImage forFormat(BufferedImage image, String format) {
        if (!"jpg".equals(format) || !image.getColorModel().hasAlpha()) return image;
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /** Scales proportionally so the width is at most {@code maxWidth}, keeping the format's colour model. */
    public static BufferedImage scaleToWidth(BufferedImage src, int maxWidth, String format) {
        if (src.getWidth() <= maxWidth) return forFormat(src, format);
        double ratio = (double) maxWidth / src.getWidth();
        int newHeight = Math.max(1, (int) Math.round(src.getHeight() * ratio));
        int type = "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage scaled = new BufferedImage(maxWidth, newHeight, type);
        Graphics2D g = scaled.createGraphics();
        if (type == BufferedImage.TYPE_INT_RGB) {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, maxWidth, newHeight);
        }
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, maxWidth, newHeight, null);
        g.dispose();
        return scaled;
    }

    /** Writes the image and fails loudly if ImageIO has no writer for it. */
    public static void write(BufferedImage image, String format, java.nio.file.Path path) throws IOException {
        if (!ImageIO.write(forFormat(image, format), format, path.toFile())) {
            throw new IOException("No image writer for format " + format);
        }
        try {
            java.nio.file.Files.setPosixFilePermissions(path,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException ignored) {
            // Windows — permissions must be set at the folder level externally
        }
    }
}

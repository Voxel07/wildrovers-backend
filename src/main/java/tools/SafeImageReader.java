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

    /** Upright image: the EXIF orientation of phone photos is applied, because re-encoding drops EXIF. */
    public static BufferedImage readOriented(File file, long maxPixels) throws IOException {
        return readOriented(file, maxPixels, Integer.MAX_VALUE, null);
    }

    /** Like {@link #readOriented(File, long)}, scaled down first so that no side exceeds {@code maxEdge}. */
    public static BufferedImage readOriented(File file, long maxPixels, int maxEdge, String format) throws IOException {
        BufferedImage image = read(file, maxPixels);
        if (image == null) return null;
        byte[] header;
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
            header = in.readNBytes(EXIF_SCAN_BYTES);
        }
        return applyOrientation(limitLongEdge(image, maxEdge, format), exifOrientation(header));
    }

    public static BufferedImage readOriented(byte[] data, long maxPixels, int maxEdge, String format) throws IOException {
        BufferedImage image = read(data, maxPixels);
        return image == null ? null : applyOrientation(limitLongEdge(image, maxEdge, format), exifOrientation(data));
    }

    /** EXIF lives in an APP1 segment of at most 64 KiB right after the start of the JPEG. */
    private static final int EXIF_SCAN_BYTES = 256 * 1024;

    /** EXIF orientation (1–8) of a JPEG, 1 when absent or not a JPEG. */
    static int exifOrientation(byte[] d) {
        if (d == null || d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8) return 1;
        int pos = 2;
        while (pos + 4 <= d.length) {
            if ((d[pos] & 0xFF) != 0xFF) return 1;
            int marker = d[pos + 1] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) return 1; // end of image / start of scan data
            int length = ((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF);
            int end = pos + 2 + length;
            if (length < 2 || end > d.length) return 1;
            if (marker == 0xE1 && length >= 16 && d[pos + 4] == 'E' && d[pos + 5] == 'x' && d[pos + 6] == 'i'
                    && d[pos + 7] == 'f' && d[pos + 8] == 0 && d[pos + 9] == 0) {
                return tiffOrientation(d, pos + 10, end);
            }
            pos = end;
        }
        return 1;
    }

    private static int tiffOrientation(byte[] d, int start, int end) {
        boolean little;
        if (d[start] == 'I' && d[start + 1] == 'I') little = true;
        else if (d[start] == 'M' && d[start + 1] == 'M') little = false;
        else return 1;
        long ifdOffset = u32(d, start + 4, little);
        if (ifdOffset < 8 || start + ifdOffset + 2 > end) return 1;
        int ifd = start + (int) ifdOffset;
        int entries = u16(d, ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) return 1;
            if (u16(d, entry, little) == 0x0112) {
                int value = u16(d, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] d, int p, boolean little) {
        return little ? (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8) : ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
    }

    private static long u32(byte[] d, int p, boolean little) {
        return little
                ? (d[p] & 0xFFL) | ((d[p + 1] & 0xFFL) << 8) | ((d[p + 2] & 0xFFL) << 16) | ((d[p + 3] & 0xFFL) << 24)
                : ((d[p] & 0xFFL) << 24) | ((d[p + 1] & 0xFFL) << 16) | ((d[p + 2] & 0xFFL) << 8) | (d[p + 3] & 0xFFL);
    }

    /** Rotates/mirrors the pixels so that an image with this EXIF orientation is displayed upright. */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage dst = new BufferedImage(swap ? h : w, swap ? w : h, type);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> { dx = w - 1 - x; dy = y; }          // mirrored
                    case 3 -> { dx = w - 1 - x; dy = h - 1 - y; }  // rotated 180°
                    case 4 -> { dx = x; dy = h - 1 - y; }          // mirrored vertically
                    case 5 -> { dx = y; dy = x; }                  // transposed
                    case 6 -> { dx = h - 1 - y; dy = x; }          // needs 90° clockwise
                    case 7 -> { dx = h - 1 - y; dy = w - 1 - x; }  // transversed
                    default -> { dx = y; dy = w - 1 - x; }         // 8: needs 90° counter-clockwise
                }
                dst.setRGB(dx, dy, row[x]);
            }
        }
        return dst;
    }

    /** Scales proportionally so that neither side exceeds {@code maxEdge}. */
    public static BufferedImage limitLongEdge(BufferedImage src, int maxEdge, String format) {
        if (src.getWidth() >= src.getHeight()) return scaleToWidth(src, maxEdge, format);
        if (src.getHeight() <= maxEdge) return forFormat(src, format);
        int width = Math.max(1, (int) Math.round(src.getWidth() * ((double) maxEdge / src.getHeight())));
        return scaleToWidth(src, width, format);
    }

    /** Quality of stored JPEGs (ImageIO's default is 0.75). */
    private static final float JPEG_QUALITY = 0.85f;

    /** Writes the image and fails loudly if ImageIO has no writer for it. */
    public static void write(BufferedImage image, String format, java.nio.file.Path path) throws IOException {
        BufferedImage output = forFormat(image, format);
        if ("jpg".equals(format)) {
            writeJpeg(output, path);
        } else if (!ImageIO.write(output, format, path.toFile())) {
            throw new IOException("No image writer for format " + format);
        }
        try {
            java.nio.file.Files.setPosixFilePermissions(path,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException ignored) {
            // Windows — permissions must be set at the folder level externally
        }
    }

    private static void writeJpeg(BufferedImage image, java.nio.file.Path path) throws IOException {
        Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IOException("No image writer for format jpg");
        javax.imageio.ImageWriter writer = writers.next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        // A stream over a truncating OutputStream: FileImageOutputStream would keep the tail of a larger old file.
        try (java.io.OutputStream file = java.nio.file.Files.newOutputStream(path);
             javax.imageio.stream.ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(out);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}

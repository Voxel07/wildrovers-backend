package tools;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeImageReaderTest {

    private static final int RED = 0xFFFF0000;

    /** 3×2 image, red top-left pixel, the rest white. */
    private static BufferedImage sample() {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) image.setRGB(x, y, 0xFFFFFFFF);
        image.setRGB(0, 0, RED);
        return image;
    }

    /** JPEG with an APP1/Exif segment (big-endian TIFF) that carries only the orientation tag. */
    private static byte[] jpegWithOrientation(BufferedImage image, int orientation) throws Exception {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", plain);
        byte[] jpeg = plain.toByteArray();
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8,           // header, IFD at offset 8
                0, 1,                                  // one entry
                0x01, 0x12, 0, 3, 0, 0, 0, 1,          // tag 0x0112 (orientation), SHORT, count 1
                0, (byte) orientation, 0, 0,           // value
                0, 0, 0, 0 };                          // no next IFD
        int length = 2 + 6 + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(new byte[] { (byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length });
        out.write(new byte[] { 'E', 'x', 'i', 'f', 0, 0 });
        out.write(tiff);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    @Test
    void readsOrientationFromExif() throws Exception {
        assertEquals(6, SafeImageReader.exifOrientation(jpegWithOrientation(sample(), 6)));
        assertEquals(1, SafeImageReader.exifOrientation(new byte[] { 1, 2, 3, 4 }));
    }

    @Test
    void rotatesPhoneImagesUpright() {
        BufferedImage rotated = SafeImageReader.applyOrientation(sample(), 6);
        assertEquals(2, rotated.getWidth());
        assertEquals(3, rotated.getHeight());
        // 90° clockwise: the top-left pixel ends up top-right.
        assertEquals(RED, rotated.getRGB(1, 0));

        BufferedImage ccw = SafeImageReader.applyOrientation(sample(), 8);
        assertEquals(RED, ccw.getRGB(0, 2));
        BufferedImage flipped = SafeImageReader.applyOrientation(sample(), 3);
        assertEquals(RED, flipped.getRGB(2, 1));
    }

    @Test
    void readOrientedAppliesExifAndLimitsTheLongEdge() throws Exception {
        BufferedImage big = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage image = SafeImageReader.readOriented(jpegWithOrientation(big, 6), 1_000_000L, 200, "jpg");
        assertEquals(50, image.getWidth());
        assertEquals(200, image.getHeight());
    }

    @Test
    void writesJpegOverALargerOldFile() throws Exception {
        Path file = Files.createTempFile("safe-image", ".jpg");
        try {
            Files.write(file, new byte[2_000_000]);
            SafeImageReader.write(sample(), "jpg", file);
            assertTrue(Files.size(file) < 10_000, "old bytes must not survive");
            assertEquals(3, ImageIO.read(file.toFile()).getWidth());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}

package com.cinescout.photos;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ImageProcessorTest {

    private static BufferedImage read(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void readsWhereThePhotoWasTakenTurnsItUprightAndKeepsNoMetadata() throws IOException {
        // A landscape picture the camera held sideways (orientation 6: turn it 90 degrees clockwise to view).
        byte[] upload = TestPhotos.withExif(TestPhotos.jpeg(400, 200), 6, 51.5072, -0.1276);

        ImageProcessor.Processed processed = ImageProcessor.process(upload);

        assertThat(processed.gps()).isNotNull();
        assertThat(processed.gps().latitude()).isCloseTo(51.5072, within(0.0001));
        assertThat(processed.gps().longitude()).isCloseTo(-0.1276, within(0.0001));
        assertThat(processed.contentType()).isEqualTo("image/jpeg");
        assertThat(processed.width()).isEqualTo(200);
        assertThat(processed.height()).isEqualTo(400);
        BufferedImage full = read(processed.full());
        assertThat(full.getWidth()).isEqualTo(200);
        // Turned clockwise: the red left half is now the top.
        assertThat(new java.awt.Color(full.getRGB(100, 20)).getRed()).isGreaterThan(200);
        assertThat(new java.awt.Color(full.getRGB(100, 380)).getBlue()).isGreaterThan(200);
        for (byte[] kept : new byte[][] {processed.full(), processed.thumb()}) {
            String raw = new String(kept, StandardCharsets.ISO_8859_1);
            assertThat(raw).doesNotContain("Exif", "SECRET-OWNER");
        }
        assertThat(ImageProcessor.process(TestPhotos.jpeg(40, 30)).gps()).isNull();
    }

    @Test
    void shrinksLargePicturesAndMakesASmallThumbnail() throws IOException {
        ImageProcessor.Processed processed = ImageProcessor.process(TestPhotos.jpeg(6000, 3000));

        assertThat(Math.max(processed.width(), processed.height())).isLessThanOrEqualTo(ImageProcessor.MAX_SIDE);
        BufferedImage thumb = read(processed.thumb());
        assertThat(Math.max(thumb.getWidth(), thumb.getHeight())).isEqualTo(ImageProcessor.THUMB_SIDE);
    }

    @Test
    void keepsATransparentPngAsPngAndRefusesWhatIsNotAPhoto() {
        assertThat(ImageProcessor.process(TestPhotos.png(50, 40)).contentType()).isEqualTo("image/png");
        assertThatThrownBy(() -> ImageProcessor.process("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UnreadableImageException.class).hasMessageContaining("JPEG, PNG or HEIC");
        byte[] broken = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3};
        assertThatThrownBy(() -> ImageProcessor.process(broken)).isInstanceOf(UnreadableImageException.class);
    }
}

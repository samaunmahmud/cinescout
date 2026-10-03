package com.cinescout.photos;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Photos for tests: a JPEG with an EXIF block that says where it was taken, how it should be turned, and who owns
 * the camera, the way a phone writes one.
 */
public final class TestPhotos {

    private TestPhotos() {
    }

    /** A {@code width}x{@code height} JPEG: red on its left half, blue on its right. */
    public static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return write(image, "jpeg");
    }

    public static byte[] png(int width, int height) {
        return write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    /** The JPEG with an EXIF block: orientation, an artist's name, and the GPS position given (north and east positive). */
    public static byte[] withExif(byte[] jpeg, int orientation, double latitude, double longitude) {
        byte[] tiff = tiff(orientation, latitude, longitude);
        byte[] exifHeader = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        int length = 2 + exifHeader.length + tiff.length;
        ByteBuffer app1 = ByteBuffer.allocate(2 + length).order(ByteOrder.BIG_ENDIAN);
        app1.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(exifHeader).put(tiff);
        ByteBuffer out = ByteBuffer.allocate(jpeg.length + app1.capacity());
        out.put(jpeg, 0, 2).put(app1.array()).put(jpeg, 2, jpeg.length - 2);
        return out.array();
    }

    /** Little-endian TIFF: IFD0 (Orientation, Artist, GPS pointer), then the GPS IFD and its values. */
    private static byte[] tiff(int orientation, double latitude, double longitude) {
        ByteBuffer b = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        byte[] artist = "SECRET-OWNER\0".getBytes(StandardCharsets.US_ASCII);
        int ifd0 = 8;
        int ifd0Size = 2 + 3 * 12 + 4;
        int artistAt = ifd0 + ifd0Size;
        int gpsIfd = artistAt + artist.length + (artist.length % 2);
        int gpsIfdSize = 2 + 4 * 12 + 4;
        int latAt = gpsIfd + gpsIfdSize;
        int lngAt = latAt + 24;
        // IFD0
        b.position(ifd0);
        b.putShort((short) 3);
        entry(b, 0x0112, 3, 1, orientation);          // Orientation, SHORT
        entry(b, 0x013B, 2, artist.length, artistAt); // Artist, ASCII
        entry(b, 0x8825, 4, 1, gpsIfd);               // GPSInfo pointer, LONG
        b.putInt(0);
        b.position(artistAt);
        b.put(artist);
        // GPS IFD
        b.position(gpsIfd);
        b.putShort((short) 4);
        entry(b, 0x0001, 2, 2, (latitude >= 0 ? 'N' : 'S'));  // GPSLatitudeRef, ASCII "N\0" inline
        entry(b, 0x0002, 5, 3, latAt);                         // GPSLatitude, 3 RATIONAL
        entry(b, 0x0003, 2, 2, (longitude >= 0 ? 'E' : 'W'));  // GPSLongitudeRef
        entry(b, 0x0004, 5, 3, lngAt);                         // GPSLongitude
        b.putInt(0);
        b.position(latAt);
        rationalDegrees(b, Math.abs(latitude));
        rationalDegrees(b, Math.abs(longitude));
        byte[] out = new byte[b.position()];
        System.arraycopy(b.array(), 0, out, 0, out.length);
        return out;
    }

    private static void entry(ByteBuffer b, int tag, int type, int count, int value) {
        b.putShort((short) tag).putShort((short) type).putInt(count);
        if (type == 3) {
            b.putShort((short) value).putShort((short) 0);
        } else {
            b.putInt(value);
        }
    }

    /** Degrees, minutes and seconds (to a thousandth), as three rationals. */
    private static void rationalDegrees(ByteBuffer b, double value) {
        int degrees = (int) value;
        double minutesAll = (value - degrees) * 60;
        int minutes = (int) minutesAll;
        long seconds = Math.round((minutesAll - minutes) * 60 * 1000);
        b.putInt(degrees).putInt(1).putInt(minutes).putInt(1).putInt((int) seconds).putInt(1000);
    }

    private static byte[] write(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}

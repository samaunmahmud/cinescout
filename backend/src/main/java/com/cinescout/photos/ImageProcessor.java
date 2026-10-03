package com.cinescout.photos;

import com.cinescout.logistics.GeoPoint;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Turns an uploaded photo into what is kept: the picture re-encoded from its pixels alone, so none of its metadata
 * (camera, owner, time, place) goes with it, turned upright as the camera recorded, at most {@value #MAX_SIDE} px
 * on its long side, and a thumbnail. Its GPS position is read first, as the one piece of metadata worth keeping.
 * JPEG and PNG only: the browser converts HEIC before it uploads.
 */
public final class ImageProcessor {

    static final int MAX_SIDE = 2560;
    static final int THUMB_SIDE = 480;
    /** About a 120-megapixel picture: beyond any phone, and more would not fit in memory decoded. */
    static final long MAX_PIXELS = 120_000_000L;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private ImageProcessor() {
    }

    /** What is kept of an upload; {@code gps} null when the photo did not say where it was taken. */
    public record Processed(byte[] full, String contentType, int width, int height, byte[] thumb, GeoPoint gps) {
    }

    /** @throws UnreadableImageException if the bytes are not a JPEG or PNG this can read */
    public static Processed process(byte[] upload) {
        boolean png = startsWith(upload, PNG);
        if (!png && !startsWith(upload, JPEG)) {
            throw new UnreadableImageException("Upload a JPEG, PNG or HEIC photo");
        }
        Metadata metadata = metadata(upload);
        BufferedImage image = upright(decode(upload), orientation(metadata));
        BufferedImage full = scaled(image, MAX_SIDE);
        boolean keepPng = png && full.getColorModel().hasAlpha();
        byte[] fullBytes = keepPng ? png(full) : jpeg(full, 0.85f);
        byte[] thumb = jpeg(scaled(full, THUMB_SIDE), 0.8f);
        return new Processed(fullBytes, keepPng ? "image/png" : "image/jpeg", full.getWidth(), full.getHeight(), thumb, gps(metadata));
    }

    // --- reading ------------------------------------------------------------------------------------

    private static Metadata metadata(byte[] upload) {
        try {
            return ImageMetadataReader.readMetadata(new ByteArrayInputStream(upload), upload.length);
        } catch (ImageProcessingException | IOException e) {
            return new Metadata(); // unreadable metadata is no reason to refuse the picture
        }
    }

    static GeoPoint gps(Metadata metadata) {
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        GeoLocation location = gps == null ? null : gps.getGeoLocation();
        if (location == null || location.isZero()
                || Math.abs(location.getLatitude()) > 90 || Math.abs(location.getLongitude()) > 180) {
            return null;
        }
        return new GeoPoint(location.getLatitude(), location.getLongitude());
    }

    private static int orientation(Metadata metadata) {
        ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        try {
            return exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION) ? exif.getInt(ExifIFD0Directory.TAG_ORIENTATION) : 1;
        } catch (MetadataException e) {
            return 1;
        }
    }

    /** Decoded at a reduced resolution when it is far larger than will be kept, so a huge photo stays small in memory. */
    private static BufferedImage decode(byte[] upload) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new UnreadableImageException("This photo could not be read");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new UnreadableImageException("This photo is too large; use one under 100 megapixels");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / MAX_SIDE);
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof UnreadableImageException unreadable) {
                throw unreadable;
            }
            throw new UnreadableImageException("This photo could not be read");
        }
    }

    // --- shaping ------------------------------------------------------------------------------------

    /** Turned and flipped as EXIF orientation 1 to 8 says, so it no longer needs the tag. */
    static BufferedImage upright(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { }
        }
        BufferedImage turned = new BufferedImage(swap ? h : w, swap ? w : h, type(image));
        Graphics2D g = turned.createGraphics();
        g.drawImage(image, t, null);
        g.dispose();
        return turned;
    }

    static BufferedImage scaled(BufferedImage image, int maxSide) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= maxSide && image.getType() == type(image)) {
            return image;
        }
        double ratio = Math.min(1.0, (double) maxSide / longest);
        int w = Math.max(1, (int) Math.round(image.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(image.getHeight() * ratio));
        BufferedImage out = new BufferedImage(w, h, type(image));
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static int type(BufferedImage image) {
        return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    // --- writing (no metadata: only pixels are given to the writers) --------------------------------

    private static byte[] jpeg(BufferedImage image, float quality) {
        BufferedImage rgb = image;
        if (image.getColorModel().hasAlpha()) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode a JPEG", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode a PNG", e);
        }
        return out.toByteArray();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
    }
}

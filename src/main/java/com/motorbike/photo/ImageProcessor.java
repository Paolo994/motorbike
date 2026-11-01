package com.motorbike.photo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Valida e normalizza le immagini caricate: decodifica, raddrizza secondo EXIF, ritaglia al centro
 * in un quadrato e ri-codifica in JPEG. Il file originale non viene mai salvato né servito.
 */
final class ImageProcessor {

    static final int OUTPUT_SIZE = 400;
    private static final int MIN_SIDE = 100;
    private static final long MAX_PIXELS = 40_000_000L;   // protegge da "decompression bomb"
    private static final Set<String> FORMATS = Set.of("jpeg", "png", "gif");

    private ImageProcessor() {
    }

    static class InvalidImageException extends RuntimeException {
        InvalidImageException(String message) {
            super(message);
        }
    }

    static byte[] toSquareJpeg(byte[] input) {
        BufferedImage source;
        String format;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new InvalidImageException("Il file non è un'immagine valida. Usa JPG, PNG o GIF.");
            }
            ImageReader reader = readers.next();
            try {
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!FORMATS.contains(format)) {
                    throw new InvalidImageException("Formato non supportato. Usa JPG, PNG o GIF.");
                }
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w < MIN_SIDE || h < MIN_SIDE) {
                    throw new InvalidImageException("L'immagine è troppo piccola (minimo " + MIN_SIDE + " pixel per lato).");
                }
                if ((long) w * h > MAX_PIXELS) {
                    throw new InvalidImageException("L'immagine ha una risoluzione troppo alta.");
                }
                source = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidImageException iie) {
                throw iie;
            }
            throw new InvalidImageException("Impossibile leggere l'immagine: il file potrebbe essere danneggiato.");
        }

        if ("jpeg".equals(format)) {
            source = applyExifOrientation(source, ExifOrientation.read(input));
        }
        return encodeJpeg(cropAndScale(source));
    }

    private static BufferedImage cropAndScale(BufferedImage src) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;
        int out = Math.min(OUTPUT_SIZE, side);

        BufferedImage result = new BufferedImage(out, out, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(Color.WHITE);                       // sfondo per le PNG trasparenti
            g.fillRect(0, 0, out, out);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, out, out, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    private static BufferedImage applyExifOrientation(BufferedImage img, int orientation) {
        int w = img.getWidth();
        int h = img.getHeight();
        AffineTransform t = new AffineTransform();
        boolean swap;
        switch (orientation) {
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); swap = false; }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); swap = true; }
            case 8 -> { t.translate(0, w); t.rotate(-Math.PI / 2); swap = true; }
            default -> { return img; }                     // 1 = già dritta; i casi speculari sono rarissimi
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(img, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(img, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Codifica JPEG fallita", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** Lettore minimale del tag EXIF Orientation (0x0112) da un JPEG. */
    static final class ExifOrientation {

        private ExifOrientation() {
        }

        static int read(byte[] jpeg) {
            try {
                int i = 2;                                              // dopo SOI (FFD8)
                while (i + 4 < jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
                    int marker = jpeg[i + 1] & 0xFF;
                    int len = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
                    if (marker == 0xE1 && len > 8 && new String(jpeg, i + 4, 4, "US-ASCII").equals("Exif")) {
                        return fromTiff(jpeg, i + 10, i + 2 + len);
                    }
                    if (marker == 0xDA) {
                        break;                                          // inizio dati immagine
                    }
                    i += 2 + len;
                }
            } catch (Exception ignored) {
                // EXIF malformato: nessuna rotazione
            }
            return 1;
        }

        private static int fromTiff(byte[] b, int tiff, int end) {
            boolean little = b[tiff] == 'I';
            int ifd = tiff + readInt(b, tiff + 4, little);
            int entries = readShort(b, ifd, little);
            for (int e = 0; e < entries; e++) {
                int entry = ifd + 2 + e * 12;
                if (entry + 12 > end) {
                    break;
                }
                if (readShort(b, entry, little) == 0x0112) {
                    return readShort(b, entry + 8, little);
                }
            }
            return 1;
        }

        private static int readShort(byte[] b, int p, boolean little) {
            return little ? (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8)
                          : ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF);
        }

        private static int readInt(byte[] b, int p, boolean little) {
            return little
                ? (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8) | ((b[p + 2] & 0xFF) << 16) | ((b[p + 3] & 0xFF) << 24)
                : ((b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
        }
    }
}

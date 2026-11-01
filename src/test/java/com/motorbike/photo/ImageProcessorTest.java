package com.motorbike.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.motorbike.photo.ImageProcessor.InvalidImageException;

class ImageProcessorTest {

    @Test
    void outputIsSquareJpegOfMax400px() throws Exception {
        byte[] out = ImageProcessor.toSquareJpeg(image(1200, 800, "png"));
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out));
        assertThat(img.getWidth()).isEqualTo(400);
        assertThat(img.getHeight()).isEqualTo(400);
        assertThat(out[0] & 0xFF).isEqualTo(0xFF);                 // marker JPEG SOI
        assertThat(out[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void smallImagesAreNotUpscaled() throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(ImageProcessor.toSquareJpeg(image(300, 150, "jpg"))));
        assertThat(img.getWidth()).isEqualTo(150);
    }

    @Test
    void rejectsNonImagesAndTinyImages() throws Exception {
        assertThatThrownBy(() -> ImageProcessor.toSquareJpeg("<?php echo 'ciao'; ?>".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> ImageProcessor.toSquareJpeg(image(50, 50, "png")))
            .isInstanceOf(InvalidImageException.class)
            .hasMessageContaining("troppo piccola");
        // file troncato: intestazione PNG valida ma dati mancanti
        byte[] png = image(400, 400, "png");
        byte[] truncated = java.util.Arrays.copyOf(png, 60);
        assertThatThrownBy(() -> ImageProcessor.toSquareJpeg(truncated)).isInstanceOf(InvalidImageException.class);
    }

    /**
     * Foto "da telefono": pixel salvati in orizzontale (sinistra rossa, destra blu) con EXIF Orientation=6
     * (ruotare di 90° in senso orario). Dopo la rotazione il rosso deve stare in alto e il blu in basso.
     */
    @Test
    void appliesExifOrientation() throws Exception {
        byte[] jpeg = withExifOrientation(halfRedHalfBlue(400, 200), 6);
        assertThat(ImageProcessor.ExifOrientation.read(jpeg)).isEqualTo(6);

        BufferedImage out = ImageIO.read(new ByteArrayInputStream(ImageProcessor.toSquareJpeg(jpeg)));
        assertThat(dominant(out.getRGB(10, 10))).isEqualTo("red");                       // in alto
        assertThat(dominant(out.getRGB(10, out.getHeight() - 10))).isEqualTo("blue");    // in basso

        // controllo: senza EXIF il rosso resta a sinistra anche in basso
        BufferedImage plain = ImageIO.read(new ByteArrayInputStream(
            ImageProcessor.toSquareJpeg(toJpeg(halfRedHalfBlue(400, 200)))));
        assertThat(dominant(plain.getRGB(10, plain.getHeight() - 10))).isEqualTo("red");
    }

    // ---------------------------------------------------------------- helper

    private static byte[] image(int w, int h, String format) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage halfRedHalfBlue(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w / 2, h);
        g.dispose();
        return img;
    }

    private static byte[] toJpeg(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /** Inserisce dopo SOI un segmento APP1 Exif minimo (big-endian) con il solo tag Orientation. */
    private static byte[] withExifOrientation(BufferedImage img, int orientation) throws Exception {
        byte[] jpeg = toJpeg(img);
        byte[] tiff = {
            'M', 'M', 0, 42, 0, 0, 0, 8,                 // header TIFF, IFD0 a offset 8
            0, 1,                                        // 1 voce
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,   // Orientation, SHORT, count 1
            0, 0, 0, 0                                   // nessun IFD successivo
        };
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int len = 2 + exifHeader.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);                           // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write(len >> 8);
        out.write(len & 0xFF);
        out.write(exifHeader);
        out.write(tiff);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static String dominant(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int b = rgb & 0xFF;
        return r > b ? "red" : "blue";
    }
}

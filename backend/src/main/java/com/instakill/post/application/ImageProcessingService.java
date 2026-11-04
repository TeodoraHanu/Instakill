package com.instakill.post.application;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

@Service
public class ImageProcessingService {

    public void processImage(File inputFile, File outputFile) throws IOException {
        BufferedImage originalImage = ImageIO.read(inputFile);
        if (originalImage == null) {
            return; // Nu este o imagine validă (ex: e video)
        }

        BufferedImage watermark = createWatermarkImage("Instakill Demo");

        Thumbnails.of(originalImage)
                .width(300)
                .keepAspectRatio(true)
                .watermark(Positions.BOTTOM_RIGHT, watermark, 0.8f)
                .toFile(outputFile);
    }

    private BufferedImage createWatermarkImage(String text) {
        int width = 120;
        int height = 30;
        BufferedImage watermark = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = watermark.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));

        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(0, 0, width, height);

        g2d.setColor(Color.WHITE);
        g2d.drawString(text, 10, 20);

        g2d.dispose();
        return watermark;
    }
}
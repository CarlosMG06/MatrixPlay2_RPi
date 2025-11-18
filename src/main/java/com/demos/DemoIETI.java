package com.demos;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import javax.imageio.ImageIO;

import com.piomatter.*;

public class DemoIETI {

    static final int WIDTH = 64, HEIGHT = 64;
    static final int ADDR = 5;          // ABCDE (64x64)
    static final int LANES = 2;         // 2 lanes
    static final int BRIGHTNESS = 200;  // 0..255 (software)
    static final int FPS_CAP = 0;
    static final int WAIT_SECONDS = 10;

    public static void main(String[] args) throws Exception {

        // 0) Open Piomatter
        var pm = new PioMatter(WIDTH, HEIGHT, ADDR, LANES, BRIGHTNESS, FPS_CAP);
        var fb = pm.mapFramebuffer(); 

        System.out.println("Config: WIDTH=" + WIDTH + ", HEIGHT=" + HEIGHT + ", LANES=" + LANES + ", BRIGHTNESS=" + BRIGHTNESS);

        // 1) Paint using BufferedImage from Java2D
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Prevent aliasing for “LED matrix”
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        try {
            // Clear to black before starting
            PioMatter.flushBlack(pm, fb, 3, 15);

            // Black background
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            int w2 = WIDTH / 2, h2 = HEIGHT / 2;

            // Q1: Text "IETI"
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(Color.WHITE);
            g.drawString("IETI", 5, 20);

            // Q2: Multicolor checkerboard
            int cell = 4;
            Color[] colors = new Color[]{
                new Color(0, 0, 255), new Color(0, 180, 0),
                new Color(255, 255, 0), new Color(255, 0, 0),
                new Color(255, 255, 255)
            };
            for (int yy = 0; yy < h2; yy += cell) {
                for (int xx = w2; xx < WIDTH; xx += cell) {
                    int idx = ((xx - w2) / cell + yy / cell) % colors.length;
                    g.setColor(colors[idx]);
                    g.fillRect(xx, yy, Math.min(cell, WIDTH - xx), Math.min(cell, h2 - yy));
                }
            }

            // Q3: Magenta circle filled in Cyan
            BufferedImage logo = UtilsImage.loadImage("ietilogo.png"); // from src/main/resources
            if (logo != null) {
                UtilsImage.drawImageFit(g, logo, 1, h2, w2, h2 - 2, UtilsImage.FitMode.CONTAIN);
            } else {
                // Placeholder in case image not found
                int q3cx = w2 / 2;
                int q3cy = h2 + (h2 / 2) - 1;
                int radius = Math.min(w2, h2) / 3 + 3;
                g.setColor(new Color(120, 200, 255));
                g.fillOval(q3cx - radius, q3cy - radius, radius * 2, radius * 2);
                g.setColor(Color.MAGENTA);
                g.drawOval(q3cx - radius, q3cy - radius, radius * 2, radius * 2);
            }

            // Q4: Centered "Pi5" text
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.setColor(Color.WHITE);
            String text = "Pi5";
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(text);
            int th = fm.getAscent(); // alçada “usable”
            int tx = w2 + (w2 - tw) / 2;
            int ty = h2 + ((h2 + th) / 2) - 2;
            g.drawString(text, tx, ty);

            // All: White border
            g.setColor(Color.WHITE);
            g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

            // 2) Copy BufferedImage → framebuffer RGB888 with defined brightness
            PioMatter.copyBufferedImageToRGB888(img, fb.data, fb.strideBytes, WIDTH, HEIGHT, BRIGHTNESS);

            // 3) Show result during 5 seconds
            pm.swap();
            Thread.sleep(WAIT_SECONDS * 1000);

        } finally {

            g.dispose();

            // Clear to black before exiting
            PioMatter.flushBlack(pm, fb, 3, 15);
            pm.close();
        }

        System.out.println("END basic demo.");
    }
}

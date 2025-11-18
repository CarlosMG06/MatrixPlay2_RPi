package com.demos;

import java.awt.*;
import java.awt.image.BufferedImage;

import com.piomatter.*;

public class DemoAnim {

    static final int WIDTH = 64, HEIGHT = 64;
    static final int ADDR = 5;          // ABCDE (64x64)
    static final int LANES = 2;         // 2 lanes
    static final int BRIGHTNESS = 150;  // 0..255 (software)
    static final int FPS_CAP = 60;      // FPS target
    static final int WAIT_SECONDS = 10; // Countdown duration

    // Animation states
    static final double LINE_SPEED_PX_PER_S   = (HEIGHT - 1); // vertical movement
    static final double CIRCLE_SPEED_PX_PER_S = (WIDTH - 1);  // horizontal movement
    static final int    CIRCLE_RADIUS = 8;

    public static void main(String[] args) throws Exception {

        // 0) Open Piomatter (FPS cap handled by UtilsFPS)
        var pm = new PioMatter(WIDTH, HEIGHT, ADDR, LANES, BRIGHTNESS, 0);
        var fb = pm.mapFramebuffer();

        System.out.println("Config: WIDTH=" + WIDTH + ", HEIGHT=" + HEIGHT + ", LANES=" + LANES + ", BRIGHTNESS=" + BRIGHTNESS);

        // 1) Back-buffer with Java2D
        BufferedImage back = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = back.createGraphics();

        // LED-matrix friendly hints
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Initial positions and velocities
        double lineY   = 0;
        double lineVy  = +LINE_SPEED_PX_PER_S;
        double circleX = 0;
        double circleVx = +CIRCLE_SPEED_PX_PER_S;

        // FPS helper
        var fps = new UtilsFPS();
        long lastTime = System.nanoTime();
        long startTime = lastTime;

        try {
            // Clear to black before starting
            PioMatter.flushBlack(pm, fb, 2, 10);

            while (true) {
                fps.beginFrame();

                // Calculate speed according to FPS
                double dt = fps.getDeltaSeconds();
                if (dt <= 0) dt = 1.0 / FPS_CAP;

                // Update bouncing red line
                lineY += lineVy * dt;
                if (lineY >= HEIGHT - 1) { lineY = HEIGHT - 1; lineVy = -LINE_SPEED_PX_PER_S; }
                else if (lineY <= 0)     { lineY = 0;          lineVy = +LINE_SPEED_PX_PER_S; }

                // Update bouncing circle
                circleX += circleVx * dt;
                if (circleX >= WIDTH - 1) { circleX = WIDTH - 1; circleVx = -CIRCLE_SPEED_PX_PER_S; }
                else if (circleX <= 0)    { circleX = 0;         circleVx = +CIRCLE_SPEED_PX_PER_S; }

                // Draw frame background
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, WIDTH, HEIGHT);

                // Red line
                g.setColor(Color.RED);
                int yLine = (int)Math.round(lineY);
                g.drawLine(0, yLine, WIDTH - 1, yLine);

                // Circle
                int cx = (int)Math.round(circleX);
                int cy = HEIGHT/2 + HEIGHT/4;
                int d  = CIRCLE_RADIUS * 2;
                g.setColor(Color.CYAN);
                g.fillOval(cx - CIRCLE_RADIUS, cy - CIRCLE_RADIUS, d, d);
                g.setColor(new Color(0, 200, 0));
                g.drawOval(cx - CIRCLE_RADIUS, cy - CIRCLE_RADIUS, d, d);

                // Show "countdown" (bottom-right)
                double elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
                double remaining = WAIT_SECONDS - elapsed;
                if (remaining <= 0) break;

                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                String text = String.format("%d", (int)Math.ceil(remaining));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(Color.WHITE);
                g.drawString(text, WIDTH - fm.stringWidth(text) - 2, HEIGHT - 2);

                // FPS overlay (top-left)
                fps.drawOverlay(g, 2, 10);

                // Copy BufferedImage → framebuffer RGB888 with defined brightness
                PioMatter.copyBufferedImageToRGB888(back, fb.data, fb.strideBytes, WIDTH, HEIGHT, BRIGHTNESS);

                // Present frame
                pm.swap();

                // End frame + cap
                fps.endFrameAndCap(FPS_CAP);
            }

        } finally {
            g.dispose();
            // Fade to black at the end
            PioMatter.flushBlack(pm, fb, 3, 15);
            pm.close();
        }

        System.out.println("END animated demo after " + WAIT_SECONDS + " seconds.");
    }
}

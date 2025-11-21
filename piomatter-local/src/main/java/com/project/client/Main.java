package com.project.client;

import com.piomatter.PioMatter;
import com.piomatter.UtilsFPS;
import com.piomatter.UtilsImage;
import com.piomatter.UtilsImage.FitMode;

import org.json.JSONObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

public class Main {

    // Matriu
    private static final int WIDTH = 64, HEIGHT = 64;
    private static final int ADDR = 5;          // ABCDE
    private static final int LANES = 2;         // 2 lanes
    private static final int BRIGHTNESS = 200;  // 0..255
    private static final int FPS_CAP = 60;

    // Dibuix
    private static final int TEXT_X = 5;
    // Reservem una franja superior per a l'overlay d'FPS (~10-12px) + marge.
    private static final int RESERVED_TOP = 12;
    private static final int TEXT_TOP_PAD = 2; // separació extra respecte l'overlay

    // Estat missatge
    private enum Mode { NONE, TEXT, IMAGE }
    private volatile Mode mode = Mode.NONE;
    private volatile String  text = null;
    private volatile BufferedImage image = null;
    private volatile long expireAtMs = 0L;

    private final UtilsWS ws;

    public Main(String serverUri) {
        ws = UtilsWS.getSharedInstance(serverUri);
        ws.onMessage(this::onWsMessage);
    }

    private void onWsMessage(String msg) {
        try {
            JSONObject o = new JSONObject(msg);
            String t = o.optString("type", "");
            long ttl = Math.max(1, o.optLong("ttl_ms", 5000L));
            expireAtMs = System.currentTimeMillis() + ttl;

            switch (t) {
                case "text" -> {
                    text = o.optString("message", "");
                    image = null;
                    mode = Mode.TEXT;
                    System.out.println("[client] TEXT: " + text);
                }
                case "image" -> {
                    String b64 = o.optString("b64", "");
                    if (b64.isEmpty()) { mode = Mode.NONE; return; }
                    try {
                        byte[] data = Base64.getDecoder().decode(b64);
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                        if (img != null) {
                            image = img;
                            text = null;
                            mode = Mode.IMAGE;
                            System.out.println("[client] IMAGE: " + o.optString("name", "(unnamed)"));
                        } else {
                            System.out.println("[client] IMAGE decode failed.");
                            mode = Mode.NONE;
                        }
                    } catch (Exception e) {
                        System.out.println("[client] IMAGE error: " + e.getMessage());
                        mode = Mode.NONE;
                    }
                }
                default -> {
                    // ignore
                }
            }
        } catch (Exception ignored) {}
    }

    public void run() {
        PioMatter pm = null;
        PioMatter.FB fb = null;
        BufferedImage back = null;
        Graphics2D g = null;

        final UtilsFPS fps = new UtilsFPS();

        try {
            pm = new PioMatter(WIDTH, HEIGHT, ADDR, LANES, BRIGHTNESS, 0);
            fb = pm.mapFramebuffer();

            back = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            g = back.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            final Font font = new Font("SansSerif", Font.PLAIN, 12);

            // Neteja inicial
            PioMatter.flushBlack(pm, fb, 2, 10);

            while (true) {
                fps.beginFrame();

                // Fons negre
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, WIDTH, HEIGHT);

                // Zona de dibuix de text (evitant l'overlay d'FPS)
                int startY = Math.max(0, RESERVED_TOP + TEXT_TOP_PAD);
                int availH = Math.max(0, HEIGHT - startY);
                int availW = Math.max(0, WIDTH - TEXT_X);

                // Pinta segons mode si no ha caducat
                boolean alive = System.currentTimeMillis() < expireAtMs;
                if (alive) {
                    if (mode == Mode.TEXT && text != null) {
                        g.setFont(font);
                        g.setColor(Color.WHITE);
                        FontMetrics fm = g.getFontMetrics();

                        // Word-wrap amb mètriques reals, tallat vertical i horitzontal (amb ‘…’)
                        List<String> lines = wrapText(text, fm, availW, availH);
                        int y = startY + fm.getAscent();
                        for (String line : lines) {
                            g.drawString(line, TEXT_X, y);
                            y += fm.getHeight();
                        }

                    } else if (mode == Mode.IMAGE && image != null) {
                        // Mostra la imatge amb CONTAIN dins tota la pantalla
                        UtilsImage.drawImageFit(g, image, 0, 0, WIDTH, HEIGHT, FitMode.CONTAIN);
                    }
                } else {
                    // caducat
                    mode = Mode.NONE;
                    text = null;
                    image = null;
                }

                // FPS overlay (queda per sobre)
                fps.drawOverlay(g, 1, 9);

                // Volcat framebuffer
                PioMatter.copyBufferedImageToRGB888(back, fb.data, fb.strideBytes, WIDTH, HEIGHT, BRIGHTNESS);
                pm.swap();

                // Cap FPS
                fps.endFrameAndCap(FPS_CAP);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (g != null) g.dispose();
            try { if (pm != null && fb != null) PioMatter.flushBlack(pm, fb, 2, 10); } catch (InterruptedException ignored) {}
            if (pm != null) pm.close();
            ws.forceExit();
        }
    }

    /**
     * Fa word-wrap amb mètriques (FontMetrics) respectant amplada i alçada disponibles.
     * Trunca l'última línia amb ‘…’ si no hi cap tot el text.
     */
    private static List<String> wrapText(String s, FontMetrics fm, int maxW, int maxH) {
        ArrayList<String> out = new ArrayList<>();
        if (s == null || s.isEmpty() || maxW <= 0 || maxH <= 0) return out;

        int lineH = fm.getHeight();
        int maxLines = Math.max(1, maxH / lineH);

        // Split per espais (preserva paraules); també tracta salts de línia explícits
        String[] paragraphs = s.split("\\R"); // \R = qualsevol salt de línia
        for (String para : paragraphs) {
            // Si el paràgraf és buit, afegim línia en blanc si queda espai
            if (para.isEmpty()) {
                if (out.size() < maxLines) out.add("");
                else break;
                continue;
            }

            String[] words = para.split("\\s+");
            StringBuilder line = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                String candidate = line.length() == 0 ? w : (line + " " + w);
                if (fm.stringWidth(candidate) <= maxW) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    // si la paraula sola ja és massa llarga, trenquem-la amb ellipsi
                    if (line.length() == 0) {
                        out.add(truncateWithEllipsis(w, fm, maxW));
                    } else {
                        out.add(line.toString());
                        // revalorem w a la següent línia
                        i--; // tornem a intentar afegir ‘w’ en una línia nova
                    }
                    line.setLength(0);
                    // si ja no hi ha espai vertical, sortim
                    if (out.size() >= maxLines) break;
                }
                if (out.size() >= maxLines) break;
            }

            if (out.size() >= maxLines) break;
            if (line.length() > 0) {
                out.add(line.toString());
            }

            if (out.size() >= maxLines) break;
        }

        // Si hem excedit l’alçada, trunquem l’última línia amb ‘…’
        if (out.size() > maxLines) {
            while (out.size() > maxLines) out.remove(out.size() - 1);
            String last = out.get(out.size() - 1);
            out.set(out.size() - 1, truncateWithEllipsis(last, fm, maxW));
        } else if (out.size() == maxLines) {
            // potser queda contingut pendent (no podem saber-ho fàcilment); marquem amb ‘…’ si la última ja és molt plena
            // (opcional; si no ho vols, comenta aquesta part)
            // out.set(out.size() - 1, truncateWithEllipsis(out.get(out.size() - 1), fm, maxW));
        }

        return out;
    }

    /** Trunca una cadena a maxW i hi afegeix ‘…’ si cal. */
    private static String truncateWithEllipsis(String s, FontMetrics fm, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        String ell = "…";
        int ellW = fm.stringWidth(ell);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            int w = fm.stringWidth(sb.toString() + s.charAt(i));
            if (w + ellW > maxW) break;
            sb.append(s.charAt(i));
        }
        sb.append(ell);
        return sb.toString();
    }

    public static void main(String[] args) {
        String serverURI = (args.length > 0) ? args[0] : "ws://localhost:3000";
        Main app = new Main(serverURI);
        app.run();
    }
}

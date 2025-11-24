package com.demos;

import com.piomatter.PioMatter;
import com.piomatter.UtilsFPS;
import com.piomatter.UtilsImage;
import com.piomatter.UtilsImage.FitMode;

import org.json.JSONObject;
import org.json.JSONArray;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

public class Main {

    // Tamaño de la matriz LED
    private static final int WIDTH = 64, HEIGHT = 64;
    private static final int ADDR = 5;
    private static final int LANES = 2;
    private static final int BRIGHTNESS = 200;
    private static final int FPS_CAP = 60;

    // Márgenes para texto y overlay
    private static final int TEXT_X = 5;
    private static final int RESERVED_TOP = 12;
    private static final int TEXT_TOP_PAD = 2;

    // Variables para scroll horizontal
    private volatile int scrollX = 0;
    private volatile long lastScrollTime = 0;
    private volatile String scrollingText = null;

    // Estados de visualización
    private enum Mode { NONE, TEXT, IMAGE }
    private volatile Mode mode = Mode.NONE;
    private volatile String text = null;
    private volatile BufferedImage image = null;
    private volatile long expireAtMs = 0L;

    private volatile boolean alreadyConfigured = false;

    private final UtilsWS ws;

    // Estado del juego
    private volatile boolean jocActiu = false;
    private volatile boolean countdownActive = false;
    private volatile int j1Punts = 0;
    private volatile int j2Punts = 0;
    private volatile List<GameObject> gameObjects = new ArrayList<>();


    /** Constructor: inicializa WebSocket y muestra la URL inicial */
    public Main(String serverUri) {
        ws = UtilsWS.getSharedInstance(serverUri);

        // Mostrar la URL en pantalla inicial
        this.text = "Server: " + serverUri;
        this.mode = Mode.TEXT;
        this.expireAtMs = System.currentTimeMillis() + 60_000;

        // Configurar eventos del WebSocket
        ws.onClose((reason) -> System.out.println("[client] WebSocket cerrado: " + reason));
        ws.onError((e) -> System.out.println("[client] WebSocket error: " + e));

        ws.onMessage(this::onWsMessage);
        ws.onOpen(this::onWsOpen);
    }

    /** Se ejecuta cuando el WebSocket se abre */
    private void onWsOpen(String msg) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("type", "checkMyName");
            jo.put("value", "raspberryClient");
            ws.safeSend(jo.toString());

            if (!alreadyConfigured) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("type", "raspberry");
                jsonObject.put("message", "solicito_config");
                ws.safeSend(jsonObject.toString());
                System.out.println("[client] Solicitud de configuración enviada al servidor");
            }
        } catch (Exception e) {
            System.out.println("[client] Error en onWsOpen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Procesa los mensajes recibidos del servidor */
    private void onWsMessage(String msg) {
        try {
            System.out.println("[client] RAW WS MSG: " + msg);

            JSONObject o = new JSONObject(msg);
            String t = o.optString("type", "");

            if (!t.equals("jocData")) {
                long ttl = Math.max(1, o.optLong("ttl_ms", 5000L));
                expireAtMs = System.currentTimeMillis() + ttl;
            }

            switch (t) {
                case "text" -> {
                    text = o.optString("message", "");
                    image = null;
                    mode = Mode.TEXT;
                    System.out.println("[client] TEXT: " + text);
                }

                case "config" -> {
                    String groupName = o.optString("groupName", "groupName desconocido");
                    String url = o.optString("url", "url desconocida");

                    alreadyConfigured = true;
                    text = "Grupo: " + groupName;
                    mode = Mode.TEXT;
                    scrollX = 0;
                    scrollingText = null;
                    expireAtMs = System.currentTimeMillis() + 3_000L;
                    System.out.println("[client] Nombre del grupo recibido: " + groupName);

                    new Thread(() -> {
                        try {
                            Thread.sleep(3_100L);
                            if (url != null && !url.isEmpty()) {
                                javax.swing.SwingUtilities.invokeLater(() -> {
                                    text = "URL: " + url;
                                    scrollingText = "URL: " + url;
                                    scrollX = WIDTH;
                                    lastScrollTime = System.currentTimeMillis();
                                    mode = Mode.TEXT;
                                    expireAtMs = System.currentTimeMillis() + 30_000L;
                                    System.out.println("[client] Mostrando URL con scroll: " + url);
                                });
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                }

                case "countdown" -> {
                    int value = 0;
                    JSONObject valObj = o.optJSONObject("value");
                    if (valObj != null) {
                        value = valObj.optInt("msgCountDown", 0);
                    } else {
                        value = o.optInt("value", 0);
                    }

                    if (value > 0) {
                        countdownActive = true;
                        jocActiu = false;
                        j1Punts = 0;
                        j2Punts = 0;

                        text = String.valueOf(value);
                        mode = Mode.TEXT;
                        System.out.println("[client] Countdown: " + value);
                    } else {
                        countdownActive = false;
                        text = null;
                        mode = Mode.NONE;
                        System.out.println("[client] Countdown terminado");
                    }
                    image = null;
                }

                case "serverData" -> {
                    JSONObject serverGame = o.optJSONObject("serverGameData");
                    if (serverGame != null) {
                        j1Punts = serverGame.optInt("p1Points", j1Punts);
                        j2Punts = serverGame.optInt("p2Points", j2Punts);

                        GameObject[] gos = GameObject.fromServerState(serverGame, WIDTH, HEIGHT, RESERVED_TOP);
                        gameObjects.clear();
                        for (GameObject go : gos) gameObjects.add(go);

                        if (!countdownActive) {
                            jocActiu = true;
                            mode = Mode.NONE;
                        } else {
                            System.out.println("[client] serverData recibida pero IGNORADA porque hay countdown activo");
                        }

                        System.out.println("[client] serverData recibida -> p1=" + j1Punts + " p2=" + j2Punts + " objs=" + gameObjects.size());
                    }
                }

                case "jocData" -> {
                    String estatPartida = o.optString("estatPartida", "");
                    if (estatPartida.equals("Jugant")) {
                        jocActiu = !countdownActive;
                        j1Punts = o.optInt("p1Points", o.optInt("J1Punts", j1Punts));
                        j2Punts = o.optInt("p2Points", o.optInt("J2Punts", j2Punts));

                        JSONArray objectsList = o.optJSONArray("objectsList");
                        if (objectsList != null) {
                            gameObjects.clear();
                            for (int i = 0; i < objectsList.length(); i++) {
                                JSONObject object = objectsList.getJSONObject(i);
                                GameObject go = GameObject.fromJSONScaledToGameArea(
                                        object, WIDTH, HEIGHT, RESERVED_TOP, 600, 400);
                                gameObjects.add(go);
                            }
                        }
                    } else {
                        jocActiu = false;
                        gameObjects.clear();
                    }
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
                    if (!t.isEmpty()) System.out.println("[client] Tipo desconocido recibido: " + t);
                }
            }
        } catch (Exception e) {
            System.out.println("[client] Error procesando mensaje WS:");
            e.printStackTrace();
        }
    }

    /** Bucle principal de renderizado en la RPi */
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
            PioMatter.flushBlack(pm, fb, 2, 10);

            // ===== Mostrar QR frame.png al inicio =====
            try {
                BufferedImage qrImage = UtilsImage.loadImage("frame.png");
                if (qrImage != null) {
                    UtilsImage.drawImageFit(g, qrImage, 0, 0, WIDTH, HEIGHT, FitMode.CONTAIN);
                    PioMatter.copyBufferedImageToRGB888(back, fb.data, fb.strideBytes, WIDTH, HEIGHT, BRIGHTNESS);
                    pm.swap();
                    Thread.sleep(10000);
                } else {
                    System.out.println("[QR] No se pudo cargar frame.png");
                }
            } catch (Exception e) {
                System.out.println("[QR] Error mostrando QR: " + e.getMessage());
            }

            while (true) {
                fps.beginFrame();

                // PRIORIDAD: si hay countdown activo, mostrar siempre la cuenta atrás
                if (countdownActive) {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, WIDTH, HEIGHT);

                    g.setColor(Color.WHITE);
                    Font countdownFont = new Font("SansSerif", Font.BOLD, 20);
                    g.setFont(countdownFont);
                    FontMetrics fm = g.getFontMetrics();
                    String display = (text != null) ? text : "";
                    int textW = fm.stringWidth(display);
                    int x = Math.max(0, (WIDTH - textW) / 2);
                    int y = (HEIGHT / 2) + (fm.getAscent() / 2);
                    g.drawString(display, x, y);

                    g.setFont(new Font("SansSerif", Font.BOLD, 9));
                    FontMetrics fmTop = g.getFontMetrics();
                    g.drawString("PONG GAME", 1, fmTop.getAscent());

                    PioMatter.copyBufferedImageToRGB888(back, fb.data, fb.strideBytes, WIDTH, HEIGHT, BRIGHTNESS);
                    pm.swap();
                    fps.endFrameAndCap(FPS_CAP);
                    continue;
                }

                if (jocActiu) {
                    g.setColor(Color.BLUE);
                    g.fillRect(0, RESERVED_TOP, WIDTH, HEIGHT - RESERVED_TOP);

                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, WIDTH, RESERVED_TOP);

                    g.setColor(Color.WHITE);
                    Font scoreFont = new Font("SansSerif", Font.BOLD, 10);
                    g.setFont(scoreFont);
                    FontMetrics fmTop = g.getFontMetrics();

                    g.drawString(String.valueOf(j1Punts), 2, fmTop.getAscent());
                    g.drawString(String.valueOf(j2Punts),
                            WIDTH - fmTop.stringWidth(String.valueOf(j2Punts)) - 2,
                            fmTop.getAscent());

                    for (GameObject go : new ArrayList<>(gameObjects)) {
                        Color col = switch (go.color.toUpperCase()) {
                            case "RED" -> Color.RED;
                            case "BLACK" -> Color.BLACK;
                            case "WHITE" -> Color.WHITE;
                            default -> Color.GRAY;
                        };
                        g.setColor(col);
                        g.fillRect(go.x, go.y, go.ancho, go.alto);
                    }
                } else {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, WIDTH, HEIGHT);

                    g.setColor(Color.WHITE);
                    Font titleFont = new Font("SansSerif", Font.BOLD, 9);
                    g.setFont(titleFont);
                    FontMetrics fmTop = g.getFontMetrics();
                    g.drawString("PONG GAME", 1, fmTop.getAscent());

                    int startY = RESERVED_TOP + TEXT_TOP_PAD;
                    int availH = HEIGHT - startY;
                    int availW = WIDTH - TEXT_X;
                    boolean alive = System.currentTimeMillis() < expireAtMs;

                    if (alive && mode == Mode.TEXT && text != null) {
                        g.setFont(font);
                        g.setColor(Color.WHITE);
                        FontMetrics fm = g.getFontMetrics();
                        int textWidth = fm.stringWidth(text);

                        if (textWidth > availW && scrollingText != null) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastScrollTime > 100) {
                                scrollX -= 1;
                                lastScrollTime = currentTime;
                                if (scrollX + textWidth < 0) scrollX = WIDTH;
                            }
                            g.drawString(scrollingText, TEXT_X + scrollX, startY + fm.getAscent());
                        } else {
                            List<String> lines = wrapText(text, fm, availW, availH);
                            int y = startY + fm.getAscent();
                            for (String line : lines) {
                                g.drawString(line, TEXT_X, y);
                                y += fm.getHeight();
                            }
                        }

                    } else if (alive && mode == Mode.IMAGE && image != null) {
                        UtilsImage.drawImageFit(g, image, 0, 0, WIDTH, HEIGHT, FitMode.CONTAIN);
                    } else {
                        mode = Mode.NONE;
                        text = null;
                        image = null;
                        scrollingText = null;
                        scrollX = 0;
                    }
                }

                PioMatter.copyBufferedImageToRGB888(back, fb.data, fb.strideBytes, WIDTH, HEIGHT, BRIGHTNESS);
                pm.swap();
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

    private static List<String> wrapText(String s, FontMetrics fm, int maxW, int maxH) {
        ArrayList<String> out = new ArrayList<>();
        if (s == null || s.isEmpty() || maxW <= 0 || maxH <= 0) return out;

        int lineH = fm.getHeight();
        int maxLines = Math.max(1, maxH / lineH);

        String[] paragraphs = s.split("\\R");
        for (String para : paragraphs) {
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
                    if (line.length() == 0) out.add(truncateWithEllipsis(w, fm, maxW));
                    else {
                        out.add(line.toString());
                        i--;
                    }
                    line.setLength(0);
                    if (out.size() >= maxLines) break;
                }
                if (out.size() >= maxLines) break;
            }

            if (line.length() > 0) out.add(line.toString());
            if (out.size() >= maxLines) break;
        }

        if (out.size() > maxLines) {
            while (out.size() > maxLines) out.remove(out.size() - 1);
            String last = out.get(out.size() - 1);
            out.set(out.size() - 1, truncateWithEllipsis(last, fm, maxW));
        }

        return out;
    }

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

    public static String loadServerUriFromConfig() {
        try {
            String content = new String(Files.readAllBytes(Paths.get("/home/pi/Adafruit_Pi5_Piomatter/piomatter-java-jni/config.json")));
            JSONObject json = new JSONObject(content);
            return json.optString("serverUri", "wss://matrixplay2.ieti.site:443");
        } catch (Exception e) {
            e.printStackTrace();
            return "ws://localhost:3000";
        }
    }

    public static void main(String[] args) {
        String serverURI = (args.length > 0) ? args[0] : loadServerUriFromConfig();
        Main app = new Main(serverURI);
        app.run();
    }
}

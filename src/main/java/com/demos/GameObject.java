package com.demos;

import org.json.JSONObject;

public class GameObject {
    public String id;
    public int x;
    public int y;
    public int ancho;
    public int alto;
    public String color; 

    public GameObject(String id, int x, int y, int ancho, int alto, String color) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.ancho = ancho;
        this.alto = alto;
        this.color = color;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
    
    // Convierte el objeto a JSON
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("x", x);
        obj.put("y", y);
        obj.put("ancho", ancho);
        obj.put("alto", alto);
        obj.put("color", color);
        return obj;
    }

    // Crea un GameObject a partir de JSON con escalado a ventana
    public static GameObject fromJSON(JSONObject obj, int ampladaFinestra, int alcadaFinestra) {

        int xLog = obj.optInt("x", 0);
        int yLog = obj.optInt("y", 0);
        int anchoLog = obj.optInt("ancho", 1);
        int altoLog = obj.optInt("alto", 1);

        int xPix = (int) ((xLog / 600f) * ampladaFinestra);
        int yPix = (int) ((yLog / 400f) * alcadaFinestra);
        int anchoPix = (int) ((anchoLog / 600f) * ampladaFinestra);
        int altoPix = (int) ((altoLog / 400f) * alcadaFinestra);

        return new GameObject(
            obj.optString("id", null),
            xPix,
            yPix,
            anchoPix,
            altoPix,
            obj.optString("color", "gray")
        );
    }

    // Crea un GameObject a partir de JSON con escalado al área de juego
    public static GameObject fromJSONScaledToGameArea(
            JSONObject obj,
            int pantallaAncho,
            int pantallaAlto,
            int reservedTop,
            int logicWidth,
            int logicHeight) {

        int xLog = obj.optInt("x", 0);
        int yLog = obj.optInt("y", 0);
        int anchoLog = obj.optInt("ancho", 1);
        int altoLog = obj.optInt("alto", 1);

        int gameHeight = pantallaAlto - reservedTop;

        float scaleX = pantallaAncho / (float) logicWidth;
        float scaleY = gameHeight / (float) logicHeight;

        int xPix = (int) (xLog * scaleX);
        int anchoPix = (int) (anchoLog * scaleX);

        int yPix = reservedTop + (int) (yLog * scaleY);
        int altoPix = (int) (altoLog * scaleY);

        return new GameObject(
            obj.optString("id", ""),
            xPix,
            yPix,
            anchoPix,
            altoPix,
            obj.optString("color", "gray")
        );
    }

    // Crea un array de GameObjects (player1, player2, ball) a partir del estado del servidor
    public static GameObject[] fromServerState(JSONObject serverData, int pantallaAncho, int pantallaAlto, int reservedTop) {

        GameObject player1 = fromJSONScaledToGameArea(new JSONObject()
                .put("id", "player1")
                .put("x", 0)
                .put("y", serverData.optInt("p1PossY", 0))
                .put("ancho", 3)
                .put("alto", 16)
                .put("color", "blue"),
                pantallaAncho, pantallaAlto, reservedTop, 64, 64);

        GameObject player2 = fromJSONScaledToGameArea(new JSONObject()
                .put("id", "player2")
                .put("x", 61)
                .put("y", serverData.optInt("p2PossY", 0))
                .put("ancho", 3)
                .put("alto", 16)
                .put("color", "red"),
                pantallaAncho, pantallaAlto, reservedTop, 64, 64);

        GameObject ball = fromJSONScaledToGameArea(new JSONObject()
                .put("id", "ball")
                .put("x", serverData.optInt("ballX", 32))
                .put("y", serverData.optInt("ballY", 32))
                .put("ancho", 2)
                .put("alto", 2)
                .put("color", "white"),
                pantallaAncho, pantallaAlto, reservedTop, 64, 64);

        return new GameObject[]{player1, player2, ball};
    }
}

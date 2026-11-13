package com.example.blockblast;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Random;

public class GameView extends View {

    private static final int N = 8;
    private static final float TRAY_SCALE = 0.5f;
    private static final String[] SHAPES = {
            "X", "XX", "X/X", "XXX", "X/X/X", "XXXX", "X/X/X/X", "XXXXX", "X/X/X/X/X",
            "XX/XX", "XXX/XXX/XXX", "X./XX", "XX/X.", "XX/.X", ".X/XX",
            "X../X../XXX", "XXX/X../X..", "XXX/..X/..X", "..X/..X/XXX",
            "XXX/.X.", ".X./XXX", "X./XX/X.", ".X/XX/.X", "XX/XX/XX", "XXX/XXX"
    };
    private static final int[] COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835,
            0xFF8E24AA, 0xFFFB8C00, 0xFF00ACC1, 0xFFEC407A
    };

    private static class Piece {
        boolean[][] cells;
        int w, h, color, count;
    }

    private final int[][] grid = new int[N][N]; // 0 = vacío, otro = color
    private final Piece[] tray = new Piece[3];
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rectF = new RectF();

    private Bitmap bgBitmap, tileBitmap;
    private float cell, bx, by, trayCY, slotW;
    private int drag = -1;
    private float dx, dy;
    private int score, best;
    private boolean gameOver;
    private final SharedPreferences prefs;

    public GameView(Context ctx) {
        super(ctx);
        prefs = ctx.getSharedPreferences("blockblast", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        bgBitmap = BitmapFactory.decodeFile(new File(ctx.getFilesDir(), "bg.png").getPath());
        tileBitmap = BitmapFactory.decodeFile(new File(ctx.getFilesDir(), "tile.png").getPath());
        restart();
    }

    // ---------- Personalización ----------

    public void setImage(Uri uri, boolean background) {
        try {
            Bitmap b = decode(uri);
            if (b == null) return;
            if (!background) {
                int s = Math.min(b.getWidth(), b.getHeight());
                Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - s) / 2, (b.getHeight() - s) / 2, s, s);
                b = Bitmap.createScaledBitmap(sq, 128, 128, true);
            }
            File f = new File(getContext().getFilesDir(), background ? "bg.png" : "tile.png");
            try (FileOutputStream o = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, o);
            }
            if (background) bgBitmap = b; else tileBitmap = b;
            invalidate();
        } catch (Exception ignored) { }
    }

    public void resetImages() {
        new File(getContext().getFilesDir(), "bg.png").delete();
        new File(getContext().getFilesDir(), "tile.png").delete();
        bgBitmap = null;
        tileBitmap = null;
        invalidate();
    }

    private Bitmap decode(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int s = 1;
        while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, o);
        }
    }

    // ---------- Lógica ----------

    private void restart() {
        for (int[] r : grid) java.util.Arrays.fill(r, 0);
        score = 0;
        gameOver = false;
        refill();
        invalidate();
    }

    private Piece newPiece() {
        String[] rows = SHAPES[rnd.nextInt(SHAPES.length)].split("/");
        Piece p = new Piece();
        p.h = rows.length;
        p.w = rows[0].length();
        p.cells = new boolean[p.h][p.w];
        for (int r = 0; r < p.h; r++)
            for (int c = 0; c < p.w; c++)
                if (rows[r].charAt(c) == 'X') { p.cells[r][c] = true; p.count++; }
        p.color = COLORS[rnd.nextInt(COLORS.length)];
        return p;
    }

    private void refill() {
        for (int i = 0; i < 3; i++) tray[i] = newPiece();
    }

    private boolean canPlace(Piece p, int gx, int gy) {
        if (gx < 0 || gy < 0 || gx + p.w > N || gy + p.h > N) return false;
        for (int r = 0; r < p.h; r++)
            for (int c = 0; c < p.w; c++)
                if (p.cells[r][c] && grid[gy + r][gx + c] != 0) return false;
        return true;
    }

    private boolean anyMove() {
        for (Piece p : tray) {
            if (p == null) continue;
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++)
                    if (canPlace(p, x, y)) return true;
        }
        return false;
    }

    private void place(int idx, int gx, int gy) {
        Piece p = tray[idx];
        for (int r = 0; r < p.h; r++)
            for (int c = 0; c < p.w; c++)
                if (p.cells[r][c]) grid[gy + r][gx + c] = p.color;
        score += p.count;

        boolean[] row = new boolean[N], col = new boolean[N];
        int lines = 0;
        for (int i = 0; i < N; i++) {
            row[i] = true; col[i] = true;
            for (int j = 0; j < N; j++) {
                if (grid[i][j] == 0) row[i] = false;
                if (grid[j][i] == 0) col[i] = false;
            }
            if (row[i]) lines++;
            if (col[i]) lines++;
        }
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                if (row[i] || col[j]) grid[i][j] = 0;
        score += lines * 10 * Math.max(1, lines);

        tray[idx] = null;
        if (tray[0] == null && tray[1] == null && tray[2] == null) refill();
        if (score > best) { best = score; prefs.edit().putInt("best", best).apply(); }
        if (!anyMove()) gameOver = true;
    }

    // ---------- Dibujo ----------

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cell = Math.min(w * 0.94f / N, h * 0.5f / N);
        bx = (w - cell * N) / 2f;
        by = h * 0.16f;
        float boardBottom = by + cell * N;
        trayCY = boardBottom + (h - boardBottom) * 0.45f;
        slotW = w / 3f;
    }

    private void drawCell(Canvas c, float l, float t, float size, int color, int alpha) {
        rectF.set(l + 1, t + 1, l + size - 1, t + size - 1);
        paint.setAlpha(alpha);
        if (tileBitmap != null) {
            c.drawBitmap(tileBitmap, null, rectF, paint);
        } else {
            paint.setColor(color);
            paint.setAlpha(alpha);
            c.drawRoundRect(rectF, size * 0.15f, size * 0.15f, paint);
            paint.setColor(Color.WHITE);
            paint.setAlpha(alpha / 4);
            rectF.set(l + size * 0.12f, t + size * 0.1f, l + size * 0.88f, t + size * 0.4f);
            c.drawRoundRect(rectF, size * 0.1f, size * 0.1f, paint);
        }
        paint.setAlpha(255);
    }

    private void drawPiece(Canvas c, Piece p, float l, float t, float size, int alpha) {
        for (int r = 0; r < p.h; r++)
            for (int k = 0; k < p.w; k++)
                if (p.cells[r][k]) drawCell(c, l + k * size, t + r * size, size, p.color, alpha);
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();

        // Fondo
        if (bgBitmap != null) {
            float s = Math.max((float) W / bgBitmap.getWidth(), (float) H / bgBitmap.getHeight());
            int sw = (int) (W / s), sh = (int) (H / s);
            int sl = (bgBitmap.getWidth() - sw) / 2, st = (bgBitmap.getHeight() - sh) / 2;
            c.drawBitmap(bgBitmap, new Rect(sl, st, sl + sw, st + sh), new Rect(0, 0, W, H), paint);
        } else {
            paint.setShader(new LinearGradient(0, 0, 0, H, 0xFF2B3A8C, 0xFF111733, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, paint);
            paint.setShader(null);
        }

        // Panel del tablero
        paint.setColor(Color.BLACK);
        paint.setAlpha(110);
        rectF.set(bx - 8, by - 8, bx + cell * N + 8, by + cell * N + 8);
        c.drawRoundRect(rectF, 20, 20, paint);
        paint.setAlpha(255);

        // Celdas
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float l = bx + x * cell, t = by + y * cell;
                if (grid[y][x] != 0) {
                    drawCell(c, l, t, cell, grid[y][x], 255);
                } else {
                    paint.setColor(Color.WHITE);
                    paint.setAlpha(28);
                    rectF.set(l + 2, t + 2, l + cell - 2, t + cell - 2);
                    c.drawRoundRect(rectF, cell * 0.12f, cell * 0.12f, paint);
                    paint.setAlpha(255);
                }
            }

        // Puntuación
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(cell * 0.9f);
        c.drawText(String.valueOf(score), W / 2f, by - cell * 0.45f, paint);
        paint.setTextSize(cell * 0.4f);
        c.drawText("Mejor: " + best, W / 2f, by - cell * 1.5f + cell * 0.1f, paint);

        // Piezas de la bandeja
        for (int i = 0; i < 3; i++) {
            Piece p = tray[i];
            if (p == null || i == drag) continue;
            float s = cell * TRAY_SCALE;
            drawPiece(c, p, slotW * (i + 0.5f) - p.w * s / 2f, trayCY - p.h * s / 2f, s, 255);
        }

        // Pieza arrastrada + previsualización
        if (drag >= 0 && tray[drag] != null) {
            Piece p = tray[drag];
            float l = dx - p.w * cell / 2f, t = dy - p.h * cell / 2f - cell * 2f;
            int gx = Math.round((l - bx) / cell), gy = Math.round((t - by) / cell);
            if (canPlace(p, gx, gy)) drawPiece(c, p, bx + gx * cell, by + gy * cell, cell, 90);
            drawPiece(c, p, l, t, cell, 255);
        }

        // Fin del juego
        if (gameOver) {
            paint.setColor(Color.BLACK);
            paint.setAlpha(170);
            c.drawRect(0, 0, W, H, paint);
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);
            paint.setTextSize(cell * 1.1f);
            c.drawText("Fin del juego", W / 2f, H * 0.42f, paint);
            paint.setTextSize(cell * 0.6f);
            c.drawText("Puntos: " + score, W / 2f, H * 0.42f + cell * 1.2f, paint);
            c.drawText("Toca para reiniciar", W / 2f, H * 0.42f + cell * 2.2f, paint);
        }
    }

    // ---------- Táctil ----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (gameOver) { restart(); return true; }
                for (int i = 0; i < 3; i++) {
                    if (tray[i] != null && x >= slotW * i && x < slotW * (i + 1)
                            && Math.abs(y - trayCY) < cell * 1.8f) {
                        drag = i; dx = x; dy = y;
                        invalidate();
                        break;
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (drag >= 0) { dx = x; dy = y; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                if (drag >= 0) {
                    Piece p = tray[drag];
                    float l = x - p.w * cell / 2f, t = y - p.h * cell / 2f - cell * 2f;
                    int gx = Math.round((l - bx) / cell), gy = Math.round((t - by) / cell);
                    if (canPlace(p, gx, gy)) place(drag, gx, gy);
                }
                drag = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                drag = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }
                      }

package com.example.blockblast;

import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private GameView game;

    private final ActivityResultLauncher<String> pickBackground =
            registerForActivityResult(new ActivityResultContracts.GetContent(), (Uri uri) -> {
                if (uri != null) game.setImage(uri, true);
            });

    private final ActivityResultLauncher<String> pickTile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), (Uri uri) -> {
                if (uri != null) game.setImage(uri, false);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);

        game = new GameView(this);
        root.addView(game, new FrameLayout.LayoutParams(-1, -1));

        Button bg = new Button(this);
        bg.setText("Fondo");
        bg.setOnClickListener(v -> pickBackground.launch("image/*"));

        Button tile = new Button(this);
        tile.setText("Bloques");
        tile.setOnClickListener(v -> pickTile.launch("image/*"));

        Button reset = new Button(this);
        reset.setText("Restablecer");
        reset.setOnClickListener(v -> game.resetImages());

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(bg);
        bar.addView(tile);
        bar.addView(reset);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        root.addView(bar, lp);

        setContentView(root);
    }
}

package com.fakegps.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fakegps.R;
import com.fakegps.service.FloatingWindowService;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnFloating = findViewById(R.id.btn_open_floating);
        Button btnAccessibility = findViewById(R.id.btn_open_accessibility);
        Button btnDevSettings = findViewById(R.id.btn_open_dev);

        btnFloating.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            } else {
                startService(new Intent(this, FloatingWindowService.class));
                Toast.makeText(this, "悬浮窗已启动，请打开微信小程序", Toast.LENGTH_SHORT).show();
            }
        });

        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "请在无障碍列表中开启【黄科大智能避障巡航】", Toast.LENGTH_LONG).show();
        });

        btnDevSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                Toast.makeText(this, "请在开发者选项中将本应用设为【模拟位置信息应用】", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "请先在手机设置中开启开发者选项", Toast.LENGTH_SHORT).show();
            }
        });
    }
}

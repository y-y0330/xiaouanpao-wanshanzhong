package com.fakegps.service;

import android.app.AlertDialog;
import android.app.Service;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.*;
import android.widget.*;
import com.fakegps.R;
import com.fakegps.engine.CampusRoadGraph;
import java.util.*;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvStatus;
    private Button btnAction;
    private final List<double[]> collectedPoints = new ArrayList<>();
    private CampusRoadGraph graph;
    private boolean isRunning = false;
    private double currentSpeedMps = 3.1;

    private static final String[] LANDMARK_NAMES = {
            "东田径场(跑道)", "图书馆西侧", "信息大楼(众创空间)", "文化广场",
            "仲景园/西体育馆", "工学部智创楼", "南广场", "综合体育馆/游泳馆",
            "艺术中心/智慧楼", "小北门/厚德路", "西门/开拓路", "东门/博学路"
    };
    private static final double[][] LANDMARK_COORDS = {
            {34.6924, 113.6878}, {34.6925, 113.6895}, {34.6930, 113.6855}, {34.6938, 113.6868},
            {34.6938, 113.6840}, {34.6905, 113.6842}, {34.6885, 113.6855}, {34.6885, 113.6898},
            {34.6885, 113.6825}, {34.6962, 113.6862}, {34.6945, 113.6812}, {34.6938, 113.6912}
    };

    private final BroadcastReceiver pointReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double[] data = intent.getDoubleArrayExtra(TaskAccessibilityService.EXTRA_POINTS_DATA);
            String info = intent.getStringExtra(TaskAccessibilityService.EXTRA_LOG_INFO);
            if (data != null && data.length >= 2) {
                for (int i = 0; i < data.length; i += 2) {
                    collectedPoints.add(new double[]{data[i], data[i + 1]});
                }
                tvStatus.setText("已捕获点位: " + collectedPoints.size() + " 个\n" + (info != null ? info : ""));
                btnAction.setEnabled(true);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        graph = CampusRoadGraph.buildHuangKeDaSouth();
        IntentFilter filter = new IntentFilter(TaskAccessibilityService.ACTION_DETECTED_POINTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pointReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pointReceiver, filter);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 60;
        params.y = 220;

        tvStatus = floatingView.findViewById(R.id.tv_float_status);
        btnAction = floatingView.findViewById(R.id.btn_float_action);
        Button btnClear = floatingView.findViewById(R.id.btn_float_clear);
        Button btnAddManual = floatingView.findViewById(R.id.btn_float_manual);

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float touchX, touchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x;
                        initY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initX + (int) (event.getRawX() - touchX);
                        params.y = initY + (int) (event.getRawY() - touchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        btnAction.setOnClickListener(v -> {
            if (!isRunning) {
                if (collectedPoints.isEmpty()) {
                    Toast.makeText(this, "请先打开小程序自动扫描，或点击【选点】勾选点位", Toast.LENGTH_SHORT).show();
                    return;
                }
                double[] start = collectedPoints.get(0);
                List<CampusRoadGraph.Node> route = graph.planMultiTargetRoute(start[0], start[1], collectedPoints);

                LocationMockService.setTargetNodes(route, currentSpeedMps, true);
                Intent mockIntent = new Intent(this, LocationMockService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(mockIntent);
                } else {
                    startService(mockIntent);
                }

                isRunning = true;
                btnAction.setText("停止巡航");
                tvStatus.setText(String.format(Locale.CHINA, "避障巡航中 (%.1fm/s)\n途经 %d 个路口节点", currentSpeedMps, route.size()));
            } else {
                stopService(new Intent(this, LocationMockService.class));
                isRunning = false;
                btnAction.setText("开始巡航");
                tvStatus.setText("模拟已停止");
            }
        });

        btnClear.setOnClickListener(v -> {
            collectedPoints.clear();
            tvStatus.setText("点位已清空，等待扫描或选点");
            btnAction.setEnabled(false);
        });

        btnAddManual.setOnClickListener(v -> showLandmarkSelectionDialog(layoutType));

        windowManager.addView(floatingView, params);
    }

    private void showLandmarkSelectionDialog(int layoutType) {
        boolean[] checked = new boolean[LANDMARK_NAMES.length];
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("勾选本次微信打卡点位")
                .setMultiChoiceItems(LANDMARK_NAMES, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("确定规划", (d, which) -> {
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            collectedPoints.add(LANDMARK_COORDS[i]);
                        }
                    }
                    if (!collectedPoints.isEmpty()) {
                        tvStatus.setText("已选定打卡点: " + collectedPoints.size() + " 个");
                        btnAction.setEnabled(true);
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(layoutType);
        }
        dialog.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
        unregisterReceiver(pointReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

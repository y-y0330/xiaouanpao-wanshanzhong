package com.fakegps.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskAccessibilityService extends AccessibilityService {
    public static final String ACTION_DETECTED_POINTS = "com.fakegps.ACTION_DETECTED_POINTS";
    public static final String EXTRA_POINTS_DATA = "extra_points_data";
    public static final String EXTRA_LOG_INFO = "extra_log_info";

    private final Pattern latLngPattern = Pattern.compile("(\\d{2}\\.\\d{4,7})[,，\\s]+(\\d{3}\\.\\d{4,7})");
    private final Set<String> detectedSignature = new HashSet<>();

    private static final Map<String, double[]> NAME_TO_COORD = new HashMap<>();
    static {
        NAME_TO_COORD.put("图书馆", new double[]{34.6925, 113.6895});
        NAME_TO_COORD.put("东田径场", new double[]{34.6924, 113.6878});
        NAME_TO_COORD.put("西田径场", new double[]{34.6948, 113.6845});
        NAME_TO_COORD.put("信息大楼", new double[]{34.6930, 113.6855});
        NAME_TO_COORD.put("南广场", new double[]{34.6885, 113.6855});
        NAME_TO_COORD.put("体育馆", new double[]{34.6885, 113.6898});
        NAME_TO_COORD.put("行政办公楼", new double[]{34.6925, 113.6835});
        NAME_TO_COORD.put("老子园", new double[]{34.6945, 113.6885});
        NAME_TO_COORD.put("文化广场", new double[]{34.6938, 113.6868});
        NAME_TO_COORD.put("艺术中心", new double[]{34.6885, 113.6825});
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        CharSequence pkg = event.getPackageName();
        if (pkg != null && pkg.toString().contains("com.tencent.mm")) {
            List<double[]> pointsFound = new ArrayList<>();
            List<String> names = new ArrayList<>();
            scanNodes(root, pointsFound, names);

            if (!pointsFound.isEmpty()) {
                Intent broadcast = new Intent(ACTION_DETECTED_POINTS);
                double[] flatArray = new double[pointsFound.size() * 2];
                for (int i = 0; i < pointsFound.size(); i++) {
                    flatArray[i * 2] = pointsFound.get(i)[0];
                    flatArray[i * 2 + 1] = pointsFound.get(i)[1];
                }
                broadcast.putExtra(EXTRA_POINTS_DATA, flatArray);
                broadcast.putExtra(EXTRA_LOG_INFO, "检测到: " + String.join(", ", names));
                sendBroadcast(broadcast);
            }
        }
    }

    private void scanNodes(AccessibilityNodeInfo node, List<double[]> result, List<String> names) {
        if (node == null) return;

        CharSequence textSeq = node.getText();
        if (textSeq != null) {
            String text = textSeq.toString().trim();

            Matcher matcher = latLngPattern.matcher(text);
            if (matcher.find()) {
                try {
                    double lat = Double.parseDouble(matcher.group(1));
                    double lng = Double.parseDouble(matcher.group(2));
                    String key = lat + "," + lng;
                    if (!detectedSignature.contains(key)) {
                        detectedSignature.add(key);
                        result.add(new double[]{lat, lng});
                        names.add("坐标(" + lat + "," + lng + ")");
                    }
                } catch (Exception ignored) {}
            }

            for (Map.Entry<String, double[]> entry : NAME_TO_COORD.entrySet()) {
                if (text.contains(entry.getKey())) {
                    String key = entry.getKey();
                    if (!detectedSignature.contains(key)) {
                        detectedSignature.add(key);
                        result.add(entry.getValue());
                        names.add(key);
                    }
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            scanNodes(node.getChild(i), result, names);
        }
    }

    @Override
    public void onInterrupt() {}
}

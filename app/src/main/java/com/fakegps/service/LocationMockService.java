package com.fakegps.service;

import android.app.*;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.*;
import androidx.core.app.NotificationCompat;
import com.fakegps.engine.CampusRoadGraph;
import java.util.*;

public class LocationMockService extends Service {
    private static final String CHANNEL_ID = "MockLocationChannel";
    private LocationManager locationManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private static List<CampusRoadGraph.Node> targetNodes = new ArrayList<>();
    private static double baseSpeedMps = 3.1;
    private static boolean loopMode = true;

    private final List<Location> stepLocations = new ArrayList<>();
    private int currentIndex = 0;

    public static void setTargetNodes(List<CampusRoadGraph.Node> nodes, double speedMps, boolean loop) {
        targetNodes = nodes;
        baseSpeedMps = speedMps;
        loopMode = loop;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("[FakeGPS v3.0] 黄科大智能避障巡航中")
                .setContentText("双源定位(GPS+Network)与正弦配速波动已激活")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
        startForeground(101, notification);
        initMockProviders();
    }

    private void initMockProviders() {
        String[] providers = new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
        for (String provider : providers) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.addTestProvider(provider, false, false, false, false, true, true, true,
                            ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE);
                } else {
                    locationManager.addTestProvider(provider, false, false, false, false, true, true, true, 1, 1);
                }
                locationManager.setTestProviderEnabled(provider, true);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (targetNodes != null && targetNodes.size() > 1) {
            generateRealisticInterpolatedTrack(targetNodes, baseSpeedMps);
            currentIndex = 0;
            handler.removeCallbacks(mockLoop);
            handler.post(mockLoop);
        }
        return START_STICKY;
    }

    private void generateRealisticInterpolatedTrack(List<CampusRoadGraph.Node> nodes, double speedMps) {
        stepLocations.clear();
        double phase = random.nextDouble() * Math.PI * 2;

        for (int i = 0; i < nodes.size() - 1; i++) {
            CampusRoadGraph.Node p1 = nodes.get(i);
            CampusRoadGraph.Node p2 = nodes.get(i + 1);

            double dist = CampusRoadGraph.distance(p1.lat, p1.lng, p2.lat, p2.lng);
            float bearing = CampusRoadGraph.calculateBearing(p1.lat, p1.lng, p2.lat, p2.lng);
            int steps = (int) Math.max(1, dist / speedMps);

            for (int s = 0; s < steps; s++) {
                double fraction = (double) s / steps;
                double gcjLat = p1.lat + (p2.lat - p1.lat) * fraction;
                double gcjLng = p1.lng + (p2.lng - p1.lng) * fraction;

                double[] wgs84 = CampusRoadGraph.gcj02ToWgs84(gcjLat, gcjLng);

                double finalLat = wgs84[0] + (random.nextGaussian() * 0.0000045);
                double finalLng = wgs84[1] + (random.nextGaussian() * 0.0000045);

                double wave = Math.sin(phase + stepLocations.size() * 0.12) * 0.35;
                double jitter = (random.nextDouble() - 0.5) * 0.25;
                float currentSpeed = (float) Math.max(1.8, speedMps + wave + jitter);

                Location loc = new Location(LocationManager.GPS_PROVIDER);
                loc.setLatitude(finalLat);
                loc.setLongitude(finalLng);
                loc.setAltitude(62.0 + Math.sin(stepLocations.size() * 0.05) * 0.8);
                loc.setSpeed(currentSpeed);
                loc.setBearing(bearing);
                loc.setAccuracy(2.2f + random.nextFloat() * 1.2f);

                Bundle extras = new Bundle();
                extras.putInt("satellites", 12 + random.nextInt(5));
                loc.setExtras(extras);

                stepLocations.add(loc);
            }
        }
    }

    private final Runnable mockLoop = new Runnable() {
        @Override
        public void run() {
            if (stepLocations.isEmpty()) return;

            if (currentIndex >= stepLocations.size()) {
                if (loopMode) {
                    currentIndex = 0;
                } else {
                    stopSelf();
                    return;
                }
            }

            Location baseLoc = stepLocations.get(currentIndex++);
            long now = System.currentTimeMillis();
            long elapsedNanos = SystemClock.elapsedRealtimeNanos();

            injectToProvider(LocationManager.GPS_PROVIDER, baseLoc, now, elapsedNanos);
            injectToProvider(LocationManager.NETWORK_PROVIDER, baseLoc, now, elapsedNanos);

            handler.postDelayed(this, 800);
        }
    };

    private void injectToProvider(String provider, Location src, long time, long elapsedNanos) {
        try {
            Location loc = new Location(provider);
            loc.setLatitude(src.getLatitude());
            loc.setLongitude(src.getLongitude());
            loc.setAltitude(src.getAltitude());
            loc.setSpeed(src.getSpeed());
            loc.setBearing(src.getBearing());
            loc.setAccuracy(src.getAccuracy());
            loc.setTime(time);
            loc.setElapsedRealtimeNanos(elapsedNanos);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc.setSpeedAccuracyMetersPerSecond(0.2f);
                loc.setBearingAccuracyDegrees(5.0f);
                loc.setVerticalAccuracyMeters(1.5f);
            }
            if (src.getExtras() != null) {
                loc.setExtras(new Bundle(src.getExtras()));
            }
            locationManager.setTestProviderLocation(provider, loc);
        } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Mock GPS Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(mockLoop);
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                locationManager.setTestProviderEnabled(provider, false);
                locationManager.removeTestProvider(provider);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

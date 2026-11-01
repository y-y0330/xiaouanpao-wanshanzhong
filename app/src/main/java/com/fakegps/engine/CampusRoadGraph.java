package com.fakegps.engine;

import java.util.*;

/**
 * 黄河科技学院南校区高精度路网模型与避障寻路引擎 v3.0
 * 支持：GCJ-02 转 WGS-84 反偏校准、2-Opt TSP 路径优化、闭环干道寻路
 */
public class CampusRoadGraph {
    public static class Node {
        public String name;
        public double lat; // 内部统一存储高德/腾讯 GCJ-02 坐标，注入前自动转 WGS-84
        public double lng;
        public List<Edge> edges = new ArrayList<>();

        public Node(String name, double lat, double lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    public static class Edge {
        public Node target;
        public double weight;

        public Edge(Node target, double weight) {
            this.target = target;
            this.weight = weight;
        }
    }

    private final Map<String, Node> nodeMap = new LinkedHashMap<>();

    public void addNode(Node node) {
        nodeMap.put(node.name, node);
    }

    public void addRoad(String name1, String name2) {
        Node n1 = nodeMap.get(name1);
        Node n2 = nodeMap.get(name2);
        if (n1 != null && n2 != null) {
            double dist = distance(n1.lat, n1.lng, n2.lat, n2.lng);
            n1.edges.add(new Edge(n2, dist));
            n2.edges.add(new Edge(n1, dist));
        }
    }

    public Node findNearestNode(double lat, double lng) {
        Node nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Node n : nodeMap.values()) {
            double d = distance(lat, lng, n.lat, n.lng);
            if (d < minDist) {
                minDist = d;
                nearest = n;
            }
        }
        return nearest;
    }

    // A* 最短路避障搜索
    public List<Node> findPath(Node start, Node goal) {
        if (start == null || goal == null) return Collections.emptyList();
        if (start.equals(goal)) return new ArrayList<>(Collections.singletonList(start));

        Map<Node, Double> gScore = new HashMap<>();
        Map<Node, Double> fScore = new HashMap<>();
        Map<Node, Node> cameFrom = new HashMap<>();
        PriorityQueue<Node> openSet = new PriorityQueue<>(
                Comparator.comparingDouble(n -> fScore.getOrDefault(n, Double.MAX_VALUE))
        );

        gScore.put(start, 0.0);
        fScore.put(start, distance(start.lat, start.lng, goal.lat, goal.lng));
        openSet.add(start);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.equals(goal)) {
                List<Node> path = new ArrayList<>();
                Node temp = current;
                while (temp != null) {
                    path.add(0, temp);
                    temp = cameFrom.get(temp);
                }
                return path;
            }

            for (Edge edge : current.edges) {
                double tentativeG = gScore.getOrDefault(current, Double.MAX_VALUE) + edge.weight;
                if (tentativeG < gScore.getOrDefault(edge.target, Double.MAX_VALUE)) {
                    cameFrom.put(edge.target, current);
                    gScore.put(edge.target, tentativeG);
                    double f = tentativeG + distance(edge.target.lat, edge.target.lng, goal.lat, goal.lng);
                    fScore.put(edge.target, f);
                    if (!openSet.contains(edge.target)) {
                        openSet.add(edge.target);
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    // 计算两路网节点间的实际道路距离（而非直线穿墙距离）
    private double roadDistance(Node a, Node b) {
        List<Node> path = findPath(a, b);
        if (path.size() < 2) return Double.MAX_VALUE / 10;
        double sum = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            sum += distance(path.get(i).lat, path.get(i).lng, path.get(i + 1).lat, path.get(i + 1).lng);
        }
        return sum;
    }

    // 贪心构造 + 2-Opt 局部优化：规划不走冤枉路的全点位避障路线
    public List<Node> planMultiTargetRoute(double startLat, double startLng, List<double[]> targets) {
        Node startNode = findNearestNode(startLat, startLng);
        List<Node> targetNodes = new ArrayList<>();
        targetNodes.add(startNode);

        for (double[] t : targets) {
            Node n = findNearestNode(t[0], t[1]);
            if (n != null && !targetNodes.contains(n)) {
                targetNodes.add(n);
            }
        }

        // 若只有1个点，自动绕行东田径场一圈以满足里程要求
        if (targetNodes.size() == 1) {
            targetNodes.add(nodeMap.get("东田径场_北"));
            targetNodes.add(nodeMap.get("东田径场_东"));
            targetNodes.add(nodeMap.get("东田径场_南"));
            targetNodes.add(nodeMap.get("东田径场_西"));
        }

        // 2-Opt 优化访问顺序（固定起点 index=0）
        int n = targetNodes.size();
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 1; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    double d1 = roadDistance(targetNodes.get(i - 1), targetNodes.get(i))
                            + (j + 1 < n ? roadDistance(targetNodes.get(j), targetNodes.get(j + 1)) : 0);
                    double d2 = roadDistance(targetNodes.get(i - 1), targetNodes.get(j))
                            + (j + 1 < n ? roadDistance(targetNodes.get(i), targetNodes.get(j + 1)) : 0);
                    if (d2 + 1.0 < d1) {
                        Collections.reverse(targetNodes.subList(i, j + 1));
                        improved = true;
                    }
                }
            }
        }

        // 拼接完整路网节点序列
        List<Node> fullPath = new ArrayList<>();
        for (int i = 0; i < targetNodes.size() - 1; i++) {
            List<Node> seg = findPath(targetNodes.get(i), targetNodes.get(i + 1));
            if (!seg.isEmpty()) {
                if (!fullPath.isEmpty()) seg.remove(0);
                fullPath.addAll(seg);
            }
        }
        return fullPath;
    }

    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6378137.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // 方位角计算（使小蓝点箭头始终朝向跑步前进方向）
    public static float calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        double brng = Math.toDegrees(Math.atan2(y, x));
        return (float) ((brng + 360) % 360);
    }

    // 核心：GCJ-02 (微信/腾讯地图火星坐标) 转换为 WGS-84 (Android 底层 GPS 原始坐标)
    public static double[] gcj02ToWgs84(double ggLat, double ggLon) {
        double a = 6378245.0;
        double ee = 0.00669342162296594323;
        double dLat = transformLat(ggLon - 105.0, ggLat - 35.0);
        double dLon = transformLon(ggLon - 105.0, ggLat - 35.0);
        double radLat = ggLat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - ee * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Math.PI);
        dLon = (dLon * 180.0) / (a / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{ggLat - dLat, ggLon - dLon};
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }

    public static CampusRoadGraph buildHuangKeDaSouth() {
        CampusRoadGraph g = new CampusRoadGraph();
        // 横向：厚德路（北）
        g.addNode(new Node("厚德路_开拓路口", 34.6958, 113.6825));
        g.addNode(new Node("厚德路_西田径场北", 34.6958, 113.6845));
        g.addNode(new Node("厚德路_小北门", 34.6962, 113.6862));
        g.addNode(new Node("厚德路_创新路口", 34.6958, 113.6855));
        g.addNode(new Node("厚德路_春晖路口", 34.6958, 113.6895));

        // 横向：博学路（中）
        g.addNode(new Node("博学路_西门", 34.6945, 113.6812));
        g.addNode(new Node("博学路_开拓路口", 34.6938, 113.6825));
        g.addNode(new Node("博学路_仲景园南", 34.6938, 113.6840));
        g.addNode(new Node("博学路_创新路口", 34.6938, 113.6855));
        g.addNode(new Node("博学路_文化广场", 34.6938, 113.6868));
        g.addNode(new Node("博学路_春晖路口", 34.6938, 113.6895));
        g.addNode(new Node("博学路_东门", 34.6938, 113.6912));

        // 东田径场环形跑道（核心运动区）
        g.addNode(new Node("东田径场_北", 34.6932, 113.6878));
        g.addNode(new Node("东田径场_东", 34.6924, 113.6884));
        g.addNode(new Node("东田径场_南", 34.6916, 113.6878));
        g.addNode(new Node("东田径场_西", 34.6924, 113.6872));

        // 横向：至善路（中南）
        g.addNode(new Node("至善路_开拓路口", 34.6905, 113.6825));
        g.addNode(new Node("至善路_智创楼北", 34.6905, 113.6842));
        g.addNode(new Node("至善路_创新路口", 34.6905, 113.6855));
        g.addNode(new Node("至善路_春晖路口", 34.6905, 113.6895));

        // 南部与图书馆片区
        g.addNode(new Node("图书馆西侧", 34.6925, 113.6895));
        g.addNode(new Node("综合体育馆西", 34.6885, 113.6898));
        g.addNode(new Node("南广场北", 34.6885, 113.6855));
        g.addNode(new Node("艺术中心东", 34.6885, 113.6825));
        g.addNode(new Node("南一门", 34.6865, 113.6898));
        g.addNode(new Node("南二门", 34.6865, 113.6855));
        g.addNode(new Node("南三门", 34.6865, 113.6810));

        // 连通干道（严格沿校园道路，避开建筑与围栏）
        g.addRoad("厚德路_开拓路口", "厚德路_西田径场北");
        g.addRoad("厚德路_西田径场北", "厚德路_创新路口");
        g.addRoad("厚德路_创新路口", "厚德路_小北门");
        g.addRoad("厚德路_创新路口", "厚德路_春晖路口");

        g.addRoad("博学路_西门", "博学路_开拓路口");
        g.addRoad("博学路_开拓路口", "博学路_仲景园南");
        g.addRoad("博学路_仲景园南", "博学路_创新路口");
        g.addRoad("博学路_创新路口", "博学路_文化广场");
        g.addRoad("博学路_文化广场", "博学路_春晖路口");
        g.addRoad("博学路_春晖路口", "博学路_东门");

        // 东田径场闭环与出入口
        g.addRoad("博学路_文化广场", "东田径场_北");
        g.addRoad("东田径场_北", "东田径场_东");
        g.addRoad("东田径场_东", "东田径场_南");
        g.addRoad("东田径场_南", "东田径场_西");
        g.addRoad("东田径场_西", "东田径场_北");
        g.addRoad("东田径场_东", "图书馆西侧");
        g.addRoad("东田径场_南", "至善路_创新路口");

        // 至善路横向
        g.addRoad("至善路_开拓路口", "至善路_智创楼北");
        g.addRoad("至善路_智创楼北", "至善路_创新路口");
        g.addRoad("至善路_创新路口", "至善路_春晖路口");

        // 纵向三大干道：开拓路、创新路、春晖路
        g.addRoad("厚德路_开拓路口", "博学路_开拓路口");
        g.addRoad("博学路_开拓路口", "至善路_开拓路口");
        g.addRoad("至善路_开拓路口", "艺术中心东");
        g.addRoad("艺术中心东", "南三门");

        g.addRoad("厚德路_创新路口", "博学路_创新路口");
        g.addRoad("博学路_创新路口", "至善路_创新路口");
        g.addRoad("至善路_创新路口", "南广场北");
        g.addRoad("南广场北", "南二门");

        g.addRoad("厚德路_春晖路口", "博学路_春晖路口");
        g.addRoad("博学路_春晖路口", "图书馆西侧");
        g.addRoad("图书馆西侧", "至善路_春晖路口");
        g.addRoad("至善路_春晖路口", "综合体育馆西");
        g.addRoad("综合体育馆西", "南一门");

        return g;
    }
}

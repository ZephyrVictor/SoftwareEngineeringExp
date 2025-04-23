package com.example.wordgraph;

import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.swing.mxGraphComponent;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.traverse.RandomWalkIterator;
//import org.jgrapht.alg.scoring.PersonalizedPageRank;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.io.BufferedWriter;
import java.io.FileWriter;

public class WordGraphApp extends JFrame {
    Graph<String, DefaultWeightedEdge> graph;
    private JGraphXAdapter<String, DefaultWeightedEdge> jgxAdapter;
    private JPanel graphPanel;
    private JTextArea logArea;

    // 在类成员变量区添加：
    private volatile boolean stopRandomWalk = false;
    private List<String> wordsList = new ArrayList<>();


    /**
     * 手写 PageRank（不做 dangling 重分配，跳转只在有出度节点之间）
     * @param d         阻尼因子
     * @param maxIter   最大迭代次数
     * @return          每个顶点的 PR 值
     */
    private Map<String, Double> computePageRankManual(double d, int maxIter) {
        // 1. 收集所有顶点，并找出非 dangling（out>0）顶点集
        Set<String> V = graph.vertexSet();
        List<String> nonSinks = V.stream()
                .filter(u -> !graph.outgoingEdgesOf(u).isEmpty())
                .collect(Collectors.toList());
        int Nprime = nonSinks.size();

        // 2. 初始化：均匀分布 over 全集 V
        Map<String, Double> pr = new HashMap<>();
        double init = 1.0 / V.size();
        for (String u : V) {
            pr.put(u, init);
        }

        // 3. 迭代
        for (int iter = 0; iter < maxIter; iter++) {
            Map<String, Double> next = new HashMap<>();
            // 对每个 u 计算新 PR
            for (String u : V) {
                // teleport（跳转）项：只分给非 dangling 顶点
                double teleport = (1 - d) / Nprime;

                // 收敛累积项 ∑_{v→u} PR(v)/L(v)
                double sum = 0;
                for (DefaultWeightedEdge e : graph.incomingEdgesOf(u)) {
                    String v = graph.getEdgeSource(e);
                    int out = graph.outgoingEdgesOf(v).size();
                    if (out > 0) {
                        sum += pr.get(v) / out;
                    }
                }

                next.put(u, teleport + d * sum);
            }
            pr = next;
        }
        return pr;
    }

    /**
     * 用 TF 统计作为初始 PR 分布
     * @param d       阻尼因子
     * @param maxIter 迭代次数
     */
    private Map<String, Double> computePageRankWithTFInitial(double d, int maxIter) {
        Set<String> V = graph.vertexSet();
        int N = V.size();

        // 1) 计算每个词的 TF（词频 / 文档总词数）
        Map<String, Double> pr = new HashMap<>(N);
        double total = wordsList.size();
        // 统计词频
        Map<String, Long> freq = wordsList.stream()
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        // 初始化 PR
        for (String u : V) {
            double f = freq.getOrDefault(u, 0L);
            pr.put(u, f / total);
        }

        // 2) 迭代 PageRank（同 computePageRankManual，只是初始 pr 不再均匀）
        for (int iter = 0; iter < maxIter; iter++) {
            Map<String, Double> next = new HashMap<>(N);
            for (String u : V) {
                double sum = 0;
                for (DefaultWeightedEdge e : graph.incomingEdgesOf(u)) {
                    String v = graph.getEdgeSource(e);
                    int outDeg = graph.outgoingEdgesOf(v).size();
                    if (outDeg > 0) {
                        sum += pr.get(v) / outDeg;
                    }
                }
                double teleport = (1 - d) / N;
                next.put(u, teleport + d * sum);
            }
            pr = next;
        }
        return pr;
    }




    public WordGraphApp() {
        super("Zephyr#3 WordGraph");
        graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 600);
        JSplitPane split = new JSplitPane();
        split.setDividerLocation(600);
        getContentPane().add(split);

        // 左侧：图展示面板
        graphPanel = new JPanel(new BorderLayout());
        split.setLeftComponent(graphPanel);

        // 右侧：日志与按钮
        JPanel right = new JPanel(new BorderLayout(5,5));
        split.setRightComponent(right);

        logArea = new JTextArea();
        logArea.setEditable(false);
        right.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(0,1,5,5));
        JButton btnLoad   = new JButton("加载文本");
        JButton btnShow   = new JButton("展示有向图");
        JButton btnBridge = new JButton("查询桥接词");
        JButton btnGen    = new JButton("根据桥接生成新文");
        JButton btnPath   = new JButton("计算最短路径");
//        JButton btnPR     = new JButton("计算PageRank");
        JButton btnWalk   = new JButton("随机游走");
        JButton btnPR     = new JButton("计算PageRank");
        JButton btnPRTF   = new JButton("TF 初始自定义PR");  // ← 新增按钮
        buttons.add(btnLoad);
        buttons.add(btnShow);
        buttons.add(btnBridge);
        buttons.add(btnGen);
        buttons.add(btnPath);
        buttons.add(btnPR);
        buttons.add(btnWalk);
        right.add(buttons, BorderLayout.NORTH);
//        buttons.add(btnPR);
        buttons.add(btnPRTF);

        // 绑定事件
        btnLoad.addActionListener(e -> loadFile());
        btnShow.addActionListener(e -> visualizeGraph());
        btnBridge.addActionListener(e -> queryBridge());
        btnGen.addActionListener(e -> generateNewText());
        btnPath.addActionListener(e -> shortestPath());
        btnPR.addActionListener(e -> computePageRank());
        btnWalk.addActionListener(e -> randomWalk());
        btnPRTF.addActionListener(e -> customPageRank());
        setVisible(true);
    }

    /** 1. 读文件，构建有向加权图 **/
    private void loadFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("文本文件", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();

        graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        List<String> words = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file.toPath())) {
                // 非字母当作空格，统一小写
                line = line.toLowerCase().replaceAll("[^a-z]+", " ");
                for (String w : line.split("\\s+"))
                    if (!w.isEmpty()) words.add(w);
            }
            wordsList = words;
            // 添加顶点
            words.forEach(graph::addVertex);
            // 添加/累计边权
            for (int i = 0; i < words.size() - 1; i++) {
                String a = words.get(i), b = words.get(i + 1);
                DefaultWeightedEdge e = graph.getEdge(a, b);
                if (e == null) {
                    e = graph.addEdge(a, b);
                    graph.setEdgeWeight(e, 1.0);
                } else {
                    graph.setEdgeWeight(e, graph.getEdgeWeight(e) + 1.0);
                }
            }
            log("已加载： " + file.getName() +
                    "   顶点数=" + graph.vertexSet().size() +
                    "   边数=" + graph.edgeSet().size());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** 2. 可视化（Swing + JGraphXAdapter + CircleLayout） **/
    private void visualizeGraph() {
        if (graph.vertexSet().isEmpty()) {
            log("请先加载文本生成图。");
            return;
        }

        jgxAdapter = new JGraphXAdapter<>(graph);

        // 1. 设置边标签（权重）
        for (DefaultWeightedEdge e : graph.edgeSet()) {
            String label = String.format("%.1f", graph.getEdgeWeight(e));
            jgxAdapter.getEdgeToCellMap().get(e).setValue(label);
        }

        // 2. 使用有机布局（适合大图）
        com.mxgraph.layout.mxIGraphLayout layout = new com.mxgraph.layout.mxFastOrganicLayout(jgxAdapter);
        layout.execute(jgxAdapter.getDefaultParent());

        // 3. 在图面板中展示
        graphPanel.removeAll();
        mxGraphComponent graphComponent = new mxGraphComponent(jgxAdapter);
        graphPanel.add(graphComponent, BorderLayout.CENTER);
        graphPanel.revalidate();
        graphPanel.repaint();

        log("有向图已展示（自动布局 + 权重标签）。");

        // 4. 生成 PNG 并写入当前目录
        try {
            BufferedImage image = com.mxgraph.util.mxCellRenderer.createBufferedImage(
                    jgxAdapter, null, 1, Color.WHITE, true, null);
            File file = new File("graph_output.png");
            javax.imageio.ImageIO.write(image, "PNG", file);
            log("图已保存为：" + file.getAbsolutePath());
        } catch (Exception e) {
            log("保存图像失败：" + e.getMessage());
        }
    }



    /** 3. 查询桥接词 **/
    private void queryBridge() {
        String w1 = JOptionPane.showInputDialog(this, "输入 word1：");
        String w2 = JOptionPane.showInputDialog(this, "输入 word2：");
        if (w1 == null || w2 == null) return;
        w1 = w1.toLowerCase(); w2 = w2.toLowerCase();

        if (!graph.containsVertex(w1) && !graph.containsVertex(w2)) {
            log("No \"" + w1 + "\" and \"" + w2 + "\" in the graph!");
            return;
        }
        if (!graph.containsVertex(w1)) {
            log("No \"" + w1 + "\" in the graph!");
            return;
        }
        if (!graph.containsVertex(w2)) {
            log("No \"" + w2 + "\" in the graph!");
            return;
        }

        String finalW = w1;
        String finalW1 = w2;
        Set<String> bridges = graph.vertexSet().stream()
                .filter(mid ->
                        graph.containsEdge(finalW, mid) &&
                                graph.containsEdge(mid, finalW1)
                ).collect(Collectors.toSet());
        if (bridges.isEmpty()) {
            log("No bridge words from \"" + w1 + "\" to \"" + w2 + "\"");
        } else {
//            log("Bridge words: " + String.join(", ", bridges));
            log("No bridge words from \"" + w1 + "\" to \"" + w2 + " \"" +"is :\"" + String.join(", ", bridges) + "\"" );
        }

    }

    /** 4. 根据桥接词生成新文本 **/
    private void generateNewText() {
        String input = JOptionPane.showInputDialog(this, "输入一段英文文本：");
        if (input == null) return;
        String[] arr = input.toLowerCase().replaceAll("[^a-z]+"," ").trim().split("\\s+");
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) {
                int finalI = i;
                List<String> bs = graph.vertexSet().stream()
                        .filter(mid ->
                                graph.containsEdge(arr[finalI], mid) &&
                                        graph.containsEdge(mid, arr[finalI +1])
                        ).collect(Collectors.toList());
                if (!bs.isEmpty()) {
                    sb.append(" ").append(bs.get(rnd.nextInt(bs.size())));
                }
                sb.append(" ");
            }
        }
        log("生成新文本： " + sb.toString());
    }

    /** 5. 最短路径（Dijkstra） **/
    private void shortestPath() {
        String from = JOptionPane.showInputDialog(this, "请输入起点单词（或仅输入一个单词）：");
        if (from == null || from.isEmpty()) return;

        String to = JOptionPane.showInputDialog(this, "请输入终点单词（可留空以展示到所有单词的路径）：");
        if (to != null && to.isBlank()) to = null;

        from = from.toLowerCase();
        if (to != null) to = to.toLowerCase();

        if (!graph.containsVertex(from)) {
            log("No \"" + from + "\" in the graph!");
            return;
        }

        DijkstraShortestPath<String, DefaultWeightedEdge> dsp = new DijkstraShortestPath<>(graph);

        if (to == null) {
            StringBuilder sb = new StringBuilder("从 [" + from + "] 到其他所有点的最短路径：\n\n");
            for (String target : graph.vertexSet()) {
                if (target.equals(from)) continue;
                GraphPath<String, DefaultWeightedEdge> path = dsp.getPath(from, target);
                if (path == null) {
                    sb.append("到 [").append(target).append("] 不可达。\n");
                } else {
                    sb.append("到 [").append(target).append("] ：")
                            .append(String.join(" -> ", path.getVertexList()))
                            .append("   （长度=").append(path.getWeight()).append("）\n");
                }
            }
            log(sb.toString());
            return;
        }

        if (!graph.containsVertex(to)) {
            log("No \"" + to + "\" in the graph!");
            return;
        }

        GraphPath<String, DefaultWeightedEdge> path = dsp.getPath(from, to);
        if (path == null) {
            log("从 " + from + " 到 " + to + " 不可达。");
        } else {
            log("最短路径（权重=" + path.getWeight() + "）： " +
                    String.join(" -> ", path.getVertexList()));
        }
    }


    /** 6. PageRank **/
    private void computePageRank() {
        String w = JOptionPane.showInputDialog(this, "计算哪个单词的 PR？");
        if (w == null) return;
        w = w.toLowerCase();
        if (!graph.containsVertex(w)) {
            log("图中不存在单词：" + w);
            return;
        }

//        for (int i = 5 ; i < 50 ; i+=5) {
//            Map<String, Double> prMap = computePageRankManual(0.85, i);
//            double prNew = prMap.get(w);
//            log("手写 PR(" + w + ") = " + String.format("%.4f", prNew));
//        }
        double d = 0.85;
        // 调用手写算法，100 次迭代
        Map<String, Double> prMap = computePageRankManual(d, 100);
        double prNew = prMap.get(w);
        log("d值:(" + d + ") = " + String.format("%.4f", prNew));
    }




    /** 7. 随机游走 **/
    private void randomWalk() {
        if (graph.vertexSet().isEmpty()) {
            log("图为空，无法随机游走。");
            return;
        }

        stopRandomWalk = false;  // 每次开始前重置

        // --- 1. 创建对话框和组件 ---
        JDialog dialog = new JDialog(this, "随机游走中…", true);
        JTextArea ta = new JTextArea(20, 40);
        ta.setEditable(false);
        JButton btnStop = new JButton("停止");
        btnStop.addActionListener(e -> stopRandomWalk = true);

        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(ta), BorderLayout.CENTER);
        dialog.add(btnStop, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);

        // --- 2. 后台线程执行遍历 ---
        new Thread(() -> {
            List<String> visitedNodes = new ArrayList<>();
            Set<DefaultWeightedEdge> usedEdges = new HashSet<>();
            Random rnd = new Random();

            // 随机起点
            List<String> vs = new ArrayList<>(graph.vertexSet());
            String prev = vs.get(rnd.nextInt(vs.size()));
            visitedNodes.add(prev);
            String finalPrev = prev;
            SwingUtilities.invokeLater(() -> ta.append("起点: " + finalPrev + "\n"));

            while (!stopRandomWalk) {
                Set<DefaultWeightedEdge> outs = graph.outgoingEdgesOf(prev);
                if (outs.isEmpty()) {
                    String finalPrev1 = prev;
                    SwingUtilities.invokeLater(() -> ta.append(
                            "节点 [" + finalPrev1 + "] 无出边，结束。\n"));
                    break;
                }
                // 随机选一条出边
                DefaultWeightedEdge edge = new ArrayList<>(outs)
                        .get(rnd.nextInt(outs.size()));
                String curr = graph.getEdgeTarget(edge);

                // 遇到重复边就停
                if (usedEdges.contains(edge)) {
                    String finalPrev2 = prev;
                    SwingUtilities.invokeLater(() -> ta.append(
                            "遇到重复边 [" + finalPrev2 + " -> " + curr + "]，结束。\n"));
                    break;
                }

                usedEdges.add(edge);
                visitedNodes.add(curr);
                String finalPrev3 = prev;
                SwingUtilities.invokeLater(() -> ta.append(
                        finalPrev3 + " -> " + curr + "\n"));
                prev = curr;

                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }

            // --- 3. 拼成一行输出 & 写文件 ---
            String pathLine = String.join(" ", visitedNodes);
            SwingUtilities.invokeLater(() -> {
                ta.append("\n完整路径: " + pathLine + "\n");
            });

            // 保存
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("保存随机游走结果");
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File out = chooser.getSelectedFile();
                try (var bw = new BufferedWriter(new FileWriter(out))) {
                    // 只写一行完整路径
                    bw.write(pathLine);
                    bw.newLine();
                    SwingUtilities.invokeLater(() -> ta.append(
                            "已写入文件：" + out.getAbsolutePath() + "\n"));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> ta.append(
                            "写文件失败: " + ex.getMessage() + "\n"));
                }
            }

            SwingUtilities.invokeLater(() -> {
                btnStop.setText("关闭");
                for (ActionListener al : btnStop.getActionListeners()) {
                    btnStop.removeActionListener(al);
                }
                btnStop.addActionListener(e -> dialog.dispose());
            });


        }).start();

        dialog.setVisible(true);
    }

    private void customPageRank() {
        // 1) 让用户输入阻尼因子 d
        String ds = JOptionPane.showInputDialog(this, "请输入阻尼因子 d（0~1），例如 0.85：");
        if (ds == null) return;
        double d;
        try {
            d = Double.parseDouble(ds);
            if (d < 0 || d > 1) throw new NumberFormatException();
        } catch (Exception ex) {
            log("无效的 d 值：" + ds);
            return;
        }

        // 2) 让用户输入迭代次数
        String is = JOptionPane.showInputDialog(this, "请输入迭代次数（例如 20）：");
        if (is == null) return;
        int iter;
        try {
            iter = Integer.parseInt(is);
            if (iter <= 0) throw new NumberFormatException();
        } catch (Exception ex) {
            log("无效的迭代次数：" + is);
            return;
        }

        // 3) 计算带 TF 初始分布的 PR
        Map<String, Double> prMap = computePageRankWithTFInitial(d, iter);

        // 4) 展示“Top 10” PR 排名
        log(String.format("== TF 初始 PR，d=%.2f, iter=%d ==", d, iter));
        prMap.entrySet().stream()
                .sorted(Map.Entry.<String,Double>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .forEach(ent -> {
                    log(String.format("PR(%s)=%.6f", ent.getKey(), ent.getValue()));
                });

        // 5) 可让用户查询某个单词
        String w = JOptionPane.showInputDialog(this, "查询某一单词的 PR 值（可留空跳过）：");
        if (w != null && !w.isBlank()) {
            w = w.toLowerCase();
            if (prMap.containsKey(w)) {
                log(String.format("PR(%s)=%.6f", w, prMap.get(w)));
            } else {
                log("单词不在图中：" + w);
            }
        }
    }






    private void log(String s) {
        logArea.append(s + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(WordGraphApp::new);
    }

    // 为了编写测试单元 需要实现查询桥接词
    /** 查询桥接词：public 用于测试调用 **/
    public String queryBridgeWords(String w1, String w2) {
        w1 = w1.toLowerCase();
        w2 = w2.toLowerCase();

        if (!graph.containsVertex(w1) || !graph.containsVertex(w2)) {
            if (!graph.containsVertex(w1) && !graph.containsVertex(w2))
                return "No \"" + w1 + "\" or \"" + w2 + "\" in the graph!";
            if (!graph.containsVertex(w1))
                return "No \"" + w1 + "\" in the graph!";
            return "No \"" + w2 + "\" in the graph!";
        }

        Set<String> bridges = new HashSet<>();
        for (String mid : graph.vertexSet()) {
            if (graph.containsEdge(w1, mid) && graph.containsEdge(mid, w2)) {
                bridges.add(mid);
            }
        }

        if (bridges.isEmpty()) {
            return "No bridge words from \"" + w1 + "\" to \"" + w2 + "\"!";
        } else {
            String list = bridges.stream().sorted().map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(", "));
            return "The bridge words from \"" + w1 + "\" to \"" + w2 + "\" are: " + list + ".";
        }
    }
}

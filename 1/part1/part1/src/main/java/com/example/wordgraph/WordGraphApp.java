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

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class WordGraphApp extends JFrame {
    private Graph<String, DefaultWeightedEdge> graph;
    private JGraphXAdapter<String, DefaultWeightedEdge> jgxAdapter;
    private JPanel graphPanel;
    private JTextArea logArea;

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
        JButton btnPR     = new JButton("计算PageRank");
        JButton btnWalk   = new JButton("随机游走");
        buttons.add(btnLoad);
        buttons.add(btnShow);
        buttons.add(btnBridge);
        buttons.add(btnGen);
        buttons.add(btnPath);
        buttons.add(btnPR);
        buttons.add(btnWalk);
        right.add(buttons, BorderLayout.NORTH);

        // 绑定事件
        btnLoad.addActionListener(e -> loadFile());
        btnShow.addActionListener(e -> visualizeGraph());
        btnBridge.addActionListener(e -> queryBridge());
        btnGen.addActionListener(e -> generateNewText());
        btnPath.addActionListener(e -> shortestPath());
        btnPR.addActionListener(e -> computePageRank());
        btnWalk.addActionListener(e -> randomWalk());

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
            // 添加顶点
            words.forEach(graph::addVertex);
            // 添加/累计边权
            for (int i = 0; i < words.size() - 1; i++) {
                String a = words.get(i), b = words.get(i + 1);
                DefaultWeightedEdge e = graph.getEdge(a, b);
                if (e == null) {
                    e = graph.addEdge(a, b);
                    graph.setEdgeWeight(e, 1);
                } else {
                    graph.setEdgeWeight(e, graph.getEdgeWeight(e) + 1);
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
        mxCircleLayout layout = new mxCircleLayout(jgxAdapter);
        layout.execute(jgxAdapter.getDefaultParent());

        graphPanel.removeAll();
        graphPanel.add(new mxGraphComponent(jgxAdapter), BorderLayout.CENTER);
        graphPanel.revalidate();
        log("有向图已展示。");
    }

    /** 3. 查询桥接词 **/
    private void queryBridge() {
        String w1 = JOptionPane.showInputDialog(this, "输入 word1：");
        String w2 = JOptionPane.showInputDialog(this, "输入 word2：");
        if (w1 == null || w2 == null) return;
        w1 = w1.toLowerCase(); w2 = w2.toLowerCase();

        if (!graph.containsVertex(w1) || !graph.containsVertex(w2)) {
            log("图中缺少 \"" + w1 + "\" 或 \"" + w2 + "\"");
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
            log("Bridge words: " + String.join(", ", bridges));
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
        String from = JOptionPane.showInputDialog(this, "起点 word：");
        String to   = JOptionPane.showInputDialog(this, "终点 word：");
        if (from == null || to == null) return;
        from = from.toLowerCase(); to = to.toLowerCase();

        if (!graph.containsVertex(from) || !graph.containsVertex(to)) {
            log("图中缺少起点或终点。"); return;
        }
        var dsp = new DijkstraShortestPath<>(graph);
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
            log("图中不存在单词：" + w); return;
        }
        PageRank<String, DefaultWeightedEdge> pr = new PageRank<>(graph, 0.85);
        log("PR(" + w + ") = " + String.format("%.6f", pr.getVertexScore(w)));
    }

    /** 7. 随机游走 **/
    private void randomWalk() {
        if (graph.vertexSet().isEmpty()) {
            log("图为空，无法随机游走。");
            return;
        }
        List<String> vs = new ArrayList<>(graph.vertexSet());
        String start = vs.get(new Random().nextInt(vs.size()));
        RandomWalkIterator<String, DefaultWeightedEdge> walk = new RandomWalkIterator<>(graph, start);

        StringBuilder sb = new StringBuilder("从 [" + start + "] 开始随机游走：\n");
        Set<DefaultWeightedEdge> used = new HashSet<>();

        String prev = start;
        while (walk.hasNext()) {
            String curr = walk.next();
            DefaultWeightedEdge e = graph.getEdge(prev, curr);
            if (e == null) break;

            if (used.contains(e)) {
                sb.append("遇到重复边，停止。\n");
                break;
            }

            used.add(e);
            sb.append(prev).append(" -> ").append(curr).append("\n");
            prev = curr;
        }

        log(sb.toString());
    }


    private void log(String s) {
        logArea.append(s + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(WordGraphApp::new);
    }
}

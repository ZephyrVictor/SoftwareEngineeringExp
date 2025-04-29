package com.example.wordgraph;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordGraphAppTest {

    static WordGraphApp app;

    @BeforeAll
    static void setup() throws Exception {
        app = new WordGraphApp();

        // 构造图，模拟从文件中读入
        List<String> words = new ArrayList<>();
        String text = "To explore strange new worlds, To seek out new life and new civilizations";
        text = text.toLowerCase().replaceAll("[^a-z]+", " ");
        for (String w : text.split("\\s+")) {
            if (!w.isEmpty()) words.add(w);
        }

        // 构建图
        app.graph = new org.jgrapht.graph.DefaultDirectedWeightedGraph<>(org.jgrapht.graph.DefaultWeightedEdge.class);
        for (String w : words) app.graph.addVertex(w);
        for (int i = 0; i < words.size() - 1; i++) {
            String a = words.get(i), b = words.get(i + 1);
            var e = app.graph.getEdge(a, b);
            if (e == null) {
                e = app.graph.addEdge(a, b);
                app.graph.setEdgeWeight(e, 1);
            } else {
                app.graph.setEdgeWeight(e, app.graph.getEdgeWeight(e) + 1);
            }
        }
    }

    @Test
    void testBridgeWords_basicCases() {
        assertEquals("No bridge words from \"seek\" to \"to\"!", app.queryBridgeWords("seek", "to"));
        assertEquals("No bridge words from \"to\" to \"explore\"!", app.queryBridgeWords("to", "explore"));
        assertEquals("The bridge words from \"explore\" to \"new\" are: \"strange\".", app.queryBridgeWords("explore", "new"));
        assertEquals("The bridge words from \"new\" to \"and\" are: \"life\".", app.queryBridgeWords("new", "and"));
    }

    @Test
    void testBridgeWords_missingWords() {
        assertEquals("No \"exciting\" in the graph!", app.queryBridgeWords("and", "exciting"));
        assertEquals("No \"exciting\" or \"synergies\" in the graph!", app.queryBridgeWords("exciting", "synergies"));
    }
}

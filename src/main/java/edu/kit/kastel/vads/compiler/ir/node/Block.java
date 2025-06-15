package edu.kit.kastel.vads.compiler.ir.node;

import edu.kit.kastel.vads.compiler.ir.IrGraph;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;

public final class Block extends Node {
    private final String label;
    private final List<Node> nodes = new ArrayList<>();
    private final Map<Phi, Integer> phis = new LinkedHashMap<>();

    public Block(IrGraph graph, String label) {
        super(graph);
        this.label = label;
    }

    public String label() {
        return label;
    }

    public void addNode(Node node) {
        nodes.add(node);
    }

    public List<Node> nodes() {
        return new ArrayList<>(nodes);
    }

    public void addPhi(Phi phi, int i) {
        phis.put(phi, i);
    }

    public int phiPos(Phi phi) {
        return phis.get(phi);
    }

    public List<Phi> phis() {
        return new ArrayList<>(phis.keySet());
    }

    public void orderPhis(List<Phi> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (nodes.get(i).predecessors().contains(nodes.get(j)) && i > j) {
                    Phi tmp = nodes.get(j);
                    nodes.set(j, nodes.get(i));
                    nodes.set(i, tmp);
                }
            }
        }
    }

    public List<Node> allNodes() {
        Node endNode = null;
        List<Node> result = new ArrayList<>();
        for (Node node : nodes) {
            if (!(node instanceof BranchNode || node instanceof JumpNode)) {
                result.add(node);
            }
            else {
                endNode = node;
            }
        }
        List<Phi> reversed = new ArrayList<>(this.phis.keySet());
        Collections.reverse(reversed);
        orderPhis(reversed);
        result.addAll(reversed);
        if (endNode != null) {
            result.add(endNode);
        }
        return result;
    }

    @Override
    protected String info() {
        return "[" + label + "]";
    }

    public void setNodes(List<Node> mutableNodes) {
        this.nodes.clear();
        this.nodes.addAll(mutableNodes);
    }
}

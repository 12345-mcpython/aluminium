package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.AttributeBuilder;

import java.util.*;

import static com.laosun.aluminium.Constant.PERCENT_TO_BASE;

/**
 * A node in a character's skill point (trace) tree.
 *
 * <p>Each skill point may grant an attribute bonus. The tree is built from raw
 * {@link com.laosun.aluminium.beans.SkillPoint} beans with parent-child relationships
 * determined by {@code prePoint} references. The tree is constructed once per character
 * and cached for subsequent use.
 *
 * <p>Supported operations:
 * <ul>
 *   <li>{@link #init(int)} — builds (or returns cached) the skill tree for a character</li>
 *   <li>{@link #sumAttributes(List)} — DFS-sums all attribute bonuses in the tree</li>
 *   <li>{@link #appendTo(List, AttributeBuilder)} — applies summed bonuses as modifiers</li>
 *   <li>{@link #printTree(List)} — prints the tree structure for debugging</li>
 * </ul>
 */
public final class SkillPoint {
    /** Cache of built skill trees, keyed by character ID. */
    private static final Map<Integer, List<SkillPoint>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Unique point ID within the character's tree. */
    public int pointId;
    /** Type string (e.g. "atk", "hp", "def"). */
    public String pointType;
    /** Optional attribute bonus granted by this node. */
    public com.laosun.aluminium.beans.SkillPoint.Attribute attribute;
    /** Whether this node is a root (has no parent). */
    public boolean root = false;
    /** Child nodes in the tree. */
    public List<SkillPoint> children;
    /** Parent node, or null if this is a root. */
    public SkillPoint parent;

    public SkillPoint() {
        this.children = new ArrayList<>();
    }

    /**
     * Builds (or returns from cache) the skill tree for a character.
     *
     * <p>The tree is constructed from the raw bean data loaded in
     * {@link Constant#SKILL_POINTS}. Nodes with no prerequisites become roots.
     * Results are cached per character ID since the tree never changes.
     *
     * @param cid the character ID
     * @return the list of root skill points, or an empty list if none found
     */
    public static List<SkillPoint> init(int cid) {
        return CACHE.computeIfAbsent(cid, unused -> buildTree(cid));
    }

    private static List<SkillPoint> buildTree(int cid) {
        List<com.laosun.aluminium.beans.SkillPoint> beanList = Constant.SKILL_POINTS.get(cid);
        if (beanList == null || beanList.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        java.util.Map<Integer, SkillPoint> nodeMap = new java.util.HashMap<>();

        for (com.laosun.aluminium.beans.SkillPoint bean : beanList) {
            SkillPoint node = new SkillPoint();
            node.pointId = bean.pointId();
            node.pointType = bean.pointType();
            node.attribute = bean.attribute();
            node.root = false;
            node.parent = null;
            nodeMap.put(node.pointId, node);
        }

        for (com.laosun.aluminium.beans.SkillPoint bean : beanList) {
            int pointId = bean.pointId();
            SkillPoint node = nodeMap.get(pointId);
            List<Integer> preIds = bean.prePoint();

            if (preIds == null || preIds.isEmpty()) {
                node.root = true;
            } else {
                int parentId = preIds.getFirst();
                SkillPoint parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.children.add(node);
                    node.parent = parent;
                    node.root = false;
                } else {
                    node.root = true;
                }
            }
        }

        java.util.List<SkillPoint> roots = new java.util.ArrayList<>();
        for (SkillPoint node : nodeMap.values()) {
            if (node.root) {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    public String toString() {
        Integer parentId = (parent != null) ? parent.pointId : null;
        return String.format("SkillPoint{id=%d, type='%s', attr=%s, root=%s, parentId=%s, childrenCount=%d}",
                pointId, pointType, attribute, root, parentId, children != null ? children.size() : 0);
    }

    /**
     * Returns a formatted tree representation (ASCII art).
     *
     * @return multi-line tree string
     */
    public String toTreeString() {
        return toTreeString("", true);
    }

    private String toTreeString(String prefix, boolean isTail) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix)
                .append(isTail ? "|___" : "|---")
                .append("[").append(pointId).append("]")
                .append(" (").append(pointType).append(")")
                .append(root ? " [ROOT]" : "")
                .append("\n");

        if (children != null && !children.isEmpty()) {
            for (int i = 0; i < children.size(); i++) {
                SkillPoint child = children.get(i);
                boolean lastChild = (i == children.size() - 1);
                sb.append(child.toTreeString(prefix + (isTail ? "    " : "│   "), lastChild));
            }
        }
        return sb.toString();
    }

    /**
     * Prints the skill tree to stdout.
     *
     * @param roots the root nodes to print
     */
    public static void printTree(List<SkillPoint> roots) {
        if (roots == null || roots.isEmpty()) {
            System.out.println("(NO SKILL POINTS)");
            return;
        }
        for (int i = 0; i < roots.size(); i++) {
            if (i > 0) System.out.println();
            System.out.print(roots.get(i).toTreeString());
        }
    }

    /**
     * Sums all attribute bonuses across the entire skill tree (DFS traversal).
     *
     * @param roots the root nodes to traverse
     * @return a map from attribute type to total bonus value
     */
    public static Map<AttributeType, Double> sumAttributes(List<SkillPoint> roots) {
        Map<AttributeType, Double> total = new HashMap<>();
        if (roots == null || roots.isEmpty()) {
            return total;
        }

        Deque<SkillPoint> stack = new ArrayDeque<>(roots);

        while (!stack.isEmpty()) {
            SkillPoint node = stack.pop();
            if (node.attribute != null) {
                AttributeType type = node.attribute.name();
                double value = node.attribute.value();
                total.merge(type, value, Double::sum);
            }
            if (node.children != null && !node.children.isEmpty()) {
                stack.addAll(node.children);
            }
        }
        return total;
    }

    /**
     * Sums all skill point attributes and appends them as modifiers to the builder.
     *
     * @param roots   the root nodes to traverse
     * @param builder the attribute builder to append to
     */
    public static void appendTo(List<SkillPoint> roots, AttributeBuilder builder) {
        Map<AttributeType, Double> total = sumAttributes(roots);
        for (Map.Entry<AttributeType, Double> entry : total.entrySet()) {
            if (PERCENT_TO_BASE.containsKey(entry.getKey())) {
                builder.addPercent(entry.getKey(), entry.getValue(), DoubleValue.Modifier.ModifierSource.SKILL_POINT);
            } else {
                if (entry.getKey().isPercent) {
                    builder.addPercentPoint(entry.getKey(), entry.getValue(), DoubleValue.Modifier.ModifierSource.SKILL_POINT);
                } else {
                    builder.addPure(entry.getKey(), entry.getValue(), DoubleValue.Modifier.ModifierSource.SKILL_POINT);
                }
            }
        }
    }

}

package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.AttributeBuilder;

import java.util.*;

import static com.laosun.aluminium.Constant.PERCENT_TO_BASE;

public final class SkillPoint {
    public int pointId;
    public String pointType;
    public com.laosun.aluminium.beans.SkillPoint.Attribute attribute;
    public boolean root = false;
    public List<SkillPoint> children;
    public SkillPoint parent;

    public SkillPoint() {
        this.children = new ArrayList<>();
    }

    public static List<SkillPoint> init(int cid) {
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

    public static void appendTo(List<SkillPoint> roots, AttributeBuilder builder) {
        Map<AttributeType, Double> total = sumAttributes(roots);
        for (Map.Entry<AttributeType, Double> entry : total.entrySet()) {
            if (PERCENT_TO_BASE.containsKey(entry.getKey())) {
                builder.addPercent(entry.getKey(), entry.getValue(), DoubleValue.Modifier.ModifierSource.RELIC);
            } else {
                if (entry.getKey().isPercent) {
                    builder.addPercentPoint(entry.getKey(), entry.getValue(), DoubleValue.Modifier.ModifierSource.RELIC);
                } else {
                    builder.addPure(entry.getKey(), entry.getValue(), DoubleValue.Modifier.ModifierSource.RELIC);
                }
            }
        }
    }

}

package com.studypilot.common;

/** 向量数学工具：余弦相似度，含维度/空值/非法数值/零范数防护 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * 余弦相似度。调用方须先保证模型/版本/维度一致；
     * 空向量、维度不一致、NaN/Infinity、零范数均抛 IllegalArgumentException（防止除零）。
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            throw new IllegalArgumentException("空向量不可比较");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致: " + a.length + " vs " + b.length);
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            if (!Float.isFinite(a[i]) || !Float.isFinite(b[i])) {
                throw new IllegalArgumentException("向量含非法数值(NaN/Infinity)");
            }
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            throw new IllegalArgumentException("零范数向量不可比较");
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
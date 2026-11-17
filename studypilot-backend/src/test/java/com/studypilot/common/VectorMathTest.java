package com.studypilot.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 余弦相似度数学与防护单元测试（非 AI 调用） */
class VectorMathTest {

    @Test
    void identicalVectorsScoreOne() {
        float[] v = {1f, 2f, 3f};
        assertEquals(1.0, VectorMath.cosine(v, v), 1e-9);
    }

    @Test
    void oppositeVectorsScoreMinusOne() {
        assertEquals(-1.0, VectorMath.cosine(new float[]{1f, 0f}, new float[]{-1f, 0f}), 1e-9);
    }

    @Test
    void orthogonalVectorsScoreZero() {
        assertEquals(0.0, VectorMath.cosine(new float[]{1f, 0f}, new float[]{0f, 1f}), 1e-9);
    }

    @Test
    void knownValue() {
        // cos([1,2,3],[4,5,6]) = 32 / (sqrt(14)*sqrt(77))
        double expected = 32.0 / (Math.sqrt(14) * Math.sqrt(77));
        assertEquals(expected, VectorMath.cosine(new float[]{1, 2, 3}, new float[]{4, 5, 6}), 1e-9);
    }

    @Test
    void dimensionMismatchRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> VectorMath.cosine(new float[]{1f, 2f}, new float[]{1f, 2f, 3f}));
        assertTrue(e.getMessage().contains("维度不一致"));
    }

    @Test
    void emptyOrNullRejected() {
        assertThrows(IllegalArgumentException.class, () -> VectorMath.cosine(new float[]{}, new float[]{}));
        assertThrows(IllegalArgumentException.class, () -> VectorMath.cosine(null, new float[]{1f}));
        assertThrows(IllegalArgumentException.class, () -> VectorMath.cosine(new float[]{1f}, null));
    }

    @Test
    void nanOrInfinityRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> VectorMath.cosine(new float[]{Float.NaN, 1f}, new float[]{1f, 1f}));
        assertThrows(IllegalArgumentException.class,
                () -> VectorMath.cosine(new float[]{1f, 1f}, new float[]{Float.POSITIVE_INFINITY, 1f}));
    }

    @Test
    void zeroNormRejectedNoDivisionByZero() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> VectorMath.cosine(new float[]{0f, 0f}, new float[]{1f, 1f}));
        assertTrue(e.getMessage().contains("零范数"));
    }

    @Test
    void highDimensionStability() {
        float[] a = new float[1024];
        float[] b = new float[1024];
        for (int i = 0; i < 1024; i++) {
            a[i] = (i % 7) * 0.01f;
            b[i] = (i % 5) * 0.02f;
        }
        double score = VectorMath.cosine(a, b);
        assertTrue(score >= -1.0 && score <= 1.0);
    }
}
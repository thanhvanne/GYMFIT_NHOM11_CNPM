package com.gymfit.chat.nlu;

/**
 * Vector thưa đã L2-normalize cho một câu.
 *
 * @param indices chỉ số feature
 * @param values  trọng số tương ứng
 */
public record SparseVector(
        int[] indices,
        float[] values
) {

    public int size() {
        return indices.length;
    }

    public static SparseVector empty() {
        return new SparseVector(
                new int[0],
                new float[0]
        );
    }

}
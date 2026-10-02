package com.gymfit.chat.nlu;

import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mô hình softmax regression thuần Java cho phân loại intent.
 *
 * <p>Định dạng file {@code intent-model.bin} (big-endian):
 * <pre>
 * magic "GFIM" | version:int | K:int | label[K] | F:int | (key:UTF,idf:float)*F | W[K][F]:float | b[K]:float
 * </pre>
 */
@Slf4j
public class IntentModel {

    public static final String MAGIC =
            "GFIM";

    public static final int VERSION = 1;

    /** K = số intent (label). */
    public final String[] labels;

    /** Từ điển feature: index → key, và idf tương ứng. */
    public final Map<String, Integer> featureIndex;

    public final float[] idf;

    /** W[K][F] — hệ số. */
    public final float[][] W;

    /** b[K] — hệ số chặn. */
    public final float[] b;

    public IntentModel(
            String[] labels,
            Map<String, Double> idfByKey,
            int features
    ) {
        this.labels =
                labels;

        this.featureIndex =
                new HashMap<>();

        this.idf =
                new float[features];

        this.W =
                new float[labels.length][features];

        this.b =
                new float[labels.length];

        int i =
                0;

        for (Map.Entry<String, Double> entry
                : new java.util.TreeMap<>(
                idfByKey
        ).entrySet()) {

            featureIndex.put(
                    entry.getKey(),
                    i
            );

            idf[i] =
                    entry.getValue()
                            .floatValue();

            i++;
        }
    }

    public int labelCount() {
        return labels.length;
    }

    public int featureCount() {
        return idf.length;
    }

    /**
     * Biến câu (đã mask) thành vector thưa L2-normalize.
     */
    /**
     * Biến câu (đã mask) thành vector thưa L2-normalize.
     */
    public SparseVector vectorize(
            String masked
    ) {
        return vectorize(
                FeatureExtractor.features(masked)
        );
    }

    /**
     * Vectorize từ danh sách feature đã trích sẵn — dùng cho trainer
     * (tránh phải trích lại feature từ chuỗi).
     */
    public SparseVector vectorize(
            List<String> keys
    ) {
        Map<Integer, Float> vector =
                new HashMap<>();

        for (String key : keys) {

            Integer index =
                    featureIndex.get(key);

            if (index == null) {
                continue;
            }

            vector.merge(
                    index,
                    1f,
                    Float::sum
            );
        }

        if (vector.isEmpty()) {
            return SparseVector.empty();
        }

        int[] sortedIdx =
                vector.keySet()
                        .stream()
                        .sorted()
                        .mapToInt(Integer::intValue)
                        .toArray();

        float[] sortedVal =
                new float[sortedIdx.length];

        double sum =
                0;

        for (int j = 0; j < sortedIdx.length; j++) {

            float v =
                    (float) (FeatureExtractor.tf(
                            Math.round(
                                    vector.get(
                                            sortedIdx[j]
                                    )
                            )
                    ) * idf[sortedIdx[j]]);

            sortedVal[j] =
                    v;

            sum += v * v;
        }

        double norm =
                Math.sqrt(sum);

        if (norm > 0) {
            for (int j = 0; j < sortedVal.length; j++) {
                sortedVal[j] =
                        (float) (sortedVal[j] / norm);
            }
        }

        return new SparseVector(
                sortedIdx,
                sortedVal
        );
    }

    /** Chuẩn hoá theo đúng cách train (cho đường dẫn predict nhanh). */
    public double[] probabilities(
            String masked
    ) {
        return probabilities(
                vectorize(masked)
        );
    }

    public double[] probabilities(
            SparseVector vector
    ) {
        double[] z =
                new double[labels.length];

        for (int k = 0; k < labels.length; k++) {

            float[] row =
                    W[k];

            float s =
                    b[k];

            int[] idx =
                    vector.indices();

            float[] val =
                    vector.values();

            for (int j = 0; j < idx.length; j++) {
                s += row[idx[j]]
                        * val[j];
            }

            z[k] =
                    s;
        }

        return softmax(z);
    }

    /**
     * Xác suất mỗi intent (softmax).
     */
    /**
     * Xác suất mỗi intent (softmax).
     */
    public double[] predict(
            String masked
    ) {
        return probabilities(masked);
    }
    public static double[] softmax(
            double[] z
    ) {
        double[] out =
                new double[z.length];

        softmax(
                z,
                out
        );

        return out;
    }

    /** Softmax ghi kết quả vào {@code out} để tránh cấp phát mảng mỗi vòng train. */
    public static void softmax(
            double[] z,
            double[] out
    ) {
        double max =
                Double.NEGATIVE_INFINITY;

        for (double v : z) {
            max =
                    Math.max(
                            max,
                            v
                    );
        }

        double sum =
                0;

        for (int i = 0; i < z.length; i++) {

            double e =
                    Math.exp(
                            z[i] - max
                    );

            out[i] =
                    e;

            sum += e;
        }

        for (int i = 0; i < out.length; i++) {
            out[i] =
                    sum == 0
                            ? 0
                            : out[i] / sum;
        }
    }

    /**
     * Top-k dự đoán.
     */
    public List<int[]> top(
            String masked,
            int k
    ) {
        double[] probs =
                predict(masked);

        Integer[] order =
                new Integer[probs.length];

        for (int i = 0; i < probs.length; i++) {
            order[i] =
                    i;
        }

        java.util.Arrays.sort(
                order,
                (x, y) ->
                        Double.compare(
                                probs[y],
                                probs[x]
                        )
        );

        List<int[]> result =
                new ArrayList<>();

        for (int i = 0; i < Math.min(
                k,
                order.length
        ); i++) {
            result.add(
                    new int[]{
                            order[i],
                            (int) (probs[order[i]]
                                    * 1_000_000)
                    }
            );
        }

        return result;
    }

    // ------------------------------------------------------------------
    // Serialize
    // ------------------------------------------------------------------

    public void save(
            OutputStream output
    ) throws IOException {
        DataOutputStream out =
                new DataOutputStream(output);

        out.writeUTF(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(labels.length);

        for (String label : labels) {
            out.writeUTF(label);
        }

        int f =
                featureIndex.size();

        out.writeInt(f);

        // featureIndex là HashMap → sắp theo key cho deterministic.
        List<String> keys =
                new ArrayList<>(
                        featureIndex.keySet()
                );

        keys.sort(
                Comparator.naturalOrder()
        );

        for (String key : keys) {

            int index =
                    featureIndex.get(key);

            out.writeUTF(key);
            out.writeFloat(idf[index]);
        }

        // W theo thứ tự label × index (index đã là thứ tự TreeMap → khớp idf)
        for (int k = 0; k < labels.length; k++) {
            for (int j = 0; j < f; j++) {
                out.writeFloat(
                        W[k][j]
                );
            }
        }

        for (int k = 0; k < labels.length; k++) {
            out.writeFloat(
                    b[k]
            );
        }

        out.flush();
    }

    public static IntentModel load(
            InputStream input
    ) throws IOException {
        DataInputStream in =
                new DataInputStream(input);

        String magic =
                in.readUTF();

        if (!MAGIC.equals(magic)) {
            throw new IOException(
                    "Magic không hợp lệ: " + magic
            );
        }

        int version =
                in.readInt();

        if (version != VERSION) {
            throw new IOException(
                    "Phiên bản model không hỗ trợ: "
                            + version
            );
        }

        int k =
                in.readInt();

        String[] labels =
                new String[k];

        for (int i = 0; i < k; i++) {
            labels[i] =
                    in.readUTF();
        }

        int f =
                in.readInt();

        Map<String, Double> idfByKey =
                new HashMap<>(
                        f
                );

        for (int i = 0; i < f; i++) {
            String key =
                    in.readUTF();

            float value =
                    in.readFloat();

            idfByKey.put(
                    key,
                    (double) value
            );
        }

        IntentModel model =
                new IntentModel(
                        labels,
                        idfByKey,
                        f
                );

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < f; j++) {
                model.W[i][j] =
                        in.readFloat();
            }
        }

        for (int i = 0; i < k; i++) {
            model.b[i] =
                    in.readFloat();
        }

        return model;
    }

}

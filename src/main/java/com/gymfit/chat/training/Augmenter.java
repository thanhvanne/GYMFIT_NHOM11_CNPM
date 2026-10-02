package com.gymfit.chat.training;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Tạo biến thể từ câu gốc để tăng độ phủ dữ liệu huấn luyện.
 *
 * <p>Nguyên tắc:
 * <ul>
 *     <li>Các phép biến đổi <b>độc lập xác suất</b>, mỗi câu sinh 0–3 biến thể.</li>
 *     <li>Chỉ gây lỗi gõ trên từ dài ≥ 4 ký tự.</li>
 *     <li>Không bỏ dấu (normalizer đã lo), không đổi nghĩa câu.</li>
 * </ul>
 */
@Slf4j
public final class Augmenter {

    public static final int MAX_VARIANTS = 3;

    public static final int MIN_WORD_LENGTH = 4;

    private static final double P_SWAP = 0.10;
    private static final double P_DROP = 0.08;
    private static final double P_DUP = 0.05;

    /** Teencode ngược: dạng viết đầy đủ → dạng ngắn. */
    private static final List<String[]> SHORTEN =
            List.of(
                    new String[]{
                            "không",
                            "ko"
                    },
                    new String[]{
                            "được",
                            "dc"
                    },
                    new String[]{
                            "hôm nay",
                            "hnay"
                    },
                    new String[]{
                            "ngày mai",
                            "ngmai"
                    },
                    new String[]{
                            "bao nhiêu",
                            "bn"
                    },
                    new String[]{
                            "nhiều",
                            "nhieu"
                    },
                    new String[]{
                            "làm sao",
                            "lamsao"
                    },
                    new String[]{
                            "vui lòng",
                            "pls"
                    },
                    new String[]{
                            "tôi",
                            "toi"
                    },
                    new String[]{
                            "mình",
                            "minh"
                    },
                    new String[]{
                            "nhé",
                            "nhe"
                    },
                    new String[]{
                            "giúp",
                            "giup"
                    }
            );

    private static final List<String> SUFFIX =
            List.of(
                    "ạ",
                    "nha",
                    "nhé",
                    "với",
                    "giúp mình",
                    "ơi",
                    "admin ơi",
                    "nhờ bạn"
            );

    private static final List<String> PUNCTUATION =
            List.of(
                    "?",
                    ".",
                    "!!",
                    "",
                    "",
                    ""
            );

    private Augmenter() {
    }

    /**
     * @return 0–3 biến thể của {@code text} (không trả về bản gốc)
     */
    public static List<String> variants(
            String text,
            Random random
    ) {
        List<String> result =
                new ArrayList<>();

        if (text == null
                || text.isBlank()) {
            return result;
        }

        int wanted =
                random.nextInt(
                        MAX_VARIANTS + 1
                );

        for (int i = 0; i < wanted; i++) {

            String variant =
                    text;

            if (random.nextDouble() < 0.45) {
                variant =
                        shorten(
                                variant,
                                random
                        );
            }

            if (random.nextDouble() < P_SWAP) {
                variant =
                        swap(
                                variant,
                                random
                        );
            }

            if (random.nextDouble() < P_DROP) {
                variant =
                        drop(
                                variant,
                                random
                        );
            }

            if (random.nextDouble() < P_DUP) {
                variant =
                        duplicate(
                                variant,
                                random
                        );
            }

            if (random.nextDouble() < 0.30) {
                variant =
                        variant
                                + " "
                                + SUFFIX.get(
                                random.nextInt(
                                        SUFFIX.size()
                                )
                        );
            }

            if (random.nextDouble() < 0.20) {
                variant =
                        capitalize(
                                variant
                        );
            }

            if (random.nextDouble() < 0.25) {
                variant =
                        variant
                                + PUNCTUATION.get(
                                random.nextInt(
                                        PUNCTUATION.size()
                                )
                        );
            }

            variant =
                    variant.trim();

            if (!variant.equals(text)
                    && !variant.isBlank()) {
                result.add(
                        variant
                );
            }
        }

        return result;
    }

    private static String shorten(
            String text,
            Random random
    ) {
        String result =
                text;

        String[] pair =
                SHORTEN.get(
                        random.nextInt(
                                SHORTEN.size()
                        )
                );

        result =
                replaceIgnoreCase(
                        result,
                        pair[0],
                        pair[1]
                );

        return result;
    }

    /** Hoán đổi 2 ký tự kề nhau trong một từ dài ≥ 4 ký tự. */
    private static String swap(
            String text,
            Random random
    ) {
        String[] words =
                text.split(" ");

        List<Integer> candidates =
                new ArrayList<>();

        for (int i = 0; i < words.length; i++) {
            if (words[i].length()
                    >= MIN_WORD_LENGTH) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            return text;
        }

        int index =
                candidates.get(
                        random.nextInt(
                                candidates.size()
                        )
                );

        String word =
                words[index];

        int position =
                random.nextInt(
                        word.length() - 1
                );

        words[index] =
                word.substring(
                        0,
                        position
                )
                        + word.charAt(
                        position + 1
                )
                        + word.charAt(
                        position
                )
                        + word.substring(
                        position + 2
                );

        return String.join(
                " ",
                words
        );
    }

    /** Bỏ 1 ký tự ngẫu nhiên trong từ dài ≥ 4 ký tự. */
    private static String drop(
            String text,
            Random random
    ) {
        String[] words =
                text.split(" ");

        List<Integer> candidates =
                new ArrayList<>();

        for (int i = 0; i < words.length; i++) {
            if (words[i].length()
                    >= MIN_WORD_LENGTH) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            return text;
        }

        int index =
                candidates.get(
                        random.nextInt(
                                candidates.size()
                        )
                );

        String word =
                words[index];

        int position =
                random.nextInt(
                        word.length()
                );

        words[index] =
                word.substring(
                        0,
                        position
                )
                        + word.substring(
                        position + 1
                );

        return String.join(
                " ",
                words
        );
    }

    /** Nhân đôi 1 ký tự trong từ dài ≥ 4 ký tự. */
    private static String duplicate(
            String text,
            Random random
    ) {
        String[] words =
                text.split(" ");

        List<Integer> candidates =
                new ArrayList<>();

        for (int i = 0; i < words.length; i++) {
            if (words[i].length()
                    >= MIN_WORD_LENGTH) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            return text;
        }

        int index =
                candidates.get(
                        random.nextInt(
                                candidates.size()
                        )
                );

        String word =
                words[index];

        int position =
                random.nextInt(
                        word.length()
                );

        words[index] =
                word.substring(
                        0,
                        position + 1
                )
                        + word.charAt(
                        position
                )
                        + word.substring(
                        position + 1
                );

        return String.join(
                " ",
                words
        );
    }

    private static String capitalize(
            String text
    ) {
        if (text.isBlank()) {
            return text;
        }

        return text.substring(
                        0,
                        1
                ).toUpperCase(Locale.ROOT)
                + text.substring(
                        1
                );
    }

    private static String replaceIgnoreCase(
            String text,
            String target,
            String replacement
    ) {
        if (target.isEmpty()
                || text.toLowerCase(
                        Locale.ROOT
                ).contains(
                        target.toLowerCase(
                                Locale.ROOT
                        )
                ) == false) {
            return text;
        }

        return text.replaceAll(
                "(?i)"
                        + java.util.regex.Pattern.quote(
                        target
                ),
                replacement
        );
    }

}
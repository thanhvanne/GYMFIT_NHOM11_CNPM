package com.gymfit.common.util;

import java.util.UUID;

public final class CodeGenerator {

    private CodeGenerator() {
    }

    public static String generate(String prefix) {
        return prefix
                + "_"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }
}
package com.gymfit.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordGeneratorTest {

    @Test
    @DisplayName("Mật khẩu luôn đúng độ dài 10 ký tự")
    void doDaiMuonKyTu() {

        for (int i = 0; i < 500; i++) {

            String password =
                    PasswordGenerator.generate();

            assertEquals(
                    10,
                    password.length(),
                    "password=" + password
            );
        }
    }

    @Test
    @DisplayName("Luôn có ≥1 chữ hoa, ≥1 chữ thường, ≥1 chữ số")
    void duNhomKyTu() {

        for (int i = 0; i < 500; i++) {

            String password =
                    PasswordGenerator.generate();

            assertTrue(
                    password.chars()
                            .anyMatch(Character::isUpperCase),
                    "thieu chu hoa: " + password
            );

            assertTrue(
                    password.chars()
                            .anyMatch(Character::isLowerCase),
                    "thieu chu thuong: " + password
            );

            assertTrue(
                    password.chars()
                            .anyMatch(Character::isDigit),
                    "thieu chu so: " + password
            );
        }
    }

    @Test
    @DisplayName("Không chứa ký tự dễ nhầm 0, O, 1, l, I")
    void khongChuaKyTuNhinSai() {

        String forbidden = "0O1lI";

        for (int i = 0; i < 1000; i++) {

            String password =
                    PasswordGenerator.generate();

            for (char character : forbidden.toCharArray()) {

                assertFalse(
                        password.indexOf(character) >= 0,
                        "chua '" + character + "': " + password
                );
            }
        }
    }

    @Test
    @DisplayName("2.000 lần sinh không bị trùng quá 1 cặp")
    void sinhNgauNhienKhongBiTrung() {

        Set<String> seen = new HashSet<>();

        int duplicate = 0;

        for (int i = 0; i < 2000; i++) {

            if (!seen.add(
                    PasswordGenerator.generate())) {

                duplicate++;
            }
        }

        assertTrue(
                duplicate <= 1,
                "trung " + duplicate + " cap (mong doi <= 1)"
        );
    }
}

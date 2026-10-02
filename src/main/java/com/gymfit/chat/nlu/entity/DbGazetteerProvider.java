package com.gymfit.chat.nlu.entity;

import com.gymfit.branch.BranchService;
import com.gymfit.branch.BranchStatus;
import com.gymfit.branch.dto.BranchResponse;
import com.gymfit.chat.nlu.TextNormalizer;
import com.gymfit.product.ProductService;
import com.gymfit.product.ProductStatus;
import com.gymfit.product.dto.ProductResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gazetteer lấy từ dữ liệu thật: alias chi nhánh và sản phẩm.
 * <p>
 * Nạp lúc khởi động và làm mới mỗi 10 phút bằng
 * {@link org.springframework.scheduling.annotation.Scheduled}.
 * Chỉ đọc → dùng {@link AtomicReference} để an toàn đa luồng.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DbGazetteerProvider implements GazetteerProvider {

    /** Bỏ tiền tố thương hiệu khỏi tên chi nhánh khi sinh alias. */
    private static final String BRAND = "gymfit";

    private final BranchService branchService;
    private final ProductService productService;

    private final AtomicReference<Map<String, Long>> branchAliases =
            new AtomicReference<>(Map.of());

    private final AtomicReference<Map<String, String>> productAliases =
            new AtomicReference<>(Map.of());

    @PostConstruct
    void initialize() {
        refresh();
    }

    @Override
    public Map<String, Long> branchAliases() {
        return branchAliases.get();
    }

    @Override
    public Map<String, String> productAliases() {
        return productAliases.get();
    }

    /**
     * Dựng lại alias từ danh sách chi nhánh / sản phẩm ACTIVE.
     * Không ném lỗi: log cảnh báo và giữ bản cũ nếu truy vấn hỏng.
     */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${gymfit.chatbot.gazetteer-refresh-ms:600000}",
            initialDelayString = "${gymfit.chatbot.gazetteer-refresh-ms:600000}"
    )
    public void refresh() {
        try {

            branchAliases.set(
                    buildBranchAliases(
                            branchService.list(
                                    BranchStatus.ACTIVE
                            )
                    )
            );

            productAliases.set(
                    buildProductAliases(
                            productService.list(
                                    ProductStatus.ACTIVE
                            )
                    )
            );

            log.debug(
                    "Đã làm mới gazetteer: {} chi nhánh, {} sản phẩm",
                    branchAliases.get()
                            .size(),
                    productAliases.get()
                            .size()
            );

        } catch (Exception exception) {

            log.warn(
                    "Không làm mới được gazetteer: {}",
                    exception.getMessage()
            );
        }
    }

    private static Map<String, Long> buildBranchAliases(
            List<BranchResponse> branches
    ) {
        Map<String, Long> aliases =
                new LinkedHashMap<>();

        for (BranchResponse branch : branches) {

            put(
                    aliases,
                    branch.code(),
                    branch.id()
            );

            put(
                    aliases,
                    stripBrand(
                            branch.name()
                    ),
                    branch.id()
            );

            // "Quận 7" → thêm "q7" cho tiện khi người dùng gõ tắt.
            String name =
                    stripBrand(
                            branch.name()
                    );

            if (name.startsWith("quan ")) {

                String shortCode =
                        "q"
                                + name.substring(
                                        "quan ".length()
                                );

                put(
                        aliases,
                        shortCode,
                        branch.id()
                );
            }

            put(
                    aliases,
                    "chi nhanh " + stripBrand(
                            branch.name()
                    ),
                    branch.id()
            );
        }

        return Map.copyOf(aliases);
    }

    private static Map<String, String> buildProductAliases(
            List<ProductResponse> products
    ) {
        Map<String, String> aliases =
                new LinkedHashMap<>();

        for (ProductResponse product : products) {

            put(
                    aliases,
                    product.name(),
                    product.sku()
            );

            put(
                    aliases,
                    product.sku(),
                    product.sku()
            );

            put(
                    aliases,
                    product.category(),
                    product.sku()
            );
        }

        return Map.copyOf(aliases);
    }

    private static String stripBrand(
            String name
    ) {
        if (name == null) {
            return "";
        }

        return TextNormalizer.toPlain(
                        name.toLowerCase(Locale.ROOT)
                                .replace(BRAND, "")
                )
                .trim();
    }

    private static void put(
            Map<String, String> target,
            String alias,
            String value
    ) {
        String plain =
                TextNormalizer.toPlain(alias)
                        .trim();

        if (plain.isBlank()) {
            return;
        }

        target.putIfAbsent(
                plain,
                value
        );
    }

    private static void put(
            Map<String, Long> target,
            String alias,
            Long value
    ) {
        String plain =
                TextNormalizer.toPlain(alias)
                        .trim();

        if (plain.isBlank()) {
            return;
        }

        target.putIfAbsent(
                plain,
                value
        );
    }

}
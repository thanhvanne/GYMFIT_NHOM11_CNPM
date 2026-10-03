package com.gymfit.chat.dialogue.handler;

import com.gymfit.chat.dto.ChatSuggestion;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlg.Fmt;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.membership.MembershipService;
import com.gymfit.membership.dto.MembershipResponse;
import com.gymfit.branch.BranchService;
import com.gymfit.branch.BranchStatus;
import com.gymfit.branch.dto.BranchResponse;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Danh sách sản phẩm bán lẻ — nhóm theo {@code category}, tối đa 10 dòng.
 */
@Component
@RequiredArgsConstructor
public class ProductHandler
        implements IntentHandler {

    private final com.gymfit.product.ProductService productService;
    private final ResponseTemplates templates;

    @Override
    public Set<Intent> supports() {
        return java.util.Set.of(
                Intent.LIST_PRODUCTS
        );
    }

    @Override
    public ChatResponse handle(
            HandlerContext context
    ) {

        try {

            List<com.gymfit.product.dto.ProductResponse> products =
                    productService.list(
                            com.gymfit.product.ProductStatus.ACTIVE
                    );

            if (products.isEmpty()) {
                return IntentHandler.message(
                        context,
                        templates.get(
                                "info.no_data"
                        )
                );
            }

            String body =
                    products.stream()
                            .limit(
                                    10
                            )
                            .map(product ->
                                    "• " + product.name()
                                            + " — " + Fmt.money(
                                            product.price()
                                    )
                                            + " (" + product.category() + ")")
                            .reduce(
                                    (a, b) -> a + "\n" + b
                            )
                            .orElse("");

            String message =
                    templates.get(
                            "products",
                            Map.of(
                                    "products",
                                    body
                            )
                    );

            return IntentHandler.message(
                    context,
                    message,
                    List.of(
                            ChatSuggestion.of(
                                    "Có bán nước suối không"
                            ),
                            ChatSuggestion.of(
                                    "Giá protein bao nhiêu"
                            )
                    )
            );

        } catch (ApiException exception) {

            return fail(
                    context,
                    exception
            );
        }
    }
}
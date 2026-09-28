package com.lifelog.expense.dto;

import com.lifelog.domain.expense.ExpenseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryCreateRequest(
        @NotNull(message = "카테고리 유형은 필수입니다.")
        ExpenseType type,

        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다.")
        String name
) {}

package com.lifelog.expense.dto;

import com.lifelog.domain.expense.ExpenseType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ExpenseRequest(
        @NotNull(message = "내역 유형은 필수입니다.")
        ExpenseType type,

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotNull(message = "금액은 필수입니다.")
        @Positive(message = "금액은 0보다 커야 합니다.")
        @Max(value = 99_999_999_999L, message = "금액은 99,999,999,999원 이하여야 합니다.")
        Long amount,

        @NotNull(message = "거래일은 필수입니다.")
        LocalDate transactionDate,

        @Size(max = 200, message = "설명은 200자 이하여야 합니다.")
        String description,

        @Size(max = 10_000, message = "메모는 10,000자 이하여야 합니다.")
        String memo
) {}

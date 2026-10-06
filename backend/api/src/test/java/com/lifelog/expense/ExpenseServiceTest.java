package com.lifelog.expense;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.expense.Expense;
import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseCategoryRepository;
import com.lifelog.domain.expense.ExpenseRepository;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.domain.expense.PaymentMethod;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.expense.dto.ExpenseRequest;
import com.lifelog.expense.dto.ExpenseResponse;
import com.lifelog.expense.dto.ExpenseSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.lifelog.expense.ExpenseTestFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExpenseService service;

    private User owner;
    private User other;
    private ExpenseCategory food;
    private ExpenseCategory salary;

    @BeforeEach
    void setUp() {
        owner = withId(User.create("owner@test.com", "encoded-pw", "소유자"), 1L);
        other = withId(User.create("other@test.com", "encoded-pw", "타인"), 2L);
        food = withId(ExpenseCategory.create(owner, ExpenseType.EXPENSE, "식비"), 10L);
        salary = withId(ExpenseCategory.create(owner, ExpenseType.INCOME, "급여"), 11L);
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((BusinessException) e).getStatus();
    }

    private ExpenseRequest request(ExpenseType type, Long categoryId, long amount) {
        return new ExpenseRequest(type, categoryId, amount, null, LocalDate.of(2026, 9, 1), "점심", "메모");
    }

    private ExpenseRequest request(ExpenseType type, Long categoryId, long amount, PaymentMethod paymentMethod) {
        return new ExpenseRequest(type, categoryId, amount, paymentMethod, LocalDate.of(2026, 9, 1), "점심", "메모");
    }

    private Expense expense(Long id, User user, ExpenseCategory category) {
        return withId(Expense.create(user, category, category.getType(), 12_000L, null,
                LocalDate.of(2026, 9, 1), "점심", "메모"), id);
    }

    // ---- create ----

    @Test
    void create_whenValid_savesAndReturnsResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));

        ExpenseResponse response = service.create(1L, request(ExpenseType.EXPENSE, 10L, 12_000L));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.categoryId()).isEqualTo(10L);
        assertThat(response.categoryName()).isEqualTo("식비");
        assertThat(response.amount()).isEqualTo(12_000L);
        assertThat(response.type()).isEqualTo(ExpenseType.EXPENSE);
    }

    @Test
    void create_whenUserNotFound_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, request(ExpenseType.EXPENSE, 10L, 1_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void create_whenCategoryNotFound_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, request(ExpenseType.EXPENSE, 999L, 1_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void create_whenCategoryOwnedByOther_throwsForbidden() {
        ExpenseCategory othersCategory = withId(ExpenseCategory.create(other, ExpenseType.EXPENSE, "식비"), 20L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.findById(20L)).thenReturn(Optional.of(othersCategory));

        assertThatThrownBy(() -> service.create(1L, request(ExpenseType.EXPENSE, 20L, 1_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void create_whenTypeMismatchesCategory_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.findById(11L)).thenReturn(Optional.of(salary));

        assertThatThrownBy(() -> service.create(1L, request(ExpenseType.EXPENSE, 11L, 1_000L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("카테고리 유형과 내역 유형이 일치하지 않습니다.")
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(expenseRepository);
    }

    // ---- list ----

    @Test
    void list_whenValid_delegatesFiltersAndMapsToSummary() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        Page<Expense> page = new PageImpl<>(List.of(expense(100L, owner, food)), pageable, 1);
        when(expenseRepository.findByUserIdAndFilter(1L, from, to, ExpenseType.EXPENSE, 10L, pageable)).thenReturn(page);

        Page<ExpenseSummary> result = service.list(1L, from, to, ExpenseType.EXPENSE, 10L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(100L);
        assertThat(result.getContent().get(0).categoryName()).isEqualTo("식비");
    }

    @Test
    void list_whenFromEqualsTo_isAllowed() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDate day = LocalDate.of(2026, 9, 1);
        when(expenseRepository.findByUserIdAndFilter(1L, day, day, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(service.list(1L, day, day, null, null, pageable).getTotalElements()).isZero();
    }

    @Test
    void list_whenFromAfterTo_throwsBadRequest() {
        assertThatThrownBy(() -> service.list(1L, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1),
                null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void list_whenOnlyOneBoundGiven_skipsRangeValidation() {
        Pageable pageable = PageRequest.of(0, 20);
        when(expenseRepository.findByUserIdAndFilter(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.list(1L, LocalDate.of(2026, 9, 2), null, null, null, pageable);
        service.list(1L, null, LocalDate.of(2026, 9, 2), null, null, pageable);

        verify(expenseRepository, times(2)).findByUserIdAndFilter(any(), any(), any(), any(), any(), any());
    }

    // ---- get ----

    @Test
    void get_whenOwned_returnsResponse() {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense(100L, owner, food)));

        ExpenseResponse response = service.get(1L, 100L);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.memo()).isEqualTo("메모");
    }

    @Test
    void get_whenNotFound_throwsNotFound() {
        when(expenseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void get_whenNotOwned_throwsForbidden() {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense(100L, owner, food)));

        assertThatThrownBy(() -> service.get(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- update ----

    @Test
    void update_whenCategoryAndTypeChanged_updatesExpense() {
        Expense existing = expense(100L, owner, food);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(expenseCategoryRepository.findById(11L)).thenReturn(Optional.of(salary));
        when(expenseRepository.save(existing)).thenReturn(existing);

        ExpenseResponse response = service.update(1L, 100L, request(ExpenseType.INCOME, 11L, 3_000_000L));

        assertThat(response.type()).isEqualTo(ExpenseType.INCOME);
        assertThat(response.categoryId()).isEqualTo(11L);
        assertThat(response.amount()).isEqualTo(3_000_000L);
    }

    @Test
    void update_whenTypeMismatchesCategory_throwsBadRequestAndKeepsState() {
        Expense existing = expense(100L, owner, food);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(expenseCategoryRepository.findById(11L)).thenReturn(Optional.of(salary));

        assertThatThrownBy(() -> service.update(1L, 100L, request(ExpenseType.EXPENSE, 11L, 1_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(existing.getCategory()).isSameAs(food);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void update_whenExpenseNotOwned_throwsForbidden() {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense(100L, owner, food)));

        assertThatThrownBy(() -> service.update(2L, 100L, request(ExpenseType.EXPENSE, 10L, 1_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(expenseCategoryRepository);
    }

    @Test
    void update_whenNewCategoryOwnedByOther_throwsForbidden() {
        ExpenseCategory othersCategory = withId(ExpenseCategory.create(other, ExpenseType.EXPENSE, "식비"), 20L);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense(100L, owner, food)));
        when(expenseCategoryRepository.findById(20L)).thenReturn(Optional.of(othersCategory));

        assertThatThrownBy(() -> service.update(1L, 100L, request(ExpenseType.EXPENSE, 20L, 1_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void update_whenExpenseNotFound_throwsNotFound() {
        when(expenseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, 999L, request(ExpenseType.EXPENSE, 10L, 1_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- delete ----

    @Test
    void delete_whenOwned_deletesExpense() {
        Expense existing = expense(100L, owner, food);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(existing));

        service.delete(1L, 100L);

        verify(expenseRepository).delete(existing);
    }

    @Test
    void delete_whenNotOwned_throwsForbidden() {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense(100L, owner, food)));

        assertThatThrownBy(() -> service.delete(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.FORBIDDEN);
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    void delete_whenNotFound_throwsNotFound() {
        when(expenseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseServiceTest::statusOf).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- paymentMethod ----

    @Test
    void create_withPaymentMethod_passesItToEntityAndResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));

        ExpenseResponse response = service.create(1L,
                request(ExpenseType.EXPENSE, 10L, 12_000L, PaymentMethod.DEBIT_CARD));

        org.mockito.ArgumentCaptor<Expense> captor = org.mockito.ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.DEBIT_CARD);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.DEBIT_CARD);
    }

    @Test
    void create_withoutPaymentMethod_returnsNullPaymentMethod() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));

        ExpenseResponse response = service.create(1L, request(ExpenseType.EXPENSE, 10L, 12_000L));

        assertThat(response.paymentMethod()).isNull();
    }

    @Test
    void update_withPaymentMethod_changesPaymentMethod() {
        Expense existing = expense(100L, owner, food);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseRepository.save(existing)).thenReturn(existing);

        ExpenseResponse response = service.update(1L, 100L,
                request(ExpenseType.EXPENSE, 10L, 12_000L, PaymentMethod.CASH));

        assertThat(existing.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void update_withoutPaymentMethod_clearsExistingPaymentMethod() {
        Expense existing = withId(Expense.create(owner, food, ExpenseType.EXPENSE, 12_000L,
                PaymentMethod.CREDIT_CARD, LocalDate.of(2026, 9, 1), "점심", null), 100L);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseRepository.save(existing)).thenReturn(existing);

        ExpenseResponse response = service.update(1L, 100L, request(ExpenseType.EXPENSE, 10L, 12_000L));

        assertThat(existing.getPaymentMethod()).isNull();
        assertThat(response.paymentMethod()).isNull();
    }

    @Test
    void list_whenExpenseHasPaymentMethod_mapsItToSummary() {
        Pageable pageable = PageRequest.of(0, 20);
        Expense withMethod = withId(Expense.create(owner, food, ExpenseType.EXPENSE, 12_000L,
                PaymentMethod.EASY_PAY, LocalDate.of(2026, 9, 1), "점심", null), 100L);
        when(expenseRepository.findByUserIdAndFilter(1L, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(withMethod), pageable, 1));

        Page<ExpenseSummary> result = service.list(1L, null, null, null, null, pageable);

        assertThat(result.getContent().get(0).paymentMethod()).isEqualTo(PaymentMethod.EASY_PAY);
    }
}

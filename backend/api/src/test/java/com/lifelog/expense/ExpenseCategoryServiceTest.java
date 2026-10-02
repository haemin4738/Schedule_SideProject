package com.lifelog.expense;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseCategoryRepository;
import com.lifelog.domain.expense.ExpenseRepository;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.expense.dto.ExpenseCategoryCreateRequest;
import com.lifelog.expense.dto.ExpenseCategoryResponse;
import com.lifelog.expense.dto.ExpenseCategoryUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static com.lifelog.expense.ExpenseTestFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseCategoryServiceTest {

    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExpenseCategoryService service;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = withId(User.create("owner@test.com", "encoded-pw", "소유자"), 1L);
    }

    private ExpenseCategory category(Long id, User user, ExpenseType type, String name) {
        return withId(ExpenseCategory.create(user, type, name), id);
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((BusinessException) e).getStatus();
    }

    @Test
    void list_whenCalled_mapsRepositoryResultToResponses() {
        when(expenseCategoryRepository.findAllByUserIdAndType(1L, ExpenseType.EXPENSE))
                .thenReturn(List.of(category(10L, owner, ExpenseType.EXPENSE, "식비")));

        List<ExpenseCategoryResponse> result = service.list(1L, ExpenseType.EXPENSE);

        assertThat(result).extracting(ExpenseCategoryResponse::id, ExpenseCategoryResponse::name)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, "식비"));
    }

    // ---------- addDefaults ----------

    @Test
    void addDefaults_whenNoCategories_createsAllDefaultsForUser() {
        when(expenseCategoryRepository.findAllByUserIdAndType(1L, null)).thenReturn(List.of());

        int added = service.addDefaults(owner);

        ArgumentCaptor<ExpenseCategory> saved = ArgumentCaptor.forClass(ExpenseCategory.class);
        verify(expenseCategoryRepository, times(DefaultExpenseCategories.ALL.size())).save(saved.capture());
        assertThat(added).isEqualTo(DefaultExpenseCategories.ALL.size());
        assertThat(saved.getAllValues()).allSatisfy(c -> assertThat(c.getUser()).isSameAs(owner));
        assertThat(saved.getAllValues()).extracting(ExpenseCategory::getType, ExpenseCategory::getName)
                .contains(org.assertj.core.groups.Tuple.tuple(ExpenseType.EXPENSE, "식비"),
                        org.assertj.core.groups.Tuple.tuple(ExpenseType.EXPENSE, "교통비"),
                        org.assertj.core.groups.Tuple.tuple(ExpenseType.EXPENSE, "기타"),
                        org.assertj.core.groups.Tuple.tuple(ExpenseType.INCOME, "급여"),
                        org.assertj.core.groups.Tuple.tuple(ExpenseType.INCOME, "기타"));
    }

    @Test
    void addDefaults_whenSomeAlreadyExist_addsOnlyMissingOnes() {
        when(expenseCategoryRepository.findAllByUserIdAndType(1L, null)).thenReturn(List.of(
                category(10L, owner, ExpenseType.EXPENSE, "식비"),
                category(11L, owner, ExpenseType.INCOME, "급여"),
                // 같은 이름이라도 유형이 다르면 다른 카테고리 — 지출 '용돈' 이 있어도 수입 '용돈' 은 추가된다
                category(12L, owner, ExpenseType.EXPENSE, "용돈")));

        int added = service.addDefaults(owner);

        ArgumentCaptor<ExpenseCategory> saved = ArgumentCaptor.forClass(ExpenseCategory.class);
        verify(expenseCategoryRepository, times(DefaultExpenseCategories.ALL.size() - 2)).save(saved.capture());
        assertThat(added).isEqualTo(DefaultExpenseCategories.ALL.size() - 2);
        assertThat(saved.getAllValues()).extracting(ExpenseCategory::getName).doesNotContain("식비", "급여");
        assertThat(saved.getAllValues()).extracting(ExpenseCategory::getType, ExpenseCategory::getName)
                .contains(org.assertj.core.groups.Tuple.tuple(ExpenseType.INCOME, "용돈"));
    }

    @Test
    void addDefaults_whenNearLimit_addsOnlyUpToLimit() {
        List<ExpenseCategory> existing = new java.util.ArrayList<>();
        for (int i = 0; i < ExpenseCategoryService.MAX_CATEGORIES_PER_USER - 3; i++) {
            existing.add(category((long) i, owner, ExpenseType.EXPENSE, "내 카테고리 " + i));
        }
        when(expenseCategoryRepository.findAllByUserIdAndType(1L, null)).thenReturn(existing);

        assertThat(service.addDefaults(owner)).isEqualTo(3);
        verify(expenseCategoryRepository, times(3)).save(any(ExpenseCategory.class));
    }

    @Test
    void addDefaults_whenAllExist_addsNothing() {
        List<ExpenseCategory> existing = DefaultExpenseCategories.ALL.stream()
                .map(e -> category(1L, owner, e.type(), e.name()))
                .toList();
        when(expenseCategoryRepository.findAllByUserIdAndType(1L, null)).thenReturn(existing);

        assertThat(service.addDefaults(owner)).isZero();
        verify(expenseCategoryRepository, never()).save(any());
    }

    @Test
    void addDefaultsByUserId_whenCalled_returnsFullListAfterAdding() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.findAllByUserIdAndType(1L, null))
                .thenReturn(List.of())
                .thenReturn(List.of(category(10L, owner, ExpenseType.EXPENSE, "식비")));

        List<ExpenseCategoryResponse> result = service.addDefaults(1L);

        assertThat(result).extracting(ExpenseCategoryResponse::name).containsExactly("식비");
    }

    @Test
    void addDefaultsByUserId_whenUserNotFound_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addDefaults(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_whenValid_trimsNameAndSaves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.countByUserId(1L)).thenReturn(0L);
        when(expenseCategoryRepository.existsByUserIdAndTypeAndName(1L, ExpenseType.EXPENSE, "식비")).thenReturn(false);
        when(expenseCategoryRepository.save(any(ExpenseCategory.class)))
                .thenAnswer(inv -> withId(inv.getArgument(0), 100L));

        ExpenseCategoryResponse response = service.create(1L,
                new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, "  식비  "));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.name()).isEqualTo("식비");
        assertThat(response.type()).isEqualTo(ExpenseType.EXPENSE);
    }

    @Test
    void create_whenUserNotFound_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, "식비")))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(expenseCategoryRepository);
    }

    @Test
    void create_whenLimitReached_throwsConflict() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.countByUserId(1L)).thenReturn(100L);

        assertThatThrownBy(() -> service.create(1L, new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, "식비")))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.CONFLICT);
        verify(expenseCategoryRepository, never()).save(any());
    }

    @Test
    void create_whenCountJustBelowLimit_allowsCreation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.countByUserId(1L)).thenReturn(99L);
        when(expenseCategoryRepository.existsByUserIdAndTypeAndName(any(), any(), anyString())).thenReturn(false);
        when(expenseCategoryRepository.save(any(ExpenseCategory.class)))
                .thenAnswer(inv -> withId(inv.getArgument(0), 100L));

        assertThat(service.create(1L, new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, "식비")).id())
                .isEqualTo(100L);
    }

    @Test
    void create_whenDuplicateName_throwsConflict() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(expenseCategoryRepository.countByUserId(1L)).thenReturn(3L);
        when(expenseCategoryRepository.existsByUserIdAndTypeAndName(1L, ExpenseType.EXPENSE, "식비")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, " 식비")))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.CONFLICT);
        verify(expenseCategoryRepository, never()).save(any());
    }

    @Test
    void update_whenNewName_checksDuplicateAndRenames() {
        ExpenseCategory food = category(10L, owner, ExpenseType.EXPENSE, "식비");
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseCategoryRepository.existsByUserIdAndTypeAndNameAndIdNot(1L, ExpenseType.EXPENSE, "외식", 10L)).thenReturn(false);
        when(expenseCategoryRepository.save(food)).thenReturn(food);

        ExpenseCategoryResponse response = service.update(1L, 10L, new ExpenseCategoryUpdateRequest(" 외식 "));

        assertThat(response.name()).isEqualTo("외식");
        assertThat(food.getName()).isEqualTo("외식");
    }

    @Test
    void update_whenSameName_skipsDuplicateCheck() {
        ExpenseCategory food = category(10L, owner, ExpenseType.EXPENSE, "식비");
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseCategoryRepository.save(food)).thenReturn(food);

        ExpenseCategoryResponse response = service.update(1L, 10L, new ExpenseCategoryUpdateRequest("식비 "));

        assertThat(response.name()).isEqualTo("식비");
        verify(expenseCategoryRepository, never()).existsByUserIdAndTypeAndNameAndIdNot(any(), any(), any(), any());
    }

    @Test
    void update_whenOnlyCaseChanged_checksDuplicateExcludingSelfAndRenames() {
        ExpenseCategory food = category(10L, owner, ExpenseType.EXPENSE, "food");
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseCategoryRepository.existsByUserIdAndTypeAndNameAndIdNot(1L, ExpenseType.EXPENSE, "Food", 10L)).thenReturn(false);
        when(expenseCategoryRepository.save(food)).thenReturn(food);

        ExpenseCategoryResponse response = service.update(1L, 10L, new ExpenseCategoryUpdateRequest("Food"));

        assertThat(response.name()).isEqualTo("Food");
        verify(expenseCategoryRepository).existsByUserIdAndTypeAndNameAndIdNot(1L, ExpenseType.EXPENSE, "Food", 10L);
    }

    @Test
    void update_whenDuplicateName_throwsConflict() {
        ExpenseCategory food = category(10L, owner, ExpenseType.EXPENSE, "식비");
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseCategoryRepository.existsByUserIdAndTypeAndNameAndIdNot(1L, ExpenseType.EXPENSE, "교통", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, 10L, new ExpenseCategoryUpdateRequest("교통")))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.CONFLICT);
        assertThat(food.getName()).isEqualTo("식비");
    }

    @Test
    void update_whenNotFound_throwsNotFound() {
        when(expenseCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, 999L, new ExpenseCategoryUpdateRequest("외식")))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_whenNotOwned_throwsForbidden() {
        ExpenseCategory food = category(10L, owner, ExpenseType.EXPENSE, "식비");
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));

        assertThatThrownBy(() -> service.update(2L, 10L, new ExpenseCategoryUpdateRequest("외식")))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void delete_whenNoLinkedExpenses_deletesCategory() {
        ExpenseCategory food = category(10L, owner, ExpenseType.EXPENSE, "식비");
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseRepository.existsByCategoryId(10L)).thenReturn(false);

        service.delete(1L, 10L);

        ArgumentCaptor<ExpenseCategory> captor = ArgumentCaptor.forClass(ExpenseCategory.class);
        verify(expenseCategoryRepository).delete(captor.capture());
        assertThat(captor.getValue()).isSameAs(food);
    }

    @Test
    void delete_whenLinkedExpensesExist_throwsConflict() {
        ExpenseCategory food = category(10L, owner, ExpenseType.EXPENSE, "식비");
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));
        when(expenseRepository.existsByCategoryId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 카테고리를 사용하는 내역이 있어 삭제할 수 없습니다.")
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.CONFLICT);
        verify(expenseCategoryRepository, never()).delete(any());
    }

    @Test
    void delete_whenNotOwned_throwsForbidden() {
        ExpenseCategory food = category(10L, owner, ExpenseType.EXPENSE, "식비");
        when(expenseCategoryRepository.findById(10L)).thenReturn(Optional.of(food));

        assertThatThrownBy(() -> service.delete(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void delete_whenNotFound_throwsNotFound() {
        when(expenseCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseCategoryServiceTest::statusOf).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

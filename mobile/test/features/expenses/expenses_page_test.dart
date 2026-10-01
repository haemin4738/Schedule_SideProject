import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/expenses/expense_type.dart';
import 'package:mobile/features/expenses/presentation/expenses_page.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';
import 'package:mobile/features/expenses/provider/expense_summary_provider.dart';
import 'package:mobile/features/expenses/provider/expenses_provider.dart';

import 'expense_fakes.dart';

const _lunch = ExpenseItem(
  id: 10,
  type: ExpenseType.EXPENSE,
  categoryId: 1,
  categoryName: '식비',
  amount: 12000,
  transactionDate: '2026-09-28',
  description: '점심',
);

const _salary = ExpenseItem(
  id: 11,
  type: ExpenseType.INCOME,
  categoryId: 3,
  categoryName: '급여',
  amount: 3000000,
  transactionDate: '2026-09-05',
);

class _Harness {
  final FakeExpensesNotifier expenses;
  final FakeExpenseSummaryNotifier summary;
  final FakeExpenseCategoriesNotifier categories;

  _Harness({
    AsyncValue<ExpensesState>? listState,
    AsyncValue<ExpenseSummaryState> summaryState =
        const AsyncValue.data(emptySummary),
    AsyncValue<List<ExpenseCategory>> categoriesState =
        const AsyncValue.data(sampleCategories),
    ExpenseItem? detail,
  })  : expenses = FakeExpensesNotifier(
          listState ?? AsyncValue.data(expensesState([_lunch, _salary])),
          detail: detail,
        ),
        summary = FakeExpenseSummaryNotifier(summaryState),
        categories = FakeExpenseCategoriesNotifier(categoriesState);

  Widget build({DateTime? initialMonth}) => ProviderScope(
        overrides: [
          expensesProvider.overrideWith((ref) => expenses),
          expenseSummaryProvider.overrideWith((ref) => summary),
          expenseCategoriesProvider.overrideWith((ref) => categories),
        ],
        child: MaterialApp(home: ExpensesPage(initialMonth: initialMonth)),
      );
}


Future<void> _pump(WidgetTester tester, _Harness harness) async {
  useTallScreen(tester);
  await tester.pumpWidget(harness.build());
}

Future<void> _openCreateForm(WidgetTester tester) async {
  await tester.tap(find.text('내역 추가'));
  await tester.pumpAndSettle();
}

Future<void> _tapSave(WidgetTester tester) async {
  final save = find.widgetWithText(ElevatedButton, '저장');
  await tester.ensureVisible(save);
  await tester.tap(save);
  await tester.pumpAndSettle();
}

void main() {
  group('페이지 렌더링', () {
    testWidgets('로딩 상태일 때 로딩 인디케이터를 보여준다', (tester) async {
      await _pump(
        tester,
        _Harness(
          listState: const AsyncValue.loading(),
          summaryState: const AsyncValue.loading(),
        ),
      );

      expect(find.byType(CircularProgressIndicator), findsWidgets);
      expect(find.text('내역이 없습니다'), findsNothing);
    });

    testWidgets('데이터 상태일 때 월, 요약, 목록, 카테고리별 지출을 렌더링한다', (tester) async {
      await _pump(
        tester,
        _Harness(
          summaryState: const AsyncValue.data(ExpenseSummaryState(
            totalIncome: 3000000,
            totalExpense: 12000,
            net: 2988000,
            categoryTotal: 12000,
            categories: [
              CategorySummaryItem(
                categoryId: 1,
                categoryName: '식비',
                amount: 12000,
                count: 1,
              ),
            ],
          )),
        ),
      );

      expect(find.text('2026년 9월'), findsOneWidget);
      // 요약 카드
      expect(find.text('3,000,000원'), findsOneWidget);
      expect(find.text('2,988,000원'), findsOneWidget);
      // 목록 행: 날짜(M/d), 카테고리명, 설명(없으면 '-'), 부호 금액
      expect(find.text('9/28'), findsOneWidget);
      expect(find.text('점심'), findsOneWidget);
      expect(find.text('-12,000원'), findsOneWidget);
      expect(find.text('9/5'), findsOneWidget);
      expect(find.text('-'), findsOneWidget);
      expect(find.text('+3,000,000원'), findsOneWidget);
      // 카테고리별 지출
      expect(find.text('1건 · '), findsOneWidget);
      expect(find.text('100%'), findsOneWidget);
      expect(find.text('지출 내역이 없습니다'), findsNothing);
      expect(find.text('내역이 없습니다'), findsNothing);
    });

    testWidgets('합계가 음수면 빨간색으로 표시한다', (tester) async {
      await _pump(
        tester,
        _Harness(
          summaryState: const AsyncValue.data(ExpenseSummaryState(
            totalIncome: 0,
            totalExpense: 5000,
            net: -5000,
            categoryTotal: 5000,
            categories: [],
          )),
        ),
      );

      final net = tester.widget<Text>(find.text('-5,000원'));
      expect(net.style?.color, Colors.red);
    });

    testWidgets('빈 상태일 때 안내 문구를 보여준다', (tester) async {
      await _pump(
        tester,
        _Harness(listState: AsyncValue.data(expensesState([]))),
      );

      expect(find.text('내역이 없습니다'), findsOneWidget);
      expect(find.text('지출 내역이 없습니다'), findsOneWidget);
    });

    testWidgets('빈 페이지여도 전체 페이지가 여러 개면 페이지 이동 버튼을 보여준다', (tester) async {
      await _pump(
        tester,
        _Harness(
          listState: const AsyncValue.data(ExpensesState(
            items: [],
            page: 1,
            size: 20,
            total: 20,
            totalPages: 2,
          )),
        ),
      );

      expect(find.text('내역이 없습니다'), findsOneWidget);
      expect(find.text('2 / 2'), findsOneWidget);
      final prev = tester.widget<IconButton>(find.ancestor(
        of: find.byTooltip('이전 페이지'),
        matching: find.byType(IconButton),
      ));
      expect(prev.onPressed, isNotNull);
    });

    testWidgets('에러 상태일 때 서버 오류 메시지를 보여준다', (tester) async {
      await _pump(
        tester,
        _Harness(
          listState: AsyncValue.error(
            serverError(400, '조회 시작일은 종료일보다 늦을 수 없습니다.'),
            StackTrace.empty,
          ),
        ),
      );

      expect(find.text('오류: 조회 시작일은 종료일보다 늦을 수 없습니다.'), findsOneWidget);
    });
  });

  group('월 이동 / 필터', () {
    testWidgets('다음 달 버튼을 누르면 목록과 요약을 해당 월로 재조회한다', (tester) async {
      final harness = _Harness();
      await _pump(tester, harness);

      await tester.tap(find.byTooltip('다음 달'));
      await tester.pump();

      expect(harness.expenses.monthChanges, [DateTime(2026, 10)]);
      expect(harness.summary.fetches, [DateTime(2026, 10)]);
    });

    testWidgets('유형 필터를 바꾸면 카테고리 필터가 초기화된다', (tester) async {
      final harness = _Harness();
      harness.expenses.categoryIdFilter = 1;
      await _pump(tester, harness);

      await tester.tap(find.widgetWithText(ChoiceChip, '수입'));
      await tester.pump();

      expect(harness.expenses.typeFilter, ExpenseType.INCOME);
      expect(harness.expenses.categoryIdFilter, isNull);
    });
  });

  group('삭제', () {
    testWidgets('확인 후 삭제하고 요약을 재조회한다', (tester) async {
      final harness = _Harness();
      await _pump(tester, harness);

      await tester.drag(find.text('점심'), const Offset(-500, 0));
      await tester.pumpAndSettle();
      expect(find.text('이 내역을 삭제할까요?'), findsOneWidget);

      await tester.tap(find.widgetWithText(TextButton, '삭제'));
      await tester.pumpAndSettle();

      expect(harness.expenses.deleted, [10]);
      expect(harness.summary.refreshCount, 1);
      expect(find.text('점심'), findsNothing);
    });
  });

  group('내역 폼', () {
    testWidgets('필수값이 비어 있으면 검증 메시지를 보여주고 저장하지 않는다', (tester) async {
      final harness = _Harness();
      await _pump(tester, harness);
      await _openCreateForm(tester);

      await _tapSave(tester);

      expect(find.text('카테고리를 선택하세요.'), findsOneWidget);
      expect(find.text('금액을 입력하세요.'), findsOneWidget);
      expect(harness.expenses.created, isEmpty);
    });

    testWidgets('금액이 범위를 벗어나면 검증 메시지를 보여준다', (tester) async {
      final harness = _Harness();
      await _pump(tester, harness);
      await _openCreateForm(tester);

      const rangeMessage = '금액은 1원 이상 99,999,999,999원 이하로 입력하세요.';
      final amountField = find.widgetWithText(TextFormField, '금액 (원)');

      await tester.enterText(amountField, '0');
      await _tapSave(tester);
      expect(find.text(rangeMessage), findsOneWidget);

      await tester.enterText(amountField, '100000000000');
      await _tapSave(tester);
      expect(find.text(rangeMessage), findsOneWidget);
      expect(harness.expenses.created, isEmpty);
    });

    testWidgets('유효한 입력이면 생성 요청 후 닫고 요약을 재조회한다', (tester) async {
      final harness = _Harness();
      await _pump(tester, harness);
      await _openCreateForm(tester);

      await tester.tap(find.byType(DropdownButtonFormField<int>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('교통').last);
      await tester.pumpAndSettle();
      await tester.enterText(
          find.widgetWithText(TextFormField, '금액 (원)'), '1500');
      await tester.enterText(
          find.widgetWithText(TextFormField, '설명 (선택)'), '  버스  ');
      await _tapSave(tester);

      expect(harness.expenses.created, hasLength(1));
      final body = harness.expenses.created.single;
      expect(body['type'], ExpenseType.EXPENSE);
      expect(body['categoryId'], 2);
      expect(body['amount'], 1500);
      expect(body['description'], '버스');
      expect(body['memo'], isNull);
      // 선택 월(2026-09)이 이번 달이 아니면 그 달 1일, 이번 달이면 오늘이 기본 날짜
      final now = DateTime.now();
      final expectedDate = (now.year == 2026 && now.month == 9)
          ? '2026-09-${now.day.toString().padLeft(2, '0')}'
          : '2026-09-01';
      expect(body['transactionDate'], expectedDate);
      expect(find.text('내역 수정'), findsNothing);
      expect(harness.summary.refreshCount, 1);
    });

    testWidgets('유형을 바꾸면 해당 유형 카테고리만 보이고 선택이 해제된다', (tester) async {
      final harness = _Harness();
      await _pump(tester, harness);
      await _openCreateForm(tester);

      await tester.tap(find.byType(DropdownButtonFormField<int>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('식비').last);
      await tester.pumpAndSettle();

      await tester.tap(find.descendant(
        of: find.byType(SegmentedButton<ExpenseType>),
        matching: find.text('수입'),
      ));
      await tester.pumpAndSettle();
      await _tapSave(tester);
      expect(find.text('카테고리를 선택하세요.'), findsOneWidget);

      await tester.tap(find.byType(DropdownButtonFormField<int>));
      await tester.pumpAndSettle();
      expect(find.text('급여'), findsWidgets);
      expect(find.text('교통'), findsNothing);
    });

    testWidgets('해당 유형 카테고리가 없으면 안내를 보여주고 저장을 비활성화한다', (tester) async {
      await _pump(
        tester,
        _Harness(categoriesState: const AsyncValue.data([])),
      );
      await _openCreateForm(tester);

      expect(find.text('먼저 카테고리를 추가하세요'), findsOneWidget);
      expect(find.widgetWithText(TextButton, '카테고리 관리'), findsOneWidget);
      final save = tester.widget<ElevatedButton>(
        find.widgetWithText(ElevatedButton, '저장'),
      );
      expect(save.onPressed, isNull);
    });

    testWidgets('카테고리 관리 버튼을 누르면 폼을 닫고 카테고리 관리 화면으로 이동한다', (tester) async {
      await _pump(
        tester,
        _Harness(categoriesState: const AsyncValue.data([])),
      );
      await _openCreateForm(tester);

      await tester.tap(find.widgetWithText(TextButton, '카테고리 관리'));
      await tester.pumpAndSettle();

      expect(find.text('먼저 카테고리를 추가하세요'), findsNothing);
      expect(find.widgetWithText(AppBar, '카테고리 관리'), findsOneWidget);
    });

    testWidgets('행을 누르면 단건 조회 결과로 폼을 채워 memo 가 유실되지 않는다', (tester) async {
      final harness = _Harness(
        detail: const ExpenseItem(
          id: 10,
          type: ExpenseType.EXPENSE,
          categoryId: 1,
          categoryName: '식비',
          amount: 12000,
          transactionDate: '2026-09-28',
          description: '점심',
          memo: '상세 조회로만 오는 메모',
        ),
      );
      await _pump(tester, harness);

      await tester.tap(find.text('점심'));
      await tester.pumpAndSettle();

      expect(harness.expenses.detailRequests, [10]);
      expect(find.text('내역 수정'), findsOneWidget);
      expect(find.text('상세 조회로만 오는 메모'), findsOneWidget);
      expect(find.text('12000'), findsOneWidget);
      expect(find.text('2026-09-28'), findsOneWidget);
    });
  });

  group('캘린더에서 달 지정 진입', () {
    testWidgets('initialMonth_다른달_목록과요약을그달로옮긴다', (tester) async {
      final harness = _Harness();
      useTallScreen(tester);
      final current = harness.expenses.month;
      final target = DateTime(current.year, current.month - 2);
      await tester.pumpWidget(harness.build(initialMonth: target));
      await tester.pump();

      expect(harness.expenses.monthChanges, [target]);
      expect(harness.summary.fetches, [target]);
    });

    testWidgets('initialMonth_이번달_다시조회하지않는다', (tester) async {
      final harness = _Harness();
      useTallScreen(tester);
      await tester.pumpWidget(harness.build(initialMonth: harness.expenses.month));
      await tester.pump();

      expect(harness.expenses.monthChanges, isEmpty);
      expect(harness.summary.fetches, isEmpty);
    });

    test('parseMonth_형식이맞으면그달1일_틀리면null', () {
      expect(ExpensesPage.parseMonth('2026-10'), DateTime(2026, 10));
      for (final bad in [null, '', '2026-13', '2026-1', '2026-10-01', 'abc']) {
        expect(ExpensesPage.parseMonth(bad), isNull, reason: '$bad');
      }
    });
  });
}

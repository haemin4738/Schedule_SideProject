import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/expenses/expense_type.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';
import 'package:mobile/features/expenses/provider/expense_summary_provider.dart';
import 'package:mobile/features/expenses/provider/expenses_provider.dart';

/// 실제 Dio 네트워크 호출 없이 provider 상태만 주입하기 위한 fake notifier 들.
/// 각 Notifier 생성자가 곧바로 fetch()를 호출하므로 네트워크 메서드를 전부 override 해서
/// 호출 기록만 남기고 원하는 state/결과를 직접 돌려준다.
class FakeExpensesNotifier extends ExpensesNotifier {
  FakeExpensesNotifier(AsyncValue<ExpensesState> initialState, {this.detail})
      : super(initialMonth: DateTime(2026, 9)) {
    state = initialState;
  }

  final ExpenseItem? detail;
  final List<int> detailRequests = [];
  final List<Map<String, Object?>> created = [];
  final List<int> deleted = [];
  final List<DateTime> monthChanges = [];

  @override
  Future<void> fetch({int page = 0, int size = 20}) async {}

  @override
  Future<void> refresh() async {}

  @override
  Future<void> goToPage(int page) async {}

  @override
  Future<void> changeMonth(DateTime newMonth) async {
    month = firstDayOfMonth(newMonth);
    monthChanges.add(month);
  }

  @override
  Future<void> filter({ExpenseType? type, int? categoryId}) async {
    typeFilter = type;
    categoryIdFilter = categoryId;
  }

  @override
  Future<ExpenseItem> getDetail(int id) async {
    detailRequests.add(id);
    return detail!;
  }

  @override
  Future<void> create({
    required ExpenseType type,
    required int categoryId,
    required int amount,
    required String transactionDate,
    String? description,
    String? memo,
  }) async {
    created.add({
      'type': type,
      'categoryId': categoryId,
      'amount': amount,
      'transactionDate': transactionDate,
      'description': description,
      'memo': memo,
    });
  }

  @override
  Future<void> delete(int id) async {
    deleted.add(id);
    final current = state.valueOrNull!;
    state = AsyncValue.data(ExpensesState(
      items: current.items.where((e) => e.id != id).toList(),
      page: current.page,
      size: current.size,
      total: current.total - 1,
      totalPages: current.totalPages,
    ));
  }
}

class FakeExpenseSummaryNotifier extends ExpenseSummaryNotifier {
  FakeExpenseSummaryNotifier(AsyncValue<ExpenseSummaryState> initialState)
      : super(initialMonth: DateTime(2026, 9)) {
    state = initialState;
  }

  final List<DateTime> fetches = [];
  int refreshCount = 0;
  bool _constructed = false;

  @override
  Future<void> fetch(DateTime newMonth) async {
    // 생성자에서 호출되는 최초 fetch 는 기록하지 않는다.
    if (_constructed) fetches.add(firstDayOfMonth(newMonth));
    _constructed = true;
  }

  @override
  Future<void> refresh() async => refreshCount++;
}

class FakeExpenseCategoriesNotifier extends ExpenseCategoriesNotifier {
  FakeExpenseCategoriesNotifier(
    AsyncValue<List<ExpenseCategory>> initialState, {
    this.deleteError,
  }) {
    state = initialState;
  }

  final Object? deleteError;
  final List<int> deleted = [];

  @override
  Future<void> fetch() async {}

  @override
  Future<void> delete(int id) async {
    if (deleteError != null) throw deleteError!;
    deleted.add(id);
  }
}

/// 서버 envelope 오류 응답을 흉내낸 DioException.
DioException serverError(int statusCode, String message) {
  final options = RequestOptions(path: '/api/v1/test');
  return DioException(
    requestOptions: options,
    response: Response(
      requestOptions: options,
      statusCode: statusCode,
      data: {'success': false, 'data': null, 'error': message},
    ),
    type: DioExceptionType.badResponse,
  );
}

const sampleCategories = [
  ExpenseCategory(id: 1, type: ExpenseType.EXPENSE, name: '식비'),
  ExpenseCategory(id: 2, type: ExpenseType.EXPENSE, name: '교통'),
  ExpenseCategory(id: 3, type: ExpenseType.INCOME, name: '급여'),
];

ExpensesState expensesState(List<ExpenseItem> items) => ExpensesState(
      items: items,
      page: 0,
      size: 20,
      total: items.length,
      totalPages: items.isEmpty ? 0 : 1,
    );

const emptySummary = ExpenseSummaryState(
  totalIncome: 0,
  totalExpense: 0,
  net: 0,
  categoryTotal: 0,
  categories: [],
);

/// 위젯 테스트 기본 화면(800x600)은 목록 하단이 lazy build 되지 않으므로 세로로 넉넉히 키운다.
void useTallScreen(WidgetTester tester) {
  tester.view.physicalSize = const Size(800, 2400);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);
}

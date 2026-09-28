import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:mobile/core/network/dio_client.dart';
import 'package:mobile/features/expenses/expense_type.dart';
import 'package:mobile/features/expenses/provider/expenses_provider.dart';

class CategorySummaryItem {
  final int categoryId;
  final String categoryName;
  final int amount;
  final int count;

  const CategorySummaryItem({
    required this.categoryId,
    required this.categoryName,
    required this.amount,
    required this.count,
  });

  factory CategorySummaryItem.fromJson(Map<String, dynamic> json) =>
      CategorySummaryItem(
        categoryId: json['categoryId'] as int,
        categoryName: json['categoryName'] as String,
        amount: (json['amount'] as num).toInt(),
        count: (json['count'] as num).toInt(),
      );
}

/// 선택 월의 월 요약(수입/지출/합계) + 카테고리별 지출.
class ExpenseSummaryState {
  final int totalIncome;
  final int totalExpense;
  final int net;
  final int categoryTotal;
  final List<CategorySummaryItem> categories;

  const ExpenseSummaryState({
    required this.totalIncome,
    required this.totalExpense,
    required this.net,
    required this.categoryTotal,
    required this.categories,
  });
}

class ExpenseSummaryNotifier
    extends StateNotifier<AsyncValue<ExpenseSummaryState>> {
  ExpenseSummaryNotifier({DateTime? initialMonth, Dio? dio})
      : month = firstDayOfMonth(initialMonth ?? DateTime.now()),
        _dio = dio ?? createDio(),
        super(const AsyncValue.loading()) {
    fetch(month);
  }

  final Dio _dio;

  DateTime month;
  int _requestSeq = 0;

  Future<void> fetch(DateTime newMonth) async {
    if (!mounted) return;
    month = firstDayOfMonth(newMonth);
    final seq = ++_requestSeq;
    state = const AsyncValue.loading();
    try {
      final yearMonth = DateFormat('yyyy-MM').format(month);
      final dateFormat = DateFormat('yyyy-MM-dd');
      final results = await Future.wait([
        _dio.get('/api/v1/expenses/summary/monthly', queryParameters: {
          'from': yearMonth,
          'to': yearMonth,
        }),
        _dio.get('/api/v1/expenses/summary/by-category', queryParameters: {
          'type': ExpenseType.EXPENSE.name,
          'from': dateFormat.format(month),
          'to': dateFormat.format(lastDayOfMonth(month)),
        }),
      ]);
      if (!mounted || seq != _requestSeq) return;
      final monthly = results[0].data['data'] as Map<String, dynamic>;
      final byCategory = results[1].data['data'] as Map<String, dynamic>;

      state = AsyncValue.data(ExpenseSummaryState(
        totalIncome: (monthly['totalIncome'] as num).toInt(),
        totalExpense: (monthly['totalExpense'] as num).toInt(),
        net: (monthly['net'] as num).toInt(),
        categoryTotal: (byCategory['total'] as num).toInt(),
        categories: (byCategory['categories'] as List)
            .map((e) => CategorySummaryItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      ));
    } catch (e, st) {
      if (!mounted || seq != _requestSeq) return;
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> refresh() => fetch(month);
}

/// 화면 재진입 시 이번 달로 다시 시작하고, 로그아웃 후 이전 사용자 데이터가 남지 않도록 autoDispose.
final expenseSummaryProvider = StateNotifierProvider.autoDispose<
    ExpenseSummaryNotifier, AsyncValue<ExpenseSummaryState>>(
  (_) => ExpenseSummaryNotifier(),
);

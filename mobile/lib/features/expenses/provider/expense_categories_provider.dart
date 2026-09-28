import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile/core/network/dio_client.dart';
import 'package:mobile/features/expenses/expense_type.dart';

class ExpenseCategory {
  final int id;
  final ExpenseType type;
  final String name;

  const ExpenseCategory({
    required this.id,
    required this.type,
    required this.name,
  });

  factory ExpenseCategory.fromJson(Map<String, dynamic> json) =>
      ExpenseCategory(
        id: json['id'] as int,
        type: ExpenseType.values.byName(json['type'] as String),
        name: json['name'] as String,
      );
}

/// 사용자의 전체 카테고리 (지출+수입, 최대 100개라 페이지네이션 없음).
/// 메인 화면 필터·내역 폼·카테고리 관리 화면이 같은 provider 를 공유하므로
/// 관리 화면에서 변경하면 다른 화면에도 바로 반영된다.
class ExpenseCategoriesNotifier
    extends StateNotifier<AsyncValue<List<ExpenseCategory>>> {
  ExpenseCategoriesNotifier() : super(const AsyncValue.loading()) {
    fetch();
  }

  final _dio = createDio();

  Future<void> fetch() async {
    state = const AsyncValue.loading();
    try {
      final res = await _dio.get('/api/v1/expense-categories');
      if (!mounted) return;
      state = AsyncValue.data((res.data['data'] as List)
          .map((e) => ExpenseCategory.fromJson(e as Map<String, dynamic>))
          .toList());
    } catch (e, st) {
      if (!mounted) return;
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> create({required ExpenseType type, required String name}) async {
    await _dio.post('/api/v1/expense-categories', data: {
      'type': type.name,
      'name': name,
    });
    await fetch();
  }

  Future<void> rename(int id, String name) async {
    await _dio.put('/api/v1/expense-categories/$id', data: {'name': name});
    await fetch();
  }

  Future<void> delete(int id) async {
    await _dio.delete('/api/v1/expense-categories/$id');
    await fetch();
  }
}

final expenseCategoriesProvider = StateNotifierProvider<
    ExpenseCategoriesNotifier, AsyncValue<List<ExpenseCategory>>>(
  (_) => ExpenseCategoriesNotifier(),
);

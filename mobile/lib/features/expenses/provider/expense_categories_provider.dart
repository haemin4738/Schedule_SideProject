import 'package:dio/dio.dart';
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
  ExpenseCategoriesNotifier({Dio? dio})
      : _dio = dio ?? createDio(),
        super(const AsyncValue.loading()) {
    fetch();
  }

  final Dio _dio;

  // 추가/변경을 연달아 할 때 늦게 도착한 이전 응답이 최신 목록을 덮어쓰지 않도록 한다.
  int _requestSeq = 0;

  /// 이미 목록이 있으면 로딩 상태로 바꾸지 않고 응답이 오면 교체한다.
  /// (추가/변경/삭제 후 재조회 때 탭 전체가 스피너로 깜빡이지 않도록)
  Future<void> fetch() async {
    if (!mounted) return;
    final seq = ++_requestSeq;
    if (!state.hasValue) state = const AsyncValue.loading();
    try {
      final res = await _dio.get('/api/v1/expense-categories');
      if (!mounted || seq != _requestSeq) return;
      state = AsyncValue.data((res.data['data'] as List)
          .map((e) => ExpenseCategory.fromJson(e as Map<String, dynamic>))
          .toList());
    } catch (e, st) {
      if (!mounted || seq != _requestSeq) return;
      // 재조회 실패 시 기존 목록을 버리지 않는다 (필터/관리 화면이 비지 않도록)
      state = AsyncValue<List<ExpenseCategory>>.error(e, st).copyWithPrevious(state);
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

/// 가계부 화면을 벗어나면(로그아웃·세션 만료로 라우터가 재생성되는 경우 포함) 폐기해
/// 다른 계정의 데이터가 남지 않도록 autoDispose 로 둔다.
/// 카테고리 관리 화면은 메인 화면 위에 push 되므로 메인이 계속 구독해 같은 인스턴스를 공유한다.
final expenseCategoriesProvider = StateNotifierProvider.autoDispose<
    ExpenseCategoriesNotifier, AsyncValue<List<ExpenseCategory>>>(
  (_) => ExpenseCategoriesNotifier(),
);

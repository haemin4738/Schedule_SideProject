import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:mobile/core/network/dio_client.dart';
import 'package:mobile/features/expenses/expense_type.dart';

final _dateFormat = DateFormat('yyyy-MM-dd');

/// 서버 envelope 의 error 메시지(한국어)를 그대로 꺼낸다. 없으면 일반 안내 문구.
String expenseErrorMessage(Object error) {
  if (error is DioException) {
    final data = error.response?.data;
    if (data is Map && data['error'] is String) return data['error'] as String;
    return '서버와 통신할 수 없습니다.';
  }
  // 응답 파싱 오류 등 내부 예외 메시지는 사용자에게 노출하지 않는다
  return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.';
}

/// 해당 월의 1일 (시각 제거).
DateTime firstDayOfMonth(DateTime date) => DateTime(date.year, date.month);

/// 해당 월의 말일.
DateTime lastDayOfMonth(DateTime date) => DateTime(date.year, date.month + 1, 0);

/// 목록(ExpenseSummary) / 상세(ExpenseResponse) 응답을 모두 이 모델 하나로 파싱한다.
/// 목록 응답에는 memo 가 없으므로 수정 폼은 반드시 getDetail() 결과로 채운다.
class ExpenseItem {
  final int id;
  final ExpenseType type;
  final int categoryId;
  final String categoryName;
  final int amount;
  final String transactionDate;
  final String? description;
  final String? memo;

  const ExpenseItem({
    required this.id,
    required this.type,
    required this.categoryId,
    required this.categoryName,
    required this.amount,
    required this.transactionDate,
    this.description,
    this.memo,
  });

  factory ExpenseItem.fromJson(Map<String, dynamic> json) => ExpenseItem(
        id: json['id'] as int,
        type: ExpenseType.values.byName(json['type'] as String),
        categoryId: json['categoryId'] as int,
        categoryName: json['categoryName'] as String,
        amount: (json['amount'] as num).toInt(),
        transactionDate: json['transactionDate'] as String,
        description: json['description'] as String?,
        memo: json['memo'] as String?,
      );
}

/// 목록 화면 상태: 아이템 + 페이지네이션 메타.
/// 선택 월/필터는 로딩 중에도 화면에 보여야 하므로 notifier 필드로 보관한다.
class ExpensesState {
  final List<ExpenseItem> items;
  final int page;
  final int size;
  final int total;
  final int totalPages;

  const ExpensesState({
    required this.items,
    required this.page,
    required this.size,
    required this.total,
    required this.totalPages,
  });
}

class ExpensesNotifier extends StateNotifier<AsyncValue<ExpensesState>> {
  ExpensesNotifier({DateTime? initialMonth})
      : month = firstDayOfMonth(initialMonth ?? DateTime.now()),
        super(const AsyncValue.loading()) {
    fetch();
  }

  final _dio = createDio();

  /// 선택 월의 1일.
  DateTime month;
  ExpenseType? typeFilter;
  int? categoryIdFilter;

  // 월 이동을 빠르게 반복할 때 늦게 도착한 이전 응답이 최신 상태를 덮어쓰지 않도록 한다.
  int _requestSeq = 0;

  Future<void> fetch({int page = 0, int size = 20}) async {
    final seq = ++_requestSeq;
    state = const AsyncValue.loading();
    try {
      final query = <String, dynamic>{
        'from': _dateFormat.format(month),
        'to': _dateFormat.format(lastDayOfMonth(month)),
        'page': page,
        'size': size,
      };
      if (typeFilter != null) query['type'] = typeFilter!.name;
      if (categoryIdFilter != null) query['categoryId'] = categoryIdFilter;

      final res = await _dio.get('/api/v1/expenses', queryParameters: query);
      if (!mounted || seq != _requestSeq) return;
      final items = (res.data['data'] as List)
          .map((e) => ExpenseItem.fromJson(e as Map<String, dynamic>))
          .toList();
      final meta = res.data['meta'] as Map<String, dynamic>;

      state = AsyncValue.data(ExpensesState(
        items: items,
        page: meta['page'] as int,
        size: meta['size'] as int,
        total: meta['total'] as int,
        totalPages: meta['totalPages'] as int,
      ));
    } catch (e, st) {
      if (!mounted || seq != _requestSeq) return;
      state = AsyncValue.error(e, st);
    }
  }

  /// 현재 월/필터/페이지를 유지한 채 다시 조회한다.
  Future<void> refresh() => fetch(page: state.valueOrNull?.page ?? 0);

  Future<void> goToPage(int page) => fetch(page: page);

  /// 월 변경 시 페이지는 0으로 돌아간다. (필터는 유지)
  Future<void> changeMonth(DateTime newMonth) {
    month = firstDayOfMonth(newMonth);
    return fetch();
  }

  Future<void> filter({ExpenseType? type, int? categoryId}) {
    typeFilter = type;
    categoryIdFilter = categoryId;
    return fetch();
  }

  Future<ExpenseItem> getDetail(int id) async {
    final res = await _dio.get('/api/v1/expenses/$id');
    return ExpenseItem.fromJson(res.data['data'] as Map<String, dynamic>);
  }

  Future<void> create({
    required ExpenseType type,
    required int categoryId,
    required int amount,
    required String transactionDate,
    String? description,
    String? memo,
  }) async {
    await _dio.post('/api/v1/expenses', data: {
      'type': type.name,
      'categoryId': categoryId,
      'amount': amount,
      'transactionDate': transactionDate,
      'description': description,
      'memo': memo,
    });
    await refresh();
  }

  Future<void> update(
    int id, {
    required ExpenseType type,
    required int categoryId,
    required int amount,
    required String transactionDate,
    String? description,
    String? memo,
  }) async {
    await _dio.put('/api/v1/expenses/$id', data: {
      'type': type.name,
      'categoryId': categoryId,
      'amount': amount,
      'transactionDate': transactionDate,
      'description': description,
      'memo': memo,
    });
    await refresh();
  }

  /// 스와이프 삭제된 행이 트리에 남지 않도록 먼저 목록에서 제거한 뒤 서버에 요청한다.
  /// 실패해도 refresh 로 서버 상태를 다시 반영하고, 오류는 호출자에게 전달한다.
  Future<void> delete(int id) async {
    final current = state.valueOrNull;
    if (current != null) {
      state = AsyncValue.data(ExpensesState(
        items: current.items.where((e) => e.id != id).toList(),
        page: current.page,
        size: current.size,
        total: current.total,
        totalPages: current.totalPages,
      ));
    }
    try {
      await _dio.delete('/api/v1/expenses/$id');
    } finally {
      await refresh();
    }
  }
}

final expensesProvider =
    StateNotifierProvider<ExpensesNotifier, AsyncValue<ExpensesState>>(
  (_) => ExpensesNotifier(),
);

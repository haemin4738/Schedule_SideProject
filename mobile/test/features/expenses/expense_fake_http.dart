import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

/// 실제 네트워크 호출 없이 notifier 의 실제 요청/응답 처리 로직을 검증하기 위한 가짜 어댑터.
/// handler 가 Future 를 돌려주면 응답 도착 시점을 테스트에서 직접 제어할 수 있다.
class FakeHttpAdapter implements HttpClientAdapter {
  FakeHttpAdapter(this.handler);

  final FutureOr<ResponseBody> Function(RequestOptions options) handler;
  final List<RequestOptions> requests = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}

  /// method + path 로 요청을 거른다. (예: requestsOf('GET', '/api/v1/expenses'))
  List<RequestOptions> requestsOf(String method, String path) =>
      requests.where((r) => r.method == method && r.path == path).toList();
}

Dio fakeDio(FakeHttpAdapter adapter) =>
    Dio(BaseOptions(baseUrl: 'http://test', contentType: 'application/json'))
      ..httpClientAdapter = adapter;

ResponseBody jsonBody(int status, Object body) => ResponseBody.fromString(
  jsonEncode(body),
  status,
  headers: {
    Headers.contentTypeHeader: [Headers.jsonContentType],
  },
);

ResponseBody ok(Object? data, {Map<String, Object>? meta}) => jsonBody(200, {
  'success': true,
  'data': data,
  'error': null,
  'meta': ?meta,
});

ResponseBody errorBody(int status, String message) =>
    jsonBody(status, {'success': false, 'data': null, 'error': message});

/// 요청 바디(Map)를 JSON 왕복시켜 비교하기 쉽게 만든다.
Map<String, dynamic> bodyOf(RequestOptions options) =>
    jsonDecode(jsonEncode(options.data)) as Map<String, dynamic>;

Map<String, Object?> expenseJson(
  int id, {
  String type = 'EXPENSE',
  int categoryId = 1,
  String categoryName = '식비',
  int amount = 1000,
  String transactionDate = '2026-09-10',
  String? description,
  String? memo,
  String? paymentMethod,
}) => {
  'id': id,
  'type': type,
  'categoryId': categoryId,
  'categoryName': categoryName,
  'amount': amount,
  'transactionDate': transactionDate,
  'description': description,
  'memo': memo,
  'paymentMethod': paymentMethod,
};

/// 목록 응답: items 와 페이지네이션 메타.
ResponseBody expensePage(
  List<Map<String, Object?>> items, {
  int page = 0,
  int size = 20,
  int? total,
  int? totalPages,
}) => ok(
  items,
  meta: {
    'page': page,
    'size': size,
    'total': total ?? items.length,
    'totalPages': totalPages ?? (items.isEmpty ? 0 : 1),
  },
);

ResponseBody monthlySummary({int income = 0, int expense = 0}) => ok({
  'totalIncome': income,
  'totalExpense': expense,
  'net': income - expense,
});

ResponseBody categorySummary({
  int total = 0,
  List<Map<String, Object?>> categories = const [],
}) => ok({'total': total, 'categories': categories});

Map<String, Object?> categoryJson(
  int id,
  String name, {
  String type = 'EXPENSE',
}) => {'id': id, 'type': type, 'name': name};

/// dio 인터셉터/transformer 체인이 여러 이벤트 루프에 걸쳐 진행되므로 조건이 참이 될 때까지 기다린다.
Future<void> until(bool Function() condition) async {
  for (var i = 0; i < 200 && !condition(); i++) {
    await Future<void>.delayed(Duration.zero);
  }
  expect(condition(), isTrue, reason: '조건이 충족되지 않았습니다');
}

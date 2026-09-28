import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/expenses/presentation/expenses_page.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';
import 'package:mobile/features/expenses/provider/expense_summary_provider.dart';
import 'package:mobile/features/expenses/provider/expenses_provider.dart';

import 'expense_fake_http.dart';
import 'expense_fakes.dart';

const _list = '/api/v1/expenses';
const _monthly = '/api/v1/expenses/summary/monthly';
const _byCategory = '/api/v1/expenses/summary/by-category';

/// fake notifier 대신 실제 notifier 를 가짜 HTTP 어댑터에 연결해 화면 → notifier → 요청 흐름을 검증한다.
class _Server {
  late final adapter = FakeHttpAdapter(_handle);
  var items = <Map<String, Object?>>[
    expenseJson(
      10,
      description: '점심',
      amount: 12000,
      transactionDate: '2026-09-28',
    ),
  ];
  Map<String, Object?> detail = expenseJson(
    10,
    description: '점심',
    amount: 12000,
    transactionDate: '2026-09-28',
    memo: '상세 조회로만 오는 메모',
  );

  ResponseBody _handle(RequestOptions o) {
    if (o.method == 'DELETE') {
      items = items.where((e) => '$_list/${e['id']}' != o.path).toList();
      return ok(null);
    }
    if (o.method == 'PUT' || o.method == 'POST') return ok(detail);
    switch (o.path) {
      case _list:
        return expensePage(items);
      case '$_list/10':
        return ok(detail);
      case _monthly:
        return monthlySummary(expense: 12000);
      case _byCategory:
        return categorySummary();
      case '/api/v1/expense-categories':
        return ok([categoryJson(1, '식비')]);
    }
    return errorBody(404, '없음');
  }

  Dio get dio => fakeDio(adapter);

  List<RequestOptions> requests(String method, String path) =>
      adapter.requestsOf(method, path);

  Widget build() => ProviderScope(
    overrides: [
      expensesProvider.overrideWith(
        (ref) => ExpensesNotifier(initialMonth: DateTime(2026, 9), dio: dio),
      ),
      expenseSummaryProvider.overrideWith(
        (ref) =>
            ExpenseSummaryNotifier(initialMonth: DateTime(2026, 9), dio: dio),
      ),
      expenseCategoriesProvider.overrideWith(
        (ref) => ExpenseCategoriesNotifier(dio: dio),
      ),
    ],
    child: const MaterialApp(home: ExpensesPage()),
  );
}

Future<_Server> _pump(WidgetTester tester) async {
  useTallScreen(tester);
  final server = _Server();
  await tester.pumpWidget(server.build());
  await tester.pumpAndSettle();
  return server;
}

Future<void> _openEditAndSave(
  WidgetTester tester, {
  String? description,
  String? memo,
}) async {
  await tester.tap(find.text('점심'));
  await tester.pumpAndSettle();
  if (description != null) {
    await tester.enterText(
      find.widgetWithText(TextFormField, '설명 (선택)'),
      description,
    );
  }
  if (memo != null) {
    await tester.enterText(find.widgetWithText(TextFormField, '메모 (선택)'), memo);
  }
  final save = find.widgetWithText(ElevatedButton, '저장');
  await tester.ensureVisible(save);
  await tester.tap(save);
  await tester.pumpAndSettle();
}

/// Dismissible 의 onDismissed 에서 시작된 요청은 pumpAndSettle 만으로는 완료되지 않을 수 있어
/// 실제 비동기 한 번을 흘려보낸 뒤 다시 프레임을 정리한다.
Future<void> _flush(WidgetTester tester) async {
  // 요청 → 응답 → 후속 재조회가 이어지므로 몇 차례 나눠 흘려보낸다.
  for (var i = 0; i < 5; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 20)),
    );
    await tester.pumpAndSettle();
  }
}

void main() {
  testWidgets('스와이프 삭제 시 DELETE 후 목록과 요약을 다시 조회한다', (tester) async {
    final server = await _pump(tester);
    expect(server.requests('GET', _list), hasLength(1));
    expect(server.requests('GET', _monthly), hasLength(1));

    await tester.drag(find.text('점심'), const Offset(-500, 0));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(TextButton, '삭제'));
    await tester.pumpAndSettle();
    await _flush(tester);

    expect(server.requests('DELETE', '$_list/10'), hasLength(1));
    expect(server.requests('GET', _list), hasLength(2));
    expect(server.requests('GET', _monthly), hasLength(2));
    expect(server.requests('GET', _byCategory), hasLength(2));
    expect(find.text('점심'), findsNothing);
    expect(find.text('내역이 없습니다'), findsOneWidget);
  });

  testWidgets('수정 저장 시 단건 조회로 받은 memo 를 요청 바디에 그대로 싣는다', (tester) async {
    final server = await _pump(tester);

    await _openEditAndSave(tester);

    expect(server.requests('GET', '$_list/10'), hasLength(1));
    final body = bodyOf(server.requests('PUT', '$_list/10').single);
    expect(body['memo'], '상세 조회로만 오는 메모');
    expect(body['description'], '점심');
    expect(body['categoryId'], 1);
    expect(body['amount'], 12000);
    expect(body['transactionDate'], '2026-09-28');
    expect(find.text('내역 수정'), findsNothing);
  });

  testWidgets('description 은 trim 하고 memo 는 앞뒤 공백·줄바꿈을 보존한다', (tester) async {
    final server = await _pump(tester);

    await _openEditAndSave(
      tester,
      description: '  저녁  ',
      memo: '  첫 줄\n  둘째 줄  ',
    );

    final body = bodyOf(server.requests('PUT', '$_list/10').single);
    expect(body['description'], '저녁');
    expect(body['memo'], '  첫 줄\n  둘째 줄  ');
  });

  testWidgets('description·memo 가 공백뿐이면 null 로 보낸다', (tester) async {
    final server = await _pump(tester);

    await _openEditAndSave(tester, description: '   ', memo: ' \n ');

    final body = bodyOf(server.requests('PUT', '$_list/10').single);
    expect(body['description'], isNull);
    expect(body['memo'], isNull);
  });

  testWidgets('카테고리 관리에서 돌아왔을 때 선택 카테고리가 삭제됐으면 필터를 해제하고 다시 조회한다', (
    tester,
  ) async {
    final server = await _pump(tester);
    final container = ProviderScope.containerOf(
      tester.element(find.byType(ExpensesPage)),
    );
    final notifier = container.read(expensesProvider.notifier);
    await tester.runAsync(() => notifier.filter(categoryId: 9));
    await tester.pumpAndSettle();
    expect(server.requests('GET', _list).last.queryParameters['categoryId'], 9);

    await tester.tap(find.byTooltip('카테고리 관리'));
    await tester.pumpAndSettle();
    await tester.pageBack();
    await _flush(tester);

    expect(notifier.categoryIdFilter, isNull);
    final query = server.requests('GET', _list).last.queryParameters;
    expect(query.containsKey('categoryId'), isFalse);
    expect(query['page'], 0);
    expect(server.requests('GET', _monthly), hasLength(2));
  });
}

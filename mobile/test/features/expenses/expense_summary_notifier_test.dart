import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/expenses/provider/expense_summary_provider.dart';

import 'expense_fake_http.dart';

const _monthly = '/api/v1/expenses/summary/monthly';
const _byCategory = '/api/v1/expenses/summary/by-category';

void main() {
  late FakeHttpAdapter adapter;
  late FutureOr<ResponseBody> Function(RequestOptions) handler;

  ExpenseSummaryNotifier create() {
    adapter = FakeHttpAdapter((o) => handler(o));
    return ExpenseSummaryNotifier(
      initialMonth: DateTime(2026, 9, 15),
      dio: fakeDio(adapter),
    );
  }

  setUp(() {
    handler = (o) => o.path == _monthly
        ? monthlySummary(income: 3000, expense: 1000)
        : categorySummary(
            total: 1000,
            categories: [
              {
                'categoryId': 1,
                'categoryName': '식비',
                'amount': 1000,
                'count': 2,
              },
            ],
          );
  });

  test('fetch_초기생성_선택월의월요약과카테고리별지출을조회한다', () async {
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(adapter.requestsOf('GET', _monthly).single.queryParameters, {
      'from': '2026-09',
      'to': '2026-09',
    });
    expect(adapter.requestsOf('GET', _byCategory).single.queryParameters, {
      'type': 'EXPENSE',
      'from': '2026-09-01',
      'to': '2026-09-30',
    });
    final state = notifier.state.value!;
    expect(state.totalIncome, 3000);
    expect(state.totalExpense, 1000);
    expect(state.net, 2000);
    expect(state.categoryTotal, 1000);
    expect(state.categories.single.count, 2);
    notifier.dispose();
  });

  test('fetch_늦게도착한이전월응답_최신월결과를덮어쓰지않는다', () async {
    final pending = <String, Completer<ResponseBody>>{};
    handler = (o) {
      final from = o.queryParameters['from'] as String;
      if (from.startsWith('2026-09')) {
        return o.path == _monthly ? monthlySummary() : categorySummary();
      }
      if (o.path == _byCategory) return categorySummary();
      return (pending[from] = Completer<ResponseBody>()).future;
    };
    final notifier = create();
    await until(() => notifier.state.hasValue);

    final october = notifier.fetch(DateTime(2026, 10));
    final november = notifier.fetch(DateTime(2026, 11));
    await until(() => pending.length == 2);
    pending['2026-11']!.complete(monthlySummary(income: 11));
    await november;
    pending['2026-10']!.complete(monthlySummary(income: 10));
    await october;

    expect(notifier.month, DateTime(2026, 11));
    expect(notifier.state.value!.totalIncome, 11);
    notifier.dispose();
  });

  test('refresh_현재선택월로다시조회한다', () async {
    final notifier = create();
    await until(() => !notifier.state.isLoading);
    await notifier.fetch(DateTime(2026, 3));

    await notifier.refresh();

    expect(
      adapter.requestsOf('GET', _monthly).last.queryParameters['from'],
      '2026-03',
    );
    expect(adapter.requestsOf('GET', _monthly), hasLength(3));
    notifier.dispose();
  });

  test('fetch_서버오류_에러상태가된다', () async {
    handler = (o) => errorBody(500, '서버 오류');
    final notifier = create();
    await until(() => !notifier.state.isLoading);

    expect(notifier.state.hasError, isTrue);
    notifier.dispose();
  });
}

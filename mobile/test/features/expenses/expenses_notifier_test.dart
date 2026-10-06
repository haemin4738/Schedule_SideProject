import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/expenses/expense_type.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';
import 'package:mobile/features/expenses/provider/expenses_provider.dart';

import 'expense_fake_http.dart';

const _list = '/api/v1/expenses';

void main() {
  late FakeHttpAdapter adapter;
  late FutureOr<ResponseBody> Function(RequestOptions) handler;

  ExpensesNotifier create() {
    adapter = FakeHttpAdapter((o) => handler(o));
    return ExpensesNotifier(
      initialMonth: DateTime(2026, 9, 15),
      dio: fakeDio(adapter),
    );
  }

  List<RequestOptions> listRequests() => adapter.requestsOf('GET', _list);

  setUp(() {
    handler = (_) => expensePage([expenseJson(1)]);
  });

  group('조회', () {
    test('fetch_초기생성_선택월의첫페이지를조회한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);

      final query = listRequests().single.queryParameters;
      expect(query['from'], '2026-09-01');
      expect(query['to'], '2026-09-30');
      expect(query['page'], 0);
      expect(query['size'], 20);
      expect(query.containsKey('type'), isFalse);
      expect(query.containsKey('categoryId'), isFalse);
      expect(notifier.state.value!.items.single.id, 1);
      notifier.dispose();
    });

    test('filter_다른페이지에서필터변경_page0으로초기화하고필터를쿼리에싣는다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      await notifier.goToPage(2);
      expect(listRequests().last.queryParameters['page'], 2);

      await notifier.filter(type: ExpenseType.EXPENSE, categoryId: 7);

      final query = listRequests().last.queryParameters;
      expect(query['page'], 0);
      expect(query['type'], 'EXPENSE');
      expect(query['categoryId'], 7);
      notifier.dispose();
    });

    test('changeMonth_다른페이지에서월변경_page0으로초기화하고필터는유지한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      await notifier.filter(type: ExpenseType.INCOME);
      await notifier.goToPage(3);

      await notifier.changeMonth(DateTime(2026, 2, 20));

      final query = listRequests().last.queryParameters;
      expect(notifier.month, DateTime(2026, 2));
      expect(query['from'], '2026-02-01');
      expect(query['to'], '2026-02-28');
      expect(query['page'], 0);
      expect(query['type'], 'INCOME');
      notifier.dispose();
    });

    test('fetch_늦게도착한이전응답_최신요청결과를덮어쓰지않는다', () async {
      final pending = <String, Completer<ResponseBody>>{};
      handler = (o) {
        final from = o.queryParameters['from'] as String;
        if (from == '2026-09-01') return expensePage([expenseJson(1)]);
        return (pending[from] = Completer<ResponseBody>()).future;
      };
      final racing = create();
      await until(() => racing.state.hasValue);

      final october = racing.changeMonth(DateTime(2026, 10));
      final november = racing.changeMonth(DateTime(2026, 11));
      await until(() => pending.length == 2);
      pending['2026-11-01']!.complete(expensePage([expenseJson(11)]));
      await november;
      pending['2026-10-01']!.complete(expensePage([expenseJson(10)]));
      await october;

      expect(racing.month, DateTime(2026, 11));
      expect(racing.state.value!.items.single.id, 11);
      racing.dispose();
    });

    test('fetch_서버오류_에러상태가된다', () async {
      handler = (_) => errorBody(400, '조회 시작일은 종료일보다 늦을 수 없습니다.');
      final notifier = create();
      await until(() => !notifier.state.isLoading);

      expect(notifier.state.hasError, isTrue);
      expect(
        expenseErrorMessage(notifier.state.error!),
        '조회 시작일은 종료일보다 늦을 수 없습니다.',
      );
      notifier.dispose();
    });
  });

  group('빈 페이지 이동 (M1)', () {
    test('fetch_마지막페이지가비어있으면_존재하는마지막페이지로다시조회한다', () async {
      handler = (o) {
        final page = o.queryParameters['page'] as int;
        if (page == 2) {
          return expensePage([], page: 2, total: 40, totalPages: 2);
        }
        return expensePage(
          [expenseJson(page)],
          page: page,
          total: 40,
          totalPages: 2,
        );
      };
      final notifier = create();
      await until(() => !notifier.state.isLoading);

      await notifier.goToPage(2);

      expect(listRequests().map((r) => r.queryParameters['page']), [0, 2, 1]);
      expect(notifier.state.value!.page, 1);
      expect(notifier.state.value!.items, isNotEmpty);
      notifier.dispose();
    });

    test('fetch_전체가비면_page0까지내려간다', () async {
      handler = (o) => expensePage(
        [],
        page: o.queryParameters['page'] as int,
        totalPages: 0,
      );
      final notifier = create();
      await until(() => !notifier.state.isLoading);

      await notifier.goToPage(3);

      // totalPages 0 → max(0, min(2, -1)) = 0
      expect(listRequests().map((r) => r.queryParameters['page']), [0, 3, 0]);
      expect(notifier.state.value!.page, 0);
      expect(notifier.state.value!.items, isEmpty);
      notifier.dispose();
    });

    test('fetch_첫페이지가비어있으면_다시조회하지않는다', () async {
      handler = (_) => expensePage([]);
      final notifier = create();
      await until(() => !notifier.state.isLoading);

      expect(listRequests(), hasLength(1));
      expect(notifier.state.value!.items, isEmpty);
      notifier.dispose();
    });
  });

  group('삭제', () {
    test('delete_마지막페이지의마지막항목_삭제후이전페이지로이동한다', () async {
      var deleted = false;
      handler = (o) {
        if (o.method == 'DELETE') {
          deleted = true;
          return ok(null);
        }
        final page = o.queryParameters['page'] as int;
        if (page == 1) {
          return deleted
              ? expensePage([], page: 1, total: 20, totalPages: 1)
              : expensePage(
                  [expenseJson(21)],
                  page: 1,
                  total: 21,
                  totalPages: 2,
                );
        }
        return expensePage(
          [expenseJson(1)],
          page: 0,
          total: deleted ? 20 : 21,
          totalPages: deleted ? 1 : 2,
        );
      };
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      await notifier.goToPage(1);

      await notifier.delete(21);

      expect(adapter.requestsOf('DELETE', '$_list/21'), hasLength(1));
      expect(listRequests().map((r) => r.queryParameters['page']), [
        0,
        1,
        1,
        0,
      ]);
      expect(notifier.state.value!.page, 0);
      expect(notifier.state.value!.totalPages, 1);
      notifier.dispose();
    });

    test('delete_성공_현재페이지를다시조회한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) =>
          o.method == 'DELETE' ? ok(null) : expensePage([expenseJson(2)]);

      await notifier.delete(1);

      expect(adapter.requestsOf('DELETE', '$_list/1'), hasLength(1));
      expect(listRequests(), hasLength(2));
      expect(notifier.state.value!.items.map((e) => e.id), [2]);
      notifier.dispose();
    });

    test('delete_서버오류_오류를전달하고목록은다시조회한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => o.method == 'DELETE'
          ? errorBody(404, '내역을 찾을 수 없습니다.')
          : expensePage([expenseJson(1)]);

      await expectLater(notifier.delete(1), throwsA(isA<DioException>()));

      expect(listRequests(), hasLength(2));
      expect(notifier.state.value!.items.single.id, 1);
      notifier.dispose();
    });
  });

  group('저장', () {
    test('create_description과memo를요청바디에싣고목록을다시조회한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => o.method == 'POST'
          ? ok(expenseJson(5))
          : expensePage([expenseJson(5)]);

      await notifier.create(
        type: ExpenseType.INCOME,
        categoryId: 3,
        amount: 5000,
        transactionDate: '2026-09-03',
        description: '용돈',
        memo: '  줄바꿈\n포함 메모  ',
      );

      expect(bodyOf(adapter.requestsOf('POST', _list).single), {
        'type': 'INCOME',
        'categoryId': 3,
        'amount': 5000,
        'transactionDate': '2026-09-03',
        'description': '용돈',
        'memo': '  줄바꿈\n포함 메모  ',
        'paymentMethod': null,
      });
      expect(listRequests(), hasLength(2));
      notifier.dispose();
    });

    test('update_memo를요청바디에싣고현재페이지를다시조회한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => o.method == 'PUT'
          ? ok(expenseJson(5))
          : expensePage(
              [expenseJson(5)],
              page: o.queryParameters['page'] as int,
              totalPages: 2,
            );
      await notifier.goToPage(1);

      await notifier.update(
        5,
        type: ExpenseType.EXPENSE,
        categoryId: 1,
        amount: 800,
        transactionDate: '2026-09-04',
        memo: '메모',
      );

      final body = bodyOf(adapter.requestsOf('PUT', '$_list/5').single);
      expect(body['memo'], '메모');
      expect(body['description'], isNull);
      expect(listRequests().last.queryParameters['page'], 1);
      notifier.dispose();
    });

    test('update_지출유지_전달받은paymentMethod를그대로보낸다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => o.method == 'PUT'
          ? ok(expenseJson(5))
          : expensePage([expenseJson(5)]);

      await notifier.update(
        5,
        type: ExpenseType.EXPENSE,
        categoryId: 1,
        amount: 800,
        transactionDate: '2026-09-04',
        paymentMethod: 'CREDIT_CARD',
      );

      final body = bodyOf(adapter.requestsOf('PUT', '$_list/5').single);
      expect(body['paymentMethod'], 'CREDIT_CARD');
      notifier.dispose();
    });

    test('update_수입으로전환_paymentMethod를null로보낸다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => o.method == 'PUT'
          ? ok(expenseJson(5, type: 'INCOME'))
          : expensePage([expenseJson(5, type: 'INCOME')]);

      await notifier.update(
        5,
        type: ExpenseType.INCOME,
        categoryId: 3,
        amount: 800,
        transactionDate: '2026-09-04',
        paymentMethod: 'CREDIT_CARD',
      );

      final body = bodyOf(adapter.requestsOf('PUT', '$_list/5').single);
      expect(body.containsKey('paymentMethod'), isTrue);
      expect(body['paymentMethod'], isNull);
      notifier.dispose();
    });

    test('getDetail_paymentMethod가있거나없음_문자열또는null로파싱한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (o) => o.path == '$_list/5'
          ? ok(expenseJson(5, paymentMethod: 'CREDIT_CARD'))
          : ok(expenseJson(6)..remove('paymentMethod'));

      final withValue = await notifier.getDetail(5);
      final withoutKey = await notifier.getDetail(6);

      expect(withValue.paymentMethod, 'CREDIT_CARD');
      expect(withoutKey.paymentMethod, isNull);
      notifier.dispose();
    });

    test('getDetail_단건조회결과의memo를파싱한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      handler = (_) => ok(expenseJson(5, memo: '상세 메모'));

      final detail = await notifier.getDetail(5);

      expect(adapter.requestsOf('GET', '$_list/5'), hasLength(1));
      expect(detail.memo, '상세 메모');
      notifier.dispose();
    });
  });

  group('카테고리 변경 후 재조회', () {
    const categories = [
      ExpenseCategory(id: 1, type: ExpenseType.EXPENSE, name: '식비'),
    ];

    test('refreshAfterCategoryChange_선택카테고리가삭제됨_필터를해제하고page0부터조회한다', () async {
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      await notifier.filter(type: ExpenseType.EXPENSE, categoryId: 9);
      await notifier.goToPage(1);

      await notifier.refreshAfterCategoryChange(categories);

      final query = listRequests().last.queryParameters;
      expect(notifier.categoryIdFilter, isNull);
      expect(notifier.typeFilter, ExpenseType.EXPENSE);
      expect(query['page'], 0);
      expect(query.containsKey('categoryId'), isFalse);
      notifier.dispose();
    });

    test('refreshAfterCategoryChange_선택카테고리가남아있음_필터와페이지를유지한다', () async {
      handler = (o) => expensePage(
        [expenseJson(1)],
        page: o.queryParameters['page'] as int,
        totalPages: 3,
      );
      final notifier = create();
      await until(() => !notifier.state.isLoading);
      await notifier.filter(categoryId: 1);
      await notifier.goToPage(2);

      await notifier.refreshAfterCategoryChange(categories);

      final query = listRequests().last.queryParameters;
      expect(notifier.categoryIdFilter, 1);
      expect(query['page'], 2);
      expect(query['categoryId'], 1);
      notifier.dispose();
    });
  });

  group('provider 수명 (M4)', () {
    test('expensesProvider_구독이모두해제되면폐기되고_재진입시이번달로시작한다', () async {
      handler = (_) => expensePage([]);
      final container = ProviderContainer(
        overrides: [
          expensesProvider.overrideWith(
            (ref) => ExpensesNotifier(
              dio: fakeDio(FakeHttpAdapter((o) => handler(o))),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final sub = container.listen(expensesProvider, (_, _) {});
      final first = container.read(expensesProvider.notifier);
      await first.changeMonth(DateTime(2020, 1));
      sub.close();
      await container.pump();

      container.listen(expensesProvider, (_, _) {});
      final second = container.read(expensesProvider.notifier);
      final now = DateTime.now();
      expect(identical(first, second), isFalse);
      expect(first.mounted, isFalse);
      expect(second.month, DateTime(now.year, now.month));
    });
  });
}

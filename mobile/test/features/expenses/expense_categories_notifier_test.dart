import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/expenses/expense_type.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';

import 'expense_fake_http.dart';

const _path = '/api/v1/expense-categories';

void main() {
  late FakeHttpAdapter adapter;
  late FutureOr<ResponseBody> Function(RequestOptions) handler;
  late List<Map<String, Object?>> serverCategories;
  late List<AsyncValue<List<ExpenseCategory>>> states;

  Future<ExpenseCategoriesNotifier> createLoaded() async {
    adapter = FakeHttpAdapter((o) => handler(o));
    final notifier = ExpenseCategoriesNotifier(dio: fakeDio(adapter));
    await until(() => !notifier.state.isLoading);
    states = [];
    notifier.addListener(states.add, fireImmediately: false);
    return notifier;
  }

  List<String> names(ExpenseCategoriesNotifier notifier) =>
      notifier.state.value!.map((c) => c.name).toList();

  setUp(() {
    serverCategories = [
      categoryJson(1, '식비'),
      categoryJson(2, '급여', type: 'INCOME'),
    ];
    handler = (o) {
      switch (o.method) {
        case 'POST':
          final body = bodyOf(o);
          serverCategories.add(
            categoryJson(
              3,
              body['name'] as String,
              type: body['type'] as String,
            ),
          );
          return ok(serverCategories.last);
        case 'PUT':
          final body = bodyOf(o);
          serverCategories[0] = categoryJson(1, body['name'] as String);
          return ok(serverCategories[0]);
        case 'DELETE':
          serverCategories.removeWhere((c) => '$_path/${c['id']}' == o.path);
          return ok(null);
        default:
          return ok(List.of(serverCategories));
      }
    };
  });

  test('fetch_초기생성_전체카테고리를조회한다', () async {
    final notifier = await createLoaded();

    expect(adapter.requestsOf('GET', _path), hasLength(1));
    expect(notifier.state.value!.map((c) => c.type), [
      ExpenseType.EXPENSE,
      ExpenseType.INCOME,
    ]);
    expect(names(notifier), ['식비', '급여']);
    notifier.dispose();
  });

  test('create_요청후다시조회하고_로딩상태를거치지않고교체한다', () async {
    final notifier = await createLoaded();

    await notifier.create(type: ExpenseType.EXPENSE, name: '교통');

    expect(bodyOf(adapter.requestsOf('POST', _path).single), {
      'type': 'EXPENSE',
      'name': '교통',
    });
    expect(adapter.requestsOf('GET', _path), hasLength(2));
    expect(names(notifier), ['식비', '급여', '교통']);
    expect(states.any((s) => s.isLoading), isFalse);
    notifier.dispose();
  });

  test('rename_요청후다시조회하고_로딩상태를거치지않고교체한다', () async {
    final notifier = await createLoaded();

    await notifier.rename(1, '외식');

    expect(bodyOf(adapter.requestsOf('PUT', '$_path/1').single), {
      'name': '외식',
    });
    expect(adapter.requestsOf('GET', _path), hasLength(2));
    expect(names(notifier), ['외식', '급여']);
    expect(states.any((s) => s.isLoading), isFalse);
    notifier.dispose();
  });

  test('delete_요청후다시조회하고_로딩상태를거치지않고교체한다', () async {
    final notifier = await createLoaded();

    await notifier.delete(1);

    expect(adapter.requestsOf('DELETE', '$_path/1'), hasLength(1));
    expect(adapter.requestsOf('GET', _path), hasLength(2));
    expect(names(notifier), ['급여']);
    expect(states.any((s) => s.isLoading), isFalse);
    notifier.dispose();
  });

  test('delete_사용중인카테고리409_오류를전달하고다시조회하지않는다', () async {
    final notifier = await createLoaded();
    handler = (o) => errorBody(409, '사용 중인 카테고리는 삭제할 수 없습니다.');

    await expectLater(notifier.delete(1), throwsA(isA<DioException>()));

    expect(adapter.requestsOf('GET', _path), hasLength(1));
    expect(names(notifier), ['식비', '급여']);
    notifier.dispose();
  });

  test('fetch_최초조회실패_에러상태가된다', () async {
    handler = (_) => errorBody(500, '서버 오류');
    adapter = FakeHttpAdapter((o) => handler(o));
    final notifier = ExpenseCategoriesNotifier(dio: fakeDio(adapter));
    await until(() => !notifier.state.isLoading);

    expect(notifier.state.hasError, isTrue);
    notifier.dispose();
  });

  test('fetch_재조회실패_기존목록을유지한채에러를표시한다', () async {
    final notifier = await createLoaded();
    handler = (_) => errorBody(500, '서버 오류');

    await notifier.fetch();

    expect(notifier.state.hasError, isTrue);
    expect(names(notifier), ['식비', '급여']);
    notifier.dispose();
  });
}

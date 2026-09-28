import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/expenses/presentation/expense_categories_page.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';

import 'expense_fakes.dart';

Widget _wrap(FakeExpenseCategoriesNotifier notifier) {
  return ProviderScope(
    overrides: [
      expenseCategoriesProvider.overrideWith((ref) => notifier),
    ],
    child: const MaterialApp(home: ExpenseCategoriesPage()),
  );
}

void main() {
  testWidgets('로딩 상태일 때 로딩 인디케이터를 보여준다', (tester) async {
    await tester.pumpWidget(
      _wrap(FakeExpenseCategoriesNotifier(const AsyncValue.loading())),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('유형 탭별로 카테고리를 나눠 보여준다', (tester) async {
    await tester.pumpWidget(
      _wrap(FakeExpenseCategoriesNotifier(const AsyncValue.data(sampleCategories))),
    );

    expect(find.text('식비'), findsOneWidget);
    expect(find.text('교통'), findsOneWidget);
    expect(find.text('급여'), findsNothing);

    await tester.tap(find.widgetWithText(Tab, '수입'));
    await tester.pumpAndSettle();

    expect(find.text('급여'), findsOneWidget);
    expect(find.text('식비'), findsNothing);
  });

  testWidgets('카테고리 삭제가 409면 서버 메시지를 그대로 보여준다', (tester) async {
    const message = '해당 카테고리를 사용하는 내역이 있어 삭제할 수 없습니다.';
    final notifier = FakeExpenseCategoriesNotifier(
      const AsyncValue.data(sampleCategories),
      deleteError: serverError(409, message),
    );
    await tester.pumpWidget(_wrap(notifier));

    await tester.tap(find.byTooltip('삭제').first);
    await tester.pumpAndSettle();
    expect(find.text("'식비' 카테고리를 삭제할까요?"), findsOneWidget);

    await tester.tap(find.widgetWithText(TextButton, '삭제'));
    await tester.pumpAndSettle();

    expect(find.text(message), findsOneWidget);
    expect(notifier.deleted, isEmpty);
  });

  testWidgets('삭제 확인을 취소하면 삭제하지 않는다', (tester) async {
    final notifier =
        FakeExpenseCategoriesNotifier(const AsyncValue.data(sampleCategories));
    await tester.pumpWidget(_wrap(notifier));

    await tester.tap(find.byTooltip('삭제').first);
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(TextButton, '취소'));
    await tester.pumpAndSettle();

    expect(notifier.deleted, isEmpty);
  });

  testWidgets('카테고리 추가 시 이름이 비어 있으면 검증 메시지를 보여준다', (tester) async {
    await tester.pumpWidget(
      _wrap(FakeExpenseCategoriesNotifier(const AsyncValue.data(sampleCategories))),
    );

    await tester.tap(find.text('카테고리 추가'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(TextButton, '저장'));
    await tester.pumpAndSettle();

    expect(find.text('이름을 입력하세요.'), findsOneWidget);
  });
}

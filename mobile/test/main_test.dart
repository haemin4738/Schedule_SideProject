import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/auth/provider/auth_provider.dart';
import 'package:mobile/main.dart';

import 'features/expenses/expense_fake_http.dart';

/// 세션 종료 안내를 직접 발생시키기 위한 테스트용 notifier (네트워크 호출 없음)
class _TestAuthNotifier extends AuthNotifier {
  _TestAuthNotifier() : super(dio: fakeDio(FakeHttpAdapter((_) => ok(null))));

  void emitNotice(String notice) => state = AuthState(sessionNotice: notice);
}

void main() {
  late _TestAuthNotifier notifier;

  setUp(() {
    // 로그아웃 상태로 시작 → 로그인 화면(네트워크 호출 없음)이 표시된다
    FlutterSecureStorage.setMockInitialValues({});
  });

  Future<void> pumpApp(WidgetTester tester) async {
    notifier = _TestAuthNotifier();
    await tester.pumpWidget(ProviderScope(
      overrides: [authProvider.overrideWith((_) => notifier)],
      child: const LifelogApp(),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('sessionNotice가 생기면 전역 SnackBar로 안내하고 상태의 안내는 비운다', (tester) async {
    await pumpApp(tester);
    expect(find.byType(SnackBar), findsNothing);

    notifier.emitNotice(sessionRevokedNotice);
    await tester.pumpAndSettle();

    expect(find.text(sessionRevokedNotice), findsOneWidget);
    expect(find.widgetWithText(SnackBarAction, '확인'), findsOneWidget);
    expect(notifier.state.sessionNotice, isNull);
  });

  testWidgets('안내 SnackBar는 확인을 누르면 닫힌다', (tester) async {
    await pumpApp(tester);
    notifier.emitNotice(sessionRevokedNotice);
    await tester.pumpAndSettle();

    await tester.tap(find.text('확인'));
    await tester.pumpAndSettle();

    expect(find.text(sessionRevokedNotice), findsNothing);
  });

  testWidgets('안내 SnackBar는 확인을 누르기 전까지 자동으로 닫히지 않는다', (tester) async {
    await pumpApp(tester);
    notifier.emitNotice(sessionRevokedNotice);
    await tester.pumpAndSettle();

    await tester.pump(const Duration(minutes: 1));
    await tester.pumpAndSettle();
    expect(find.text(sessionRevokedNotice), findsOneWidget);
  });
}

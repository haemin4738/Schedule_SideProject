import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/dio_client.dart';
import 'package:mobile/features/auth/provider/auth_provider.dart';

import '../expenses/expense_fake_http.dart';

void main() {
  const storage = FlutterSecureStorage();
  late FakeHttpAdapter adapter;
  late AuthNotifier notifier;

  AuthNotifier createNotifier() => AuthNotifier(storage: storage, dio: fakeDio(adapter));

  // 생성자의 _init()(storage 읽기)이 끝날 때까지 기다린다
  Future<void> settle() => Future<void>.delayed(Duration.zero);

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'accessToken': 'access', 'refreshToken': 'refresh'});
    adapter = FakeHttpAdapter((_) => ok(null));
  });

  tearDown(() {
    if (notifier.mounted) notifier.dispose();
  });

  test('생성 시 저장된 access 토큰으로 로그인 상태를 복원한다', () async {
    notifier = createNotifier();
    await settle();

    expect(notifier.state.accessToken, 'access');
    expect(notifier.state.sessionNotice, isNull);
  });

  test('logout은 storage를 비우고 상태를 초기화한 뒤 서버에 refresh 토큰 폐기를 요청한다', () async {
    notifier = createNotifier();
    await settle();

    await notifier.logout();

    expect(await storage.read(key: 'accessToken'), isNull);
    expect(await storage.read(key: 'refreshToken'), isNull);
    expect(notifier.state.accessToken, isNull);
    final requests = adapter.requestsOf('POST', '/api/v1/auth/logout');
    expect(requests, hasLength(1));
    expect(bodyOf(requests.single), {'refreshToken': 'refresh'});
    // 로그아웃 요청은 인증이 필요 없으므로 Authorization 헤더를 붙이지 않는다
    expect(requests.single.headers.containsKey('Authorization'), isFalse);
  });

  test('logout은 서버 호출이 실패해도 로컬 토큰을 비우고 예외를 던지지 않는다', () async {
    adapter = FakeHttpAdapter((_) => errorBody(503, '일시적으로 사용할 수 없습니다.'));
    notifier = createNotifier();
    await settle();

    await notifier.logout();

    expect(adapter.requests, hasLength(1));
    expect(await storage.read(key: 'accessToken'), isNull);
    expect(await storage.read(key: 'refreshToken'), isNull);
    expect(notifier.state.accessToken, isNull);
  });

  test('logout은 네트워크 오류가 나도 로컬 토큰을 비운다', () async {
    adapter = FakeHttpAdapter((o) => throw DioException.connectionError(requestOptions: o, reason: 'offline'));
    notifier = createNotifier();
    await settle();

    await notifier.logout();

    expect(await storage.read(key: 'refreshToken'), isNull);
    expect(notifier.state.accessToken, isNull);
  });

  test('logout은 저장된 refresh 토큰이 없으면 서버를 호출하지 않는다', () async {
    FlutterSecureStorage.setMockInitialValues({'accessToken': 'access'});
    notifier = createNotifier();
    await settle();

    await notifier.logout();

    expect(adapter.requests, isEmpty);
    expect(await storage.read(key: 'accessToken'), isNull);
    expect(notifier.state.accessToken, isNull);
  });

  test('login은 두 토큰을 저장하고 로그인 상태로 바꾼다', () async {
    FlutterSecureStorage.setMockInitialValues({});
    notifier = createNotifier();
    await settle();

    await notifier.login('new-access', 'new-refresh');

    expect(await storage.read(key: 'accessToken'), 'new-access');
    expect(await storage.read(key: 'refreshToken'), 'new-refresh');
    expect(notifier.state.accessToken, 'new-access');
  });

  test('세션이 보안상 폐기되면 로그아웃 상태로 바꾸고 안내 문구를 설정한다', () async {
    notifier = createNotifier();
    await settle();

    onSessionExpired!(SessionEndReason.revokedForSecurity);

    expect(notifier.state.accessToken, isNull);
    expect(notifier.state.sessionNotice, sessionRevokedNotice);
  });

  test('세션이 일반 만료되면 안내 문구 없이 로그아웃 상태로 바꾼다', () async {
    notifier = createNotifier();
    await settle();

    onSessionExpired!(SessionEndReason.expired);

    expect(notifier.state.accessToken, isNull);
    expect(notifier.state.sessionNotice, isNull);
  });

  test('이미 로그아웃된 뒤 중복 호출된 세션 만료 콜백은 안내를 다시 설정하지 않는다', () async {
    notifier = createNotifier();
    await settle();
    onSessionExpired!(SessionEndReason.revokedForSecurity);
    notifier.clearSessionNotice();

    onSessionExpired!(SessionEndReason.revokedForSecurity);

    expect(notifier.state.sessionNotice, isNull);
  });

  test('clearSessionNotice는 안내 문구를 비운다', () async {
    notifier = createNotifier();
    await settle();
    onSessionExpired!(SessionEndReason.revokedForSecurity);

    notifier.clearSessionNotice();

    expect(notifier.state.sessionNotice, isNull);
    expect(notifier.state.accessToken, isNull);
  });

  test('dispose하면 자신이 등록한 세션 만료 콜백을 해제한다', () async {
    notifier = createNotifier();
    await settle();

    notifier.dispose();

    expect(onSessionExpired, isNull);
  });

  test('refresh가 SESSION_REVOKED로 실패하면 dio_client와 연결되어 안내 문구가 설정된다', () async {
    notifier = createNotifier();
    await settle();
    final refreshDio = fakeDio(FakeHttpAdapter((_) => jsonBody(401, {
          'success': false,
          'data': null,
          'error': sessionRevokedNotice,
          'code': 'SESSION_REVOKED',
        })));
    final api = createDio(baseUrl: 'http://test', storage: storage, refreshClient: refreshDio)
      ..httpClientAdapter = FakeHttpAdapter((_) => errorBody(401, '인증이 필요합니다.'));

    await expectLater(api.get('/api/v1/events'), throwsA(isA<DioException>()));

    expect(notifier.state.accessToken, isNull);
    expect(notifier.state.sessionNotice, sessionRevokedNotice);
    expect(await storage.read(key: 'refreshToken'), isNull);
    resetRefreshStateForTest();
  });
}

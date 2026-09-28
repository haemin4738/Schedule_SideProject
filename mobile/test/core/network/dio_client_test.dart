import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/dio_client.dart';

/// 요청마다 handler를 호출해 응답을 만드는 가짜 어댑터 (실제 네트워크 호출 없음)
class _FakeAdapter implements HttpClientAdapter {
  _FakeAdapter(this.handler);

  final ResponseBody Function(RequestOptions options) handler;
  final List<RequestOptions> requests = [];

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody _json(int status, Object body) => ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );

ResponseBody _unauthorized() => _json(401, {'success': false, 'data': null, 'error': '인증이 필요합니다.'});

ResponseBody _refreshed() => _json(200, {
      'success': true,
      'data': {'accessToken': 'new-access', 'refreshToken': 'new-refresh'},
      'error': null,
    });

void main() {
  const storage = FlutterSecureStorage();
  late Dio refreshDio;
  late _FakeAdapter refreshAdapter;
  var sessionExpired = false;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'accessToken': 'expired-access', 'refreshToken': 'valid-refresh'});
    sessionExpired = false;
    onSessionExpired = () => sessionExpired = true;
    refreshAdapter = _FakeAdapter((_) => _refreshed());
    refreshDio = Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = refreshAdapter;
  });

  tearDown(() => onSessionExpired = null);

  Dio dioWith(_FakeAdapter adapter) =>
      createDio(baseUrl: 'http://test', storage: storage, refreshClient: refreshDio)..httpClientAdapter = adapter;

  test('401이면 refresh 후 새 토큰으로 재시도하고 두 토큰을 모두 저장한다', () async {
    final api = _FakeAdapter((o) => o.headers['Authorization'] == 'Bearer new-access'
        ? _json(200, {'success': true, 'data': 'events', 'error': null})
        : _unauthorized());

    final res = await dioWith(api).get('/api/v1/events');

    expect(res.data['data'], 'events');
    expect(refreshAdapter.requests, hasLength(1));
    expect(jsonDecode(jsonEncode(refreshAdapter.requests.single.data)), {'refreshToken': 'valid-refresh'});
    expect(await storage.read(key: 'accessToken'), 'new-access');
    expect(await storage.read(key: 'refreshToken'), 'new-refresh');
    expect(sessionExpired, isFalse);
  });

  test('동시에 여러 요청이 401을 받아도 refresh는 한 번만 호출한다', () async {
    final api = _FakeAdapter((o) =>
        o.headers['Authorization'] == 'Bearer new-access' ? _json(200, {'success': true}) : _unauthorized());
    final dio = dioWith(api);

    await Future.wait([dio.get('/api/v1/events'), dio.get('/api/v1/expenses')]);

    expect(refreshAdapter.requests, hasLength(1));
  });

  test('로그인 요청의 401(잘못된 비밀번호)은 refresh를 시도하지 않는다', () async {
    final api = _FakeAdapter((_) => _unauthorized());

    await expectLater(
      dioWith(api).post('/api/v1/auth/login', data: {}),
      throwsA(isA<DioException>().having((e) => e.response?.statusCode, 'status', 401)),
    );
    expect(refreshAdapter.requests, isEmpty);
    expect(sessionExpired, isFalse);
  });

  test('refresh도 실패하면 토큰을 삭제하고 세션 만료를 알린다', () async {
    refreshAdapter = _FakeAdapter((_) => _unauthorized());
    refreshDio.httpClientAdapter = refreshAdapter;
    final api = _FakeAdapter((_) => _unauthorized());

    await expectLater(
      dioWith(api).get('/api/v1/events'),
      throwsA(isA<DioException>().having((e) => e.response?.statusCode, 'status', 401)),
    );
    expect(await storage.read(key: 'accessToken'), isNull);
    expect(await storage.read(key: 'refreshToken'), isNull);
    expect(sessionExpired, isTrue);
  });

  test('403(권한 없음)은 refresh 없이 그대로 에러를 반환한다', () async {
    final api = _FakeAdapter((_) => _json(403, {'success': false, 'data': null, 'error': '접근 권한이 없습니다.'}));

    await expectLater(
      dioWith(api).get('/api/v1/events/1'),
      throwsA(isA<DioException>().having((e) => e.response?.statusCode, 'status', 403)),
    );
    expect(refreshAdapter.requests, isEmpty);
  });
}

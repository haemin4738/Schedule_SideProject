import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// API 서버 기본 주소. `--dart-define=API_BASE_URL=...` 로 바꿀 수 있고, 기본값은 Android 에뮬레이터의 호스트 주소다.
const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080');
// 이 경로들의 401(잘못된 비밀번호, 만료된 refresh 토큰 등)은 재발급 대상이 아니다
const _authPaths = {'/api/v1/auth/login', '/api/v1/auth/signup', '/api/v1/auth/refresh', '/api/v1/auth/logout'};
const _retriedKey = '_retried';
// 서버가 재사용 탐지 등으로 사용자의 모든 세션을 폐기했을 때 refresh 401 응답에 담는 code
const _sessionRevokedCode = 'SESSION_REVOKED';

/// 세션이 끝난 이유. 사용자에게 알림을 보여줄지 결정하는 데 쓴다.
enum SessionEndReason {
  /// refresh 토큰 만료/무효 등 일반적인 만료 — 별도 알림 없이 로그인 화면으로 이동
  expired,

  /// 토큰 재사용 탐지 등으로 서버가 모든 기기의 세션을 폐기함 — 보안 알림을 보여준다
  revokedForSecurity,
}

/// refresh 토큰까지 만료/무효라 재로그인이 필요할 때 호출된다. AuthNotifier가 등록한다.
void Function(SessionEndReason reason)? onSessionExpired;

// 여러 Dio 인스턴스(화면별 provider)가 동시에 401을 받아도 refresh는 앱 전체에서 한 번만 호출한다.
// 결과(새 토큰)는 처음 refresh를 시작한 인스턴스의 storage에 저장되며, 앱의 모든 인스턴스는 같은 storage를 쓴다.
Future<String>? _refreshing;

@visibleForTesting
void resetRefreshStateForTest() => _refreshing = null;

Dio createDio({
  String baseUrl = apiBaseUrl,
  FlutterSecureStorage storage = const FlutterSecureStorage(),
  Dio? refreshClient,
}) {
  final dio = Dio(BaseOptions(baseUrl: baseUrl, contentType: 'application/json'));
  final refreshDio = refreshClient ?? Dio(BaseOptions(baseUrl: baseUrl));

  Future<String> refreshAccessToken() async {
    final refreshToken = await storage.read(key: 'refreshToken');
    if (refreshToken == null) throw StateError('refresh token 없음');
    final res = await refreshDio.post('/api/v1/auth/refresh', data: {'refreshToken': refreshToken});
    final data = res.data['data'];
    final accessToken = data['accessToken'] as String;
    // 백엔드가 refresh 토큰도 새로 발급하므로 둘 다 저장한다
    await storage.write(key: 'accessToken', value: accessToken);
    await storage.write(key: 'refreshToken', value: data['refreshToken'] as String);
    return accessToken;
  }

  dio.interceptors.add(InterceptorsWrapper(
    onRequest: (options, handler) async {
      final token = await storage.read(key: 'accessToken');
      if (token != null) options.headers['Authorization'] = 'Bearer $token';
      handler.next(options);
    },
    onError: (error, handler) async {
      final options = error.requestOptions;
      final isAuthRequest = _authPaths.contains(Uri.parse(options.path).path);
      if (error.response?.statusCode != 401 || isAuthRequest || options.extra[_retriedKey] == true) {
        return handler.next(error);
      }

      final String accessToken;
      try {
        _refreshing ??= refreshAccessToken().whenComplete(() => _refreshing = null);
        accessToken = await _refreshing!;
      } on DioException catch (refreshError) {
        // refresh 토큰이 만료/무효일 때만 세션을 만료한다.
        // 네트워크 오류·5xx 같은 일시 장애나 409(REFRESH_IN_PROGRESS, 동시 갱신)로는 로그아웃하지 않는다
        final status = refreshError.response?.statusCode;
        if (status == 401 && _errorCode(refreshError.response) == _sessionRevokedCode) {
          await _expireSession(storage, SessionEndReason.revokedForSecurity);
        } else if (status == 401 || status == 403) {
          await _expireSession(storage, SessionEndReason.expired);
        }
        return handler.next(error);
      } catch (_) {
        // 저장된 refresh 토큰 없음 → 재로그인 필요
        await _expireSession(storage, SessionEndReason.expired);
        return handler.next(error);
      }

      options.extra[_retriedKey] = true;
      options.headers['Authorization'] = 'Bearer $accessToken';
      try {
        handler.resolve(await dio.fetch(options));
      } on DioException catch (retryError) {
        handler.next(retryError);
      }
    },
  ));

  return dio;
}

String? _errorCode(Response<dynamic>? response) {
  final body = response?.data;
  if (body is Map && body['code'] is String) return body['code'] as String;
  return null;
}

Future<void> _expireSession(FlutterSecureStorage storage, SessionEndReason reason) async {
  await storage.delete(key: 'accessToken');
  await storage.delete(key: 'refreshToken');
  onSessionExpired?.call(reason);
}

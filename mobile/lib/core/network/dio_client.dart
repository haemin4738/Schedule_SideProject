import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

const _baseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080');
// 이 경로들의 401(잘못된 비밀번호, 만료된 refresh 토큰 등)은 재발급 대상이 아니다
const _authPaths = {'/api/v1/auth/login', '/api/v1/auth/signup', '/api/v1/auth/refresh', '/api/v1/auth/logout'};
const _retriedKey = '_retried';

/// refresh 토큰까지 만료/무효라 재로그인이 필요할 때 호출된다. AuthNotifier가 등록한다.
void Function()? onSessionExpired;

// 여러 Dio 인스턴스(화면별 provider)가 동시에 401을 받아도 refresh는 앱 전체에서 한 번만 호출한다
Future<String>? _refreshing;

Dio createDio({
  String baseUrl = _baseUrl,
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
        // refresh 토큰이 만료/무효일 때만 세션을 만료한다. 네트워크 오류·5xx 같은 일시 장애로는 로그아웃하지 않는다
        final status = refreshError.response?.statusCode;
        if (status == 401 || status == 403) await _expireSession(storage);
        return handler.next(error);
      } catch (_) {
        // 저장된 refresh 토큰 없음 → 재로그인 필요
        await _expireSession(storage);
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

Future<void> _expireSession(FlutterSecureStorage storage) async {
  await storage.delete(key: 'accessToken');
  await storage.delete(key: 'refreshToken');
  onSessionExpired?.call();
}

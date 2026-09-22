import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

const _baseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080');
const _storage = FlutterSecureStorage();

Dio createDio() {
  final dio = Dio(BaseOptions(baseUrl: _baseUrl, contentType: 'application/json'));

  dio.interceptors.add(InterceptorsWrapper(
    onRequest: (options, handler) async {
      final token = await _storage.read(key: 'accessToken');
      if (token != null) options.headers['Authorization'] = 'Bearer $token';
      handler.next(options);
    },
    onError: (error, handler) async {
      if (error.response?.statusCode == 401) {
        final refreshToken = await _storage.read(key: 'refreshToken');
        if (refreshToken == null) return handler.next(error);
        try {
          final res = await Dio(BaseOptions(baseUrl: _baseUrl)).post(
            '/api/v1/auth/refresh',
            data: {'refreshToken': refreshToken},
          );
          final newToken = res.data['data']['accessToken'] as String;
          await _storage.write(key: 'accessToken', value: newToken);
          error.requestOptions.headers['Authorization'] = 'Bearer $newToken';
          handler.resolve(await dio.fetch(error.requestOptions));
        } catch (_) {
          handler.next(error);
        }
        return;
      }
      handler.next(error);
    },
  ));

  return dio;
}

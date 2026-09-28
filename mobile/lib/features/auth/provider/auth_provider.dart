import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:mobile/core/network/dio_client.dart';

class AuthState {
  final String? accessToken;
  const AuthState({this.accessToken});
}

class AuthNotifier extends StateNotifier<AuthState> {
  final _storage = const FlutterSecureStorage();

  AuthNotifier() : super(const AuthState()) {
    // refresh 실패로 세션이 만료되면 로그인 화면으로 돌아가도록 상태를 비운다 (토큰은 dio_client가 삭제)
    onSessionExpired = () => state = const AuthState();
    _init();
  }

  Future<void> _init() async {
    final token = await _storage.read(key: 'accessToken');
    state = AuthState(accessToken: token);
  }

  Future<void> login(String accessToken, String refreshToken) async {
    await _storage.write(key: 'accessToken', value: accessToken);
    await _storage.write(key: 'refreshToken', value: refreshToken);
    state = AuthState(accessToken: accessToken);
  }

  Future<void> logout() async {
    await _storage.deleteAll();
    state = const AuthState();
  }
}

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>(
  (_) => AuthNotifier(),
);

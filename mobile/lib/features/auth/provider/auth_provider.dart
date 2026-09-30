import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:mobile/core/network/dio_client.dart';

/// 서버가 보안상 모든 세션을 폐기했을 때(SESSION_REVOKED) 사용자에게 보여줄 안내 문구
const sessionRevokedNotice = '보안을 위해 모든 기기에서 로그아웃되었습니다. 다시 로그인해 주세요.';

class AuthState {
  final String? accessToken;

  /// 한 번만 보여줄 세션 종료 안내. 화면에 표시한 뒤 [AuthNotifier.clearSessionNotice] 로 비운다.
  final String? sessionNotice;

  const AuthState({this.accessToken, this.sessionNotice});
}

class AuthNotifier extends StateNotifier<AuthState> {
  final FlutterSecureStorage _storage;
  // 로그아웃 요청 전용 — 인증 인터셉터가 없어 401 시 refresh/세션 만료 처리가 다시 일어나지 않는다
  final Dio _dio;

  AuthNotifier({FlutterSecureStorage? storage, Dio? dio})
      : _storage = storage ?? const FlutterSecureStorage(),
        _dio = dio ?? Dio(BaseOptions(baseUrl: apiBaseUrl, contentType: 'application/json')),
        super(const AuthState()) {
    // refresh 실패로 세션이 만료되면 로그인 화면으로 돌아가도록 상태를 비운다 (토큰은 dio_client가 삭제)
    onSessionExpired = _onSessionExpired;
    _init();
  }

  void _onSessionExpired(SessionEndReason reason) {
    // 여러 요청이 같은 refresh 실패를 받아 콜백이 중복 호출돼도 알림은 한 번만 설정한다
    if (!mounted || state.accessToken == null) return;
    state = AuthState(
      sessionNotice: reason == SessionEndReason.revokedForSecurity ? sessionRevokedNotice : null,
    );
  }

  @override
  void dispose() {
    // 다른 인스턴스가 등록한 콜백은 건드리지 않는다
    if (onSessionExpired == _onSessionExpired) onSessionExpired = null;
    super.dispose();
  }

  Future<void> _init() async {
    final token = await _storage.read(key: 'accessToken');
    if (mounted) state = AuthState(accessToken: token, sessionNotice: state.sessionNotice);
  }

  Future<void> login(String accessToken, String refreshToken) async {
    await _storage.write(key: 'accessToken', value: accessToken);
    await _storage.write(key: 'refreshToken', value: refreshToken);
    state = AuthState(accessToken: accessToken);
  }

  /// 로컬 토큰을 먼저 지우고 화면을 로그인 상태에서 빼낸 뒤, 서버에 refresh 세션 폐기를 요청한다.
  /// 서버 호출은 best-effort — 실패해도 로컬 로그아웃은 이미 끝났으므로 무시한다.
  Future<void> logout() async {
    final refreshToken = await _storage.read(key: 'refreshToken');
    await _storage.deleteAll();
    if (mounted) state = const AuthState();

    if (refreshToken == null || refreshToken.isEmpty) return;
    try {
      await _dio.post('/api/v1/auth/logout', data: {'refreshToken': refreshToken});
    } catch (_) {
      // 네트워크 오류·5xx 등 — 토큰 값이 남지 않도록 로그도 남기지 않는다
    }
  }

  void clearSessionNotice() {
    if (mounted && state.sessionNotice != null) state = AuthState(accessToken: state.accessToken);
  }
}

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>(
  (_) => AuthNotifier(),
);

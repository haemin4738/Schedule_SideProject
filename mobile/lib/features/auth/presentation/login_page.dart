import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile/core/network/dio_client.dart';
import 'package:mobile/features/auth/provider/auth_provider.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _emailCtrl = TextEditingController();
  final _pwCtrl = TextEditingController();
  final _dio = createDio();

  Future<void> _login() async {
    final res = await _dio.post('/api/auth/login', data: {
      'email': _emailCtrl.text,
      'password': _pwCtrl.text,
    });
    final data = res.data['data'];
    await ref.read(authProvider.notifier).login(
          data['accessToken'] as String,
          data['refreshToken'] as String,
        );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text('로그인', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w600)),
            const SizedBox(height: 32),
            TextField(controller: _emailCtrl, decoration: const InputDecoration(labelText: '이메일')),
            const SizedBox(height: 12),
            TextField(controller: _pwCtrl, obscureText: true, decoration: const InputDecoration(labelText: '비밀번호')),
            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(onPressed: _login, child: const Text('로그인')),
            ),
          ],
        ),
      ),
    );
  }
}

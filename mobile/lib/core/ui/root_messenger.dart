import 'package:flutter/material.dart';

/// 앱 전역 ScaffoldMessenger 키. 라우트가 바뀌어도 유지되어야 하는 SnackBar(세션 종료 안내 등)에 쓴다.
final rootScaffoldMessengerKey = GlobalKey<ScaffoldMessengerState>();

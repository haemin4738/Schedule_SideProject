import 'package:flutter/material.dart';

/// 백엔드 com.lifelog.domain.event.EventCategory 와 동기화. 라벨·기본 색은 웹(constants/eventCategory.ts)과 같다
// ignore: constant_identifier_names
enum EventCategory { PERSONAL, WORK, REMINDER, OTHER }

extension EventCategoryX on EventCategory {
  String get label => switch (this) {
        EventCategory.PERSONAL => '개인',
        EventCategory.WORK => '업무',
        EventCategory.REMINDER => '알림',
        EventCategory.OTHER => '기타',
      };

  /// 색을 따로 고르지 않은 일정의 표시 색
  Color get defaultColor => switch (this) {
        EventCategory.PERSONAL => const Color(0xFF039BE5),
        EventCategory.WORK => const Color(0xFF3F51B5),
        EventCategory.REMINDER => const Color(0xFFF6BF26),
        EventCategory.OTHER => const Color(0xFF616161),
      };
}

EventCategory? parseEventCategory(Object? value) =>
    EventCategory.values.where((c) => c.name == value).firstOrNull;

final _hexColor = RegExp(r'^#[0-9A-Fa-f]{6}$');

/// 일정 표시 색: 직접 고른 색(형식이 올바를 때) → 카테고리 기본 색 → 개인 기본 색 (웹 resolveEventColor 와 같은 규칙)
Color resolveEventColor(String? color, EventCategory? category) {
  if (color != null && _hexColor.hasMatch(color)) {
    return Color(int.parse('FF${color.substring(1)}', radix: 16));
  }
  return (category ?? EventCategory.PERSONAL).defaultColor;
}

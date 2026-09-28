import 'package:intl/intl.dart';

// 백엔드 com.lifelog.domain.expense.ExpenseType 과 반드시 동기화되어야 함.
// 값 이름을 backend enum과 동일한 SCREAMING_CASE로 유지해야 .name 기반 JSON 직렬화가 그대로 맞음.
// ignore_for_file: constant_identifier_names
enum ExpenseType {
  EXPENSE,
  INCOME,
}

extension ExpenseTypeLabel on ExpenseType {
  String toKoreanLabel() {
    switch (this) {
      case ExpenseType.EXPENSE:
        return '지출';
      case ExpenseType.INCOME:
        return '수입';
    }
  }
}

/// 백엔드 ExpenseRequest.amount 의 허용 범위 (원 단위 정수).
const int minExpenseAmount = 1;
const int maxExpenseAmount = 99999999999;

final _amountFormat = NumberFormat('#,##0');

/// 12000 → "12,000원", -5000 → "-5,000원"
String formatAmount(int amount) => '${_amountFormat.format(amount)}원';

/// 목록 행 표기: 수입 "+12,000원", 지출 "-12,000원"
String formatSignedAmount(ExpenseType type, int amount) =>
    '${type == ExpenseType.INCOME ? '+' : '-'}${formatAmount(amount)}';

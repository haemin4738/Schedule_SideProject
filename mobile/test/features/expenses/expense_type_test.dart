import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/expenses/expense_type.dart';
import 'package:mobile/features/expenses/provider/expense_categories_provider.dart';
import 'package:mobile/features/expenses/provider/expense_summary_provider.dart';
import 'package:mobile/features/expenses/provider/expenses_provider.dart';

import 'expense_fakes.dart';

void main() {
  group('ExpenseType.toKoreanLabel', () {
    const expectedLabels = {
      ExpenseType.EXPENSE: '지출',
      ExpenseType.INCOME: '수입',
    };

    for (final entry in expectedLabels.entries) {
      test('${entry.key.name}은(는) "${entry.value}" 라벨을 갖는다', () {
        expect(entry.key.toKoreanLabel(), entry.value);
      });
    }

    test('모든 enum 값이 매핑 테이블에 포함된다', () {
      expect(expectedLabels.keys.toSet(), ExpenseType.values.toSet());
    });
  });

  group('formatAmount', () {
    test('천 단위 콤마와 "원"을 붙인다', () {
      expect(formatAmount(12000), '12,000원');
      expect(formatAmount(1), '1원');
      expect(formatAmount(0), '0원');
      expect(formatAmount(99999999999), '99,999,999,999원');
    });

    test('음수는 앞에 - 부호를 붙인다', () {
      expect(formatAmount(-5000), '-5,000원');
    });
  });

  group('formatSignedAmount', () {
    test('수입은 +, 지출은 - 부호를 붙인다', () {
      expect(formatSignedAmount(ExpenseType.INCOME, 12000), '+12,000원');
      expect(formatSignedAmount(ExpenseType.EXPENSE, 12000), '-12,000원');
    });
  });

  group('expenseErrorMessage', () {
    test('서버 envelope 의 error 메시지를 그대로 반환한다', () {
      expect(
        expenseErrorMessage(serverError(409, '이미 존재하는 카테고리입니다.')),
        '이미 존재하는 카테고리입니다.',
      );
    });

    test('응답이 없는 네트워크 오류는 일반 안내 문구를 반환한다', () {
      final e = DioException(
        requestOptions: RequestOptions(path: '/'),
        type: DioExceptionType.connectionError,
      );
      expect(expenseErrorMessage(e), '서버와 통신할 수 없습니다.');
    });

    test('Dio 오류가 아닌 내부 예외는 메시지를 노출하지 않고 일반 안내 문구를 반환한다', () {
      expect(
        expenseErrorMessage(const FormatException('Unexpected character at offset 3')),
        '요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.',
      );
    });
  });

  group('월 경계 계산', () {
    test('firstDayOfMonth / lastDayOfMonth 는 해당 월의 1일과 말일을 반환한다', () {
      expect(firstDayOfMonth(DateTime(2026, 9, 28, 13)), DateTime(2026, 9, 1));
      expect(lastDayOfMonth(DateTime(2026, 9, 28)), DateTime(2026, 9, 30));
      expect(lastDayOfMonth(DateTime(2028, 2, 1)), DateTime(2028, 2, 29));
      expect(lastDayOfMonth(DateTime(2026, 12, 5)), DateTime(2026, 12, 31));
    });
  });

  group('fromJson', () {
    test('ExpenseItem 은 목록 응답(memo 없음)을 파싱한다', () {
      final item = ExpenseItem.fromJson({
        'id': 1,
        'type': 'EXPENSE',
        'categoryId': 2,
        'categoryName': '식비',
        'amount': 12000,
        'transactionDate': '2026-09-28',
        'description': null,
      });

      expect(item.type, ExpenseType.EXPENSE);
      expect(item.amount, 12000);
      expect(item.description, isNull);
      expect(item.memo, isNull);
    });

    test('ExpenseItem 은 상세 응답의 memo 까지 파싱한다', () {
      final item = ExpenseItem.fromJson({
        'id': 1,
        'type': 'INCOME',
        'categoryId': 3,
        'categoryName': '급여',
        'amount': 3000000,
        'transactionDate': '2026-09-25',
        'description': '9월 급여',
        'memo': '세후',
        'createdAt': '2026-09-25T00:00:00',
        'updatedAt': '2026-09-25T00:00:00',
      });

      expect(item.type, ExpenseType.INCOME);
      expect(item.description, '9월 급여');
      expect(item.memo, '세후');
    });

    test('ExpenseCategory / CategorySummaryItem 을 파싱한다', () {
      final category = ExpenseCategory.fromJson(
        {'id': 1, 'type': 'INCOME', 'name': '급여'},
      );
      final summary = CategorySummaryItem.fromJson(
        {'categoryId': 1, 'categoryName': '식비', 'amount': 5000, 'count': 2},
      );

      expect(category.type, ExpenseType.INCOME);
      expect(category.name, '급여');
      expect(summary.amount, 5000);
      expect(summary.count, 2);
    });
  });
}

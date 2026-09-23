import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/jobapplications/job_application_status.dart';
import 'package:mobile/features/jobapplications/provider/job_applications_provider.dart';

void main() {
  group('JobApplicationStatus.toKoreanLabel', () {
    const expectedLabels = {
      JobApplicationStatus.APPLIED: '지원완료',
      JobApplicationStatus.DOCUMENT_PASS: '서류합격',
      JobApplicationStatus.DOCUMENT_FAIL: '서류불합격',
      JobApplicationStatus.INTERVIEW_SCHEDULED: '면접예정',
      JobApplicationStatus.INTERVIEW_PASS: '면접합격',
      JobApplicationStatus.INTERVIEW_FAIL: '면접불합격',
      JobApplicationStatus.OFFER: '최종합격(오퍼)',
      JobApplicationStatus.ACCEPTED: '입사확정',
      JobApplicationStatus.REJECTED: '불합격',
      JobApplicationStatus.WITHDRAWN: '지원취소',
    };

    for (final entry in expectedLabels.entries) {
      test('${entry.key.name}은(는) "${entry.value}" 라벨을 갖는다', () {
        expect(entry.key.toKoreanLabel(), entry.value);
      });
    }

    test('모든 enum 값이 매핑 테이블에 포함된다', () {
      expect(expectedLabels.keys.toSet(), JobApplicationStatus.values.toSet());
    });
  });

  group('JobApplicationItem.fromJson', () {
    test('백엔드 목록 응답(JSON)을 올바르게 파싱한다', () {
      final json = {
        'id': 1,
        'companyName': '테스트회사',
        'position': '백엔드 개발자',
        'status': 'APPLIED',
        'appliedAt': '2026-09-01',
      };

      final item = JobApplicationItem.fromJson(json);

      expect(item.id, 1);
      expect(item.companyName, '테스트회사');
      expect(item.position, '백엔드 개발자');
      expect(item.status, JobApplicationStatus.APPLIED);
      expect(item.appliedAt, '2026-09-01');
      expect(item.jobPostingUrl, isNull);
      expect(item.memo, isNull);
    });

    test('백엔드 상세 응답(JSON)의 선택 필드까지 올바르게 파싱한다', () {
      final json = {
        'id': 2,
        'companyName': '다른회사',
        'position': '프론트엔드 개발자',
        'status': 'INTERVIEW_SCHEDULED',
        'appliedAt': '2026-09-10',
        'jobPostingUrl': 'https://example.com/job/2',
        'memo': '2차 면접 준비',
        'createdAt': '2026-09-10T00:00:00',
        'updatedAt': '2026-09-11T00:00:00',
      };

      final item = JobApplicationItem.fromJson(json);

      expect(item.status, JobApplicationStatus.INTERVIEW_SCHEDULED);
      expect(item.jobPostingUrl, 'https://example.com/job/2');
      expect(item.memo, '2차 면접 준비');
      expect(item.createdAt, '2026-09-10T00:00:00');
      expect(item.updatedAt, '2026-09-11T00:00:00');
    });
  });
}

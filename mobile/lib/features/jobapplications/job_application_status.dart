// 백엔드 com.lifelog.domain.jobapplication.JobApplicationStatus 와 반드시 동기화되어야 함.
// (enum 이름/순서가 바뀌면 이 파일도 같이 갱신할 것)
// 값 이름을 backend enum과 동일한 SCREAMING_CASE로 유지해야 .name 기반 JSON 직렬화가 그대로 맞음.
// ignore_for_file: constant_identifier_names
enum JobApplicationStatus {
  APPLIED,
  DOCUMENT_PASS,
  DOCUMENT_FAIL,
  INTERVIEW_SCHEDULED,
  INTERVIEW_PASS,
  INTERVIEW_FAIL,
  OFFER,
  ACCEPTED,
  REJECTED,
  WITHDRAWN,
}

extension JobApplicationStatusLabel on JobApplicationStatus {
  String toKoreanLabel() {
    switch (this) {
      case JobApplicationStatus.APPLIED:
        return '지원완료';
      case JobApplicationStatus.DOCUMENT_PASS:
        return '서류합격';
      case JobApplicationStatus.DOCUMENT_FAIL:
        return '서류불합격';
      case JobApplicationStatus.INTERVIEW_SCHEDULED:
        return '면접예정';
      case JobApplicationStatus.INTERVIEW_PASS:
        return '면접합격';
      case JobApplicationStatus.INTERVIEW_FAIL:
        return '면접불합격';
      case JobApplicationStatus.OFFER:
        return '최종합격(오퍼)';
      case JobApplicationStatus.ACCEPTED:
        return '입사확정';
      case JobApplicationStatus.REJECTED:
        return '불합격';
      case JobApplicationStatus.WITHDRAWN:
        return '지원취소';
    }
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/jobapplications/job_application_status.dart';
import 'package:mobile/features/jobapplications/presentation/job_applications_page.dart';
import 'package:mobile/features/jobapplications/provider/job_applications_provider.dart';

/// 실제 Dio 네트워크 호출 없이 provider 상태만 주입하기 위한 fake notifier.
/// JobApplicationsNotifier의 생성자가 곧바로 fetch()를 호출하므로,
/// fetch/refresh/goToPage/filterByStatus를 no-op으로 override해서
/// 네트워크 호출을 막고 원하는 state를 직접 세팅한다.
class _FakeJobApplicationsNotifier extends JobApplicationsNotifier {
  _FakeJobApplicationsNotifier(AsyncValue<JobApplicationsState> initialState) {
    state = initialState;
  }

  @override
  Future<void> fetch({
    int page = 0,
    int size = 20,
    JobApplicationStatus? status,
  }) async {}

  @override
  Future<void> refresh() async {}

  @override
  Future<void> goToPage(int page) async {}

  @override
  Future<void> filterByStatus(JobApplicationStatus? status) async {}
}

Widget _wrap(AsyncValue<JobApplicationsState> state) {
  return ProviderScope(
    overrides: [
      jobApplicationsProvider.overrideWith(
        (ref) => _FakeJobApplicationsNotifier(state),
      ),
    ],
    child: const MaterialApp(home: JobApplicationsPage()),
  );
}

void main() {
  testWidgets('로딩 상태일 때 로딩 인디케이터를 보여준다', (tester) async {
    await tester.pumpWidget(_wrap(const AsyncValue.loading()));

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('데이터 상태일 때 목록을 렌더링한다', (tester) async {
    final state = AsyncValue.data(
      JobApplicationsState(
        items: const [
          JobApplicationItem(
            id: 1,
            companyName: '테스트회사',
            position: '백엔드 개발자',
            status: JobApplicationStatus.APPLIED,
            appliedAt: '2026-09-01',
          ),
        ],
        page: 0,
        size: 20,
        total: 1,
        totalPages: 1,
      ),
    );

    await tester.pumpWidget(_wrap(state));

    expect(find.text('테스트회사 · 백엔드 개발자'), findsOneWidget);
    expect(find.text('지원완료'), findsOneWidget);
    expect(find.text('구직활동 기록이 없습니다.'), findsNothing);
  });

  testWidgets('에러 상태일 때 에러 메시지를 보여준다', (tester) async {
    await tester.pumpWidget(
      _wrap(AsyncValue.error(Exception('network failed'), StackTrace.empty)),
    );

    expect(find.textContaining('오류:'), findsOneWidget);
  });
}

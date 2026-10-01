import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/calendar/provider/events_provider.dart';
import 'package:mobile/features/jobapplications/job_application_status.dart';
import 'package:mobile/features/jobapplications/provider/job_applications_provider.dart';

/// 생성자에서 바로 네트워크를 부르지 않도록 조회를 no-op 으로 바꾼 fake.
class _FakeEventsNotifier extends EventsNotifier {
  @override
  Future<void> refresh() async {}
}

class _FakeJobApplicationsNotifier extends JobApplicationsNotifier {
  @override
  Future<void> fetch({
    int page = 0,
    int size = 20,
    JobApplicationStatus? status,
  }) async {}
}

void main() {
  test('eventsProvider_구독이모두해제되면폐기되고_다시구독하면새인스턴스다', () async {
    final container = ProviderContainer(
      overrides: [eventsProvider.overrideWith((ref) => _FakeEventsNotifier())],
    );
    addTearDown(container.dispose);

    final sub = container.listen(eventsProvider, (_, _) {});
    final first = container.read(eventsProvider.notifier);
    sub.close();
    await container.pump();

    container.listen(eventsProvider, (_, _) {});
    final second = container.read(eventsProvider.notifier);

    // 로그아웃으로 캘린더 화면이 사라지면 이전 사용자의 일정이 남지 않는다
    expect(first.mounted, isFalse);
    expect(identical(first, second), isFalse);
  });

  test('jobApplicationsProvider_구독이모두해제되면폐기되고_다시구독하면새인스턴스다', () async {
    final container = ProviderContainer(
      overrides: [
        jobApplicationsProvider.overrideWith((ref) => _FakeJobApplicationsNotifier()),
      ],
    );
    addTearDown(container.dispose);

    final sub = container.listen(jobApplicationsProvider, (_, _) {});
    final first = container.read(jobApplicationsProvider.notifier);
    sub.close();
    await container.pump();

    container.listen(jobApplicationsProvider, (_, _) {});
    final second = container.read(jobApplicationsProvider.notifier);

    expect(first.mounted, isFalse);
    expect(identical(first, second), isFalse);
  });

  test('jobApplicationsProvider_구독중에는_같은인스턴스를유지한다', () async {
    final container = ProviderContainer(
      overrides: [
        jobApplicationsProvider.overrideWith((ref) => _FakeJobApplicationsNotifier()),
      ],
    );
    addTearDown(container.dispose);

    container.listen(jobApplicationsProvider, (_, _) {});
    final first = container.read(jobApplicationsProvider.notifier);
    await container.pump();

    // 목록 화면이 구독하는 동안 폼에서 read 해도 같은 인스턴스다
    expect(identical(first, container.read(jobApplicationsProvider.notifier)), isTrue);
    expect(first.mounted, isTrue);
  });
}

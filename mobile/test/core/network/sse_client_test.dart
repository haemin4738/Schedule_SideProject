import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/sse_client.dart';

import '../../features/expenses/expense_fake_http.dart';

// dio 응답처럼 Uint8List 스트림으로 만든다 (List<int> 스트림이면 타입 문제를 놓친다)
Stream<Uint8List> _chunks(List<String> parts) => Stream.fromIterable(parts.map((p) => Uint8List.fromList(utf8.encode(p))));

/// 실제 시간이 흐르며 조건이 참이 되기를 기다린다 (재연결 대기 같은 타이머용)
Future<void> _waitFor(bool Function() condition, {Duration timeout = const Duration(seconds: 5)}) async {
  final deadline = DateTime.now().add(timeout);
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) fail('시간 안에 조건이 충족되지 않았습니다');
    await Future<void>.delayed(const Duration(milliseconds: 2));
  }
}

ResponseBody _sseBody(Stream<Uint8List> bytes, {int status = 200}) => ResponseBody(
      bytes,
      status,
      headers: {
        Headers.contentTypeHeader: ['text/event-stream'],
      },
    );

void main() {
  group('parseSse', () {
    test('parseSse_청크경계에서줄이잘려도_이벤트를온전히나눈다', () async {
      final events = await parseSse(_chunks(['eve', 'nt:REF', 'RESH\nda', 'ta:\n', '\nevent:REFRESH\ndata:x\n\n'])).toList();

      expect(events.map((e) => (e.name, e.data)), [('REFRESH', ''), ('REFRESH', 'x')]);
    });

    test('parseSse_주석과여러줄data와CRLF_규격대로처리한다', () async {
      final events = await parseSse(_chunks([': keep-alive\r\n\r\nevent: A\r\ndata: 1\r\ndata: 2\r\nid: 7\r\n\r\ndata:only\n\n'])).toList();

      expect(events.map((e) => (e.name, e.data)), [('A', '1\n2'), ('message', 'only')]);
    });

    test('parseSse_빈줄만있으면_이벤트를만들지않는다', () async {
      expect(await parseSse(_chunks(['\n\n\n'])).toList(), isEmpty);
    });
  });

  group('sseEvents', () {
    const fast = Duration(milliseconds: 1);

    test('sseEvents_연결되면열림이벤트후서버이벤트를흘리고_끊기면다시연결한다', () async {
      var connections = 0;
      final adapter = FakeHttpAdapter((o) {
        connections++;
        return _sseBody(_chunks(['event:REFRESH\ndata:\n\n']));
      });
      final received = <String>[];
      final sub = sseEvents(fakeDio(adapter), '/api/v1/sse/events', minBackoff: fast, maxBackoff: fast, stableAfter: Duration.zero)
          .listen((e) => received.add(e.name));

      await _waitFor(() => connections >= 2 && received.length >= 4);
      await sub.cancel();

      expect(received.take(4), [sseOpenEvent, 'REFRESH', sseOpenEvent, 'REFRESH']);
      expect(adapter.requests.first.headers['Accept'], 'text/event-stream');
      expect(adapter.requests.first.responseType, ResponseType.stream);
    });

    test('sseEvents_연결실패_다시시도해성공하면열림이벤트를보낸다', () async {
      var connections = 0;
      final adapter = FakeHttpAdapter((o) {
        connections++;
        if (connections == 1) return errorBody(500, '오류');
        return _sseBody(const Stream.empty());
      });
      final received = <String>[];
      final sub = sseEvents(fakeDio(adapter), '/sse', minBackoff: fast, maxBackoff: fast).listen((e) => received.add(e.name));

      await _waitFor(() => received.contains(sseOpenEvent));
      await sub.cancel();

      expect(connections, greaterThanOrEqualTo(2));
    });

    test('sseEvents_구독취소_더이상연결하지않는다', () async {
      final adapter = FakeHttpAdapter((o) => _sseBody(const Stream.empty()));
      final sub = sseEvents(fakeDio(adapter), '/sse', minBackoff: fast, maxBackoff: fast).listen((_) {});
      await _waitFor(() => adapter.requests.isNotEmpty);

      await sub.cancel();
      final count = adapter.requests.length;
      await Future<void>.delayed(const Duration(milliseconds: 30));

      expect(adapter.requests.length, lessThanOrEqualTo(count + 1));
      final after = adapter.requests.length;
      await Future<void>.delayed(const Duration(milliseconds: 30));
      expect(adapter.requests.length, after);
    });

    test('sseEvents_연속실패_대기시간을두배씩늘리되상한을넘지않는다', () async {
      final times = <DateTime>[];
      final adapter = FakeHttpAdapter((o) {
        times.add(DateTime.now());
        return errorBody(500, '오류');
      });
      final sub = sseEvents(
        fakeDio(adapter),
        '/sse',
        minBackoff: const Duration(milliseconds: 10),
        maxBackoff: const Duration(milliseconds: 40),
      ).listen((_) {});

      await _waitFor(() => times.length >= 5);
      await sub.cancel();

      final gaps = [for (var i = 1; i < 5; i++) times[i].difference(times[i - 1]).inMilliseconds];
      // 10 → 20 → 40 → 40 (상한)
      expect(gaps[0], greaterThanOrEqualTo(10));
      expect(gaps[1], greaterThanOrEqualTo(20));
      expect(gaps[2], greaterThanOrEqualTo(40));
      expect(gaps[3], greaterThanOrEqualTo(40));
      expect(gaps[3], lessThan(200));
    });

    test('sseEvents_열리자마자끊기는연결_대기시간을되돌리지않고늘린다', () async {
      final times = <DateTime>[];
      final adapter = FakeHttpAdapter((o) {
        times.add(DateTime.now());
        return _sseBody(_chunks(['event:CONNECTED\ndata:\n\n']));
      });
      final sub = sseEvents(
        fakeDio(adapter),
        '/sse',
        minBackoff: const Duration(milliseconds: 10),
        maxBackoff: const Duration(milliseconds: 40),
      ).listen((_) {});

      await _waitFor(() => times.length >= 4);
      await sub.cancel();

      final gaps = [for (var i = 1; i < 4; i++) times[i].difference(times[i - 1]).inMilliseconds];
      expect(gaps[1], greaterThanOrEqualTo(20));
      expect(gaps[2], greaterThanOrEqualTo(40));
    });
  });
}

import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';

/// 서버가 보낸 이벤트 하나 (`event:` 이름, `data:` 줄들을 이어 붙인 값)
class SseEvent {
  final String name;
  final String data;

  const SseEvent(this.name, this.data);
}

/// 연결이 (다시) 열렸음을 알리는 가상 이벤트 이름 — 끊겨 있던 동안의 변경을 따라잡는 데 쓴다
const sseOpenEvent = '__open__';

/// SSE 본문(text/event-stream)을 이벤트로 나눈다. 줄이 청크 경계에서 잘려 와도 이어 붙인다.
/// 주석(`:` 로 시작)과 id/retry 필드는 무시한다.
Stream<SseEvent> parseSse(Stream<List<int>> bytes) async* {
  var name = 'message';
  final data = <String>[];
  // bytes.transform(utf8.decoder) 는 Stream<Uint8List>(dio 응답)에서 타입 오류가 나므로 bind 를 쓴다
  await for (final line in const LineSplitter().bind(utf8.decoder.bind(bytes))) {
    if (line.isEmpty) {
      if (data.isNotEmpty || name != 'message') yield SseEvent(name, data.join('\n'));
      name = 'message';
      data.clear();
      continue;
    }
    if (line.startsWith(':')) continue;
    final colon = line.indexOf(':');
    final field = colon == -1 ? line : line.substring(0, colon);
    var value = colon == -1 ? '' : line.substring(colon + 1);
    if (value.startsWith(' ')) value = value.substring(1);
    if (field == 'event') name = value;
    if (field == 'data') data.add(value);
  }
}

/// [path] 에 SSE 로 연결해 이벤트를 흘려보낸다. 끊기면 [minBackoff] 부터 두 배씩 [maxBackoff] 까지 기다렸다 다시 연결한다.
/// 인증 헤더·만료 토큰 재발급은 [dio] 의 인터셉터(createDio)가 처리한다. 구독을 취소하면 연결을 끊고 멈춘다.
Stream<SseEvent> sseEvents(
  Dio dio,
  String path, {
  Duration minBackoff = const Duration(seconds: 1),
  Duration maxBackoff = const Duration(seconds: 30),
  Duration stableAfter = const Duration(seconds: 10),
}) {
  late StreamController<SseEvent> controller;
  CancelToken? cancel;
  var stopped = false;

  Future<void> run() async {
    var backoff = minBackoff;
    while (!stopped) {
      cancel = CancelToken();
      try {
        final res = await dio.get<ResponseBody>(
          path,
          cancelToken: cancel,
          options: Options(
            responseType: ResponseType.stream,
            // 연결은 서버가 닫을 때(30분)까지 열려 있다 — createDio 는 받기 제한 시간을 두지 않는다
            headers: {'Accept': 'text/event-stream'},
          ),
        );
        if (stopped) break;
        controller.add(const SseEvent(sseOpenEvent, ''));
        final openedAt = DateTime.now();
        await for (final event in parseSse(res.data!.stream)) {
          if (stopped) break;
          controller.add(event);
        }
        // 연결 직후 바로 끊기는 경우(프록시·서버 오류)에 1초마다 재연결·재조회하지 않도록, 충분히 유지된 연결만 대기 시간을 되돌린다
        if (DateTime.now().difference(openedAt) >= stableAfter) backoff = minBackoff;
      } catch (_) {
        // 연결 실패·끊김 — 아래에서 기다렸다 다시 연결한다 (세션 만료면 인터셉터가 로그아웃 처리)
      }
      if (stopped) break;
      await Future<void>.delayed(backoff);
      final doubled = backoff * 2;
      backoff = doubled > maxBackoff ? maxBackoff : doubled;
    }
  }

  controller = StreamController<SseEvent>(
    onListen: run,
    onCancel: () {
      stopped = true;
      cancel?.cancel();
    },
  );
  return controller.stream;
}

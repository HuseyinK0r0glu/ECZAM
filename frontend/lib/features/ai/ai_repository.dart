import 'dart:convert';

import 'package:dio/dio.dart';

import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/features/ai/ai_dto.dart';

/// RAG assistant (`POST /ai/chat`) — Server-Sent Events over POST. Streams
/// [ChatEvent]s parsed from the SSE frames. Grounded strictly in leaflets
/// (CLAUDE.md §7): never sends a synthesized answer; on `grounded:false` the UI
/// shows the pharmacist guardrail.
class AiRepository {
  final ApiClient api;
  AiRepository(this.api);

  Stream<ChatEvent> chat({
    required String message,
    String? medicationId,
    List<String> history = const [],
    CancelToken? cancelToken,
  }) async* {
    final Response<ResponseBody> response = await api.dio.post<ResponseBody>(
      '/ai/chat',
      data: {
        'message': message,
        if (medicationId != null) 'medicationId': medicationId,
        if (history.isNotEmpty) 'history': history,
      },
      options: Options(
        responseType: ResponseType.stream,
        headers: {'Accept': 'text/event-stream'},
      ),
      cancelToken: cancelToken,
    );

    final body = response.data;
    if (body == null) return;
    yield* parseSseStream(body.stream);
  }
}

/// Parses a Server-Sent Events byte stream into [ChatEvent]s. Extracted as a
/// top-level function (decoupled from Dio) so it can be unit-tested with an
/// in-memory stream, including frames split across chunk boundaries.
///
/// Frames are separated by a blank line; within a frame, `event:` and `data:`
/// lines. A single leading space after `data:` is stripped per the SSE spec.
Stream<ChatEvent> parseSseStream(Stream<List<int>> byteStream) async* {
  var buffer = '';
  String? eventName;
  final dataLines = <String>[];

  ChatEvent? flush() {
    if (dataLines.isEmpty && eventName == null) return null;
    final data = dataLines.join('\n');
    final name = eventName;
    eventName = null;
    dataLines.clear();
    switch (name) {
      case 'token':
        return TokenEvent(data);
      case 'citation':
        return CitationEvent(data);
      case 'done':
        return DoneEvent(_parseGrounded(data));
      default:
        return null;
    }
  }

  await for (final chunk in byteStream) {
    buffer += utf8.decode(chunk, allowMalformed: true);
    var idx = buffer.indexOf('\n');
    while (idx != -1) {
      final rawLine = buffer.substring(0, idx);
      buffer = buffer.substring(idx + 1);
      final line = rawLine.endsWith('\r')
          ? rawLine.substring(0, rawLine.length - 1)
          : rawLine;

      if (line.isEmpty) {
        final event = flush();
        if (event != null) yield event;
      } else if (line.startsWith('event:')) {
        eventName = line.substring(6).trim();
      } else if (line.startsWith('data:')) {
        var d = line.substring(5);
        if (d.startsWith(' ')) d = d.substring(1);
        dataLines.add(d);
      }
      idx = buffer.indexOf('\n');
    }
  }
  final tail = flush();
  if (tail != null) yield tail;
}

bool _parseGrounded(String data) {
  try {
    final obj = jsonDecode(data);
    if (obj is Map && obj['grounded'] is bool) return obj['grounded'] as bool;
  } catch (_) {/* fall through */}
  return data.contains('true');
}

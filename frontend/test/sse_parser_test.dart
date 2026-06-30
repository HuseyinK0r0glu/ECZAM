import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/features/ai/ai_dto.dart';
import 'package:medtrack/features/ai/ai_repository.dart';

/// Turns SSE text pieces into a byte stream (one list per piece) so we can
/// exercise frame reassembly across arbitrary chunk boundaries.
Stream<List<int>> _bytes(List<String> pieces) async* {
  for (final p in pieces) {
    yield utf8.encode(p);
  }
}

void main() {
  test('parses token / citation / done events in order', () async {
    final events = await parseSseStream(_bytes([
      'event: token\ndata: Ibuprofen\n\n',
      'event: token\ndata:  is an NSAID\n\n',
      'event: citation\ndata: 4. Yan etkiler\n\n',
      'event: done\ndata: {"grounded":true}\n\n',
    ])).toList();

    expect(events, hasLength(4));
    expect((events[0] as TokenEvent).text, 'Ibuprofen');
    expect((events[1] as TokenEvent).text, 'is an NSAID'); // leading space stripped
    expect((events[2] as CitationEvent).section, '4. Yan etkiler');
    expect((events[3] as DoneEvent).grounded, isTrue);
  });

  test('reassembles a frame split across chunk boundaries', () async {
    final events = await parseSseStream(_bytes([
      'event: tok',
      'en\ndata: Hel',
      'lo\n\n',
      'event: done\ndata: {"grounded":false}\n\n',
    ])).toList();

    expect((events[0] as TokenEvent).text, 'Hello');
    expect((events[1] as DoneEvent).grounded, isFalse);
  });

  test('joins multi-line data and ignores unknown events', () async {
    final events = await parseSseStream(_bytes([
      'event: ping\ndata: ignore me\n\n',
      'event: token\ndata: line1\ndata: line2\n\n',
    ])).toList();

    expect(events, hasLength(1));
    expect((events[0] as TokenEvent).text, 'line1\nline2');
  });

  test('grounded:false drives the pharmacist guardrail path', () async {
    final events = await parseSseStream(_bytes([
      'event: done\ndata: {"grounded":false}\n\n',
    ])).toList();
    expect((events.single as DoneEvent).grounded, isFalse);
  });
}

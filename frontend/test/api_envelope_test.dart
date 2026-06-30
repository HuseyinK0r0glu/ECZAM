import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/core/api/api_envelope.dart';

void main() {
  group('ApiResponse.fromJson', () {
    test('parses a data payload and cursor meta', () {
      final env = ApiResponse<String>.fromJson(
        {
          'data': {'id': 'abc'},
          'meta': {'nextCursor': 'c2', 'limit': 20},
          'error': null,
        },
        (data) => (data as Map)['id'] as String,
      );
      expect(env.data, 'abc');
      expect(env.meta?.nextCursor, 'c2');
      expect(env.meta?.limit, 20);
      expect(env.error, isNull);
    });

    test('parses an error envelope with field details', () {
      final env = ApiResponse<Object?>.fromJson(
        {
          'data': null,
          'meta': null,
          'error': {
            'code': 'VALIDATION_FAILED',
            'message': 'Invalid',
            'fields': {'email': 'must be an email'},
          },
        },
        (data) => data,
      );
      expect(env.data, isNull);
      expect(env.error, isNotNull);
      expect(env.error!.code, 'VALIDATION_FAILED');
      expect(env.error!.fields['email'], 'must be an email');
    });
  });

  group('ApiException', () {
    test('flags auth challenges by status or code', () {
      expect(
        const ApiException(ApiError(code: 'X', message: 'm'), 401)
            .isUnauthenticated,
        isTrue,
      );
      expect(
        const ApiException(ApiError(code: 'TOKEN_INVALID', message: 'm'), 400)
            .isUnauthenticated,
        isTrue,
      );
      expect(
        const ApiException(ApiError(code: 'INSUFFICIENT_STOCK', message: 'm'),
                422)
            .isUnauthenticated,
        isFalse,
      );
    });
  });
}

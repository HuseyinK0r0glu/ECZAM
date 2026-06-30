import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/core/api/api_envelope.dart';
import 'package:medtrack/features/auth/auth_state.dart';

void main() {
  ({String message, Map<String, String> fields}) describe(String code,
          {Map<String, String> fields = const {}, int status = 422}) =>
      describeAuthError(ApiException(
          ApiError(code: code, message: 'raw', fields: fields), status));

  test('maps known backend codes to friendly messages', () {
    expect(describe('INVALID_CREDENTIALS').message, contains('incorrect'));
    expect(describe('EMAIL_TAKEN').message, contains('already registered'));
    expect(describe('WEAK_PASSWORD').message, contains('8 characters'));
    expect(describe('ACCOUNT_LOCKED').message, contains('locked'));
  });

  test('passes through 422 field errors', () {
    final r = describe('VALIDATION_FAILED', fields: {'email': 'must be an email'});
    expect(r.fields['email'], 'must be an email');
  });

  test('non-ApiException errors get a generic message', () {
    final r = describeAuthError(Exception('boom'));
    expect(r.message, contains('Something went wrong'));
    expect(r.fields, isEmpty);
  });
}

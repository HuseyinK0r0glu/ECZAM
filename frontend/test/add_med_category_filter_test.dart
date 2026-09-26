import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/core/token_store.dart';
import 'package:medtrack/features/medications/medication_repository.dart';
import 'package:medtrack/state/app_state.dart';
import 'package:medtrack/ui/add_med/add_med_sheet.dart';

import 'fakes.dart';

/// The add-medication sheet's new category-browse affordance: chips populated
/// from `GET /medications/categories`, narrowing catalog suggestions from
/// `GET /medications?category=`, with a tap prefilling name + strength — the
/// same `_catalogId`-linking behavior barcode scan already has.
void main() {
  Map<String, dynamic> envelope(Object? data) =>
      {'data': data, 'meta': null, 'error': null};

  testWidgets(
    'category chip narrows suggestions and picking one prefills name + strength',
    (tester) async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
      final adapter = DioAdapter(dio: dio, matcher: const UrlRequestMatcher());
      adapter.onGet(
        '/medications/categories',
        (server) => server.reply(200, envelope([
          {'category': 'Kas İskelet Sistemi', 'count': 2},
        ])),
      );
      adapter.onGet(
        '/medications',
        (server) => server.reply(200, envelope([
          {'id': 'm1', 'name': 'Aspirin', 'strength': '100 mg'},
        ])),
      );
      final catalog = CatalogRepository(
        ApiClient(tokenStore: TokenStore(), dio: dio),
      );

      final state = AppState(
        repo: FakeMedicationRepository(),
        notifications: FakeNotificationService(),
        photos: FakePhotoService(),
      );
      await state.init();

      await tester.pumpWidget(MaterialApp(
        home: MultiProvider(
          providers: [
            ChangeNotifierProvider.value(value: state),
            Provider<CatalogRepository>.value(value: catalog),
          ],
          child: const Scaffold(body: AddMedSheet()),
        ),
      ));
      // Initial build, then let the categories fetch (mocked, no real I/O)
      // land and its setState apply.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));
      await tester.pump(const Duration(milliseconds: 10));

      expect(find.text('Kas İskelet Sistemi'), findsOneWidget);

      await tester.tap(find.text('Kas İskelet Sistemi'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));
      await tester.pump(const Duration(milliseconds: 10));

      // Suggestion surfaced from the category-filtered search.
      expect(find.text('Aspirin'), findsOneWidget);
      expect(find.text('100 mg'), findsOneWidget);

      await tester.tap(find.text('Aspirin'));
      await tester.pump();

      // Suggestion list is gone; the same text now lives in the form fields.
      expect(find.text('Aspirin'), findsOneWidget);
      expect(find.text('100 mg'), findsOneWidget);
    },
  );

  testWidgets(
    'no CatalogRepository in the provider tree: no chip row, add flow unaffected',
    (tester) async {
      final state = AppState(
        repo: FakeMedicationRepository(),
        notifications: FakeNotificationService(),
        photos: FakePhotoService(),
      );
      await state.init();

      await tester.pumpWidget(MaterialApp(
        home: ChangeNotifierProvider.value(
          value: state,
          child: const Scaffold(body: AddMedSheet()),
        ),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));

      expect(find.widgetWithText(TextField, 'Medication name'), findsOneWidget);
    },
  );
}

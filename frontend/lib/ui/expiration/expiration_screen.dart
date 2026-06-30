import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/features/expiration/expiration_repository.dart';
import 'package:medtrack/features/inventory/inventory_dto.dart';
import 'package:medtrack/models/medication.dart' show ExpiryStatus;
import 'package:medtrack/theme/med_theme.dart';

/// Proactive expiry monitoring: items expiring soon and already-expired stock,
/// from `GET /expiration/expiring-soon` and `GET /expiration/expired`.
class ExpirationScreen extends StatefulWidget {
  const ExpirationScreen({super.key});

  @override
  State<ExpirationScreen> createState() => _ExpirationScreenState();
}

class _ExpirationScreenState extends State<ExpirationScreen> {
  late Future<(List<InventoryItem>, List<InventoryItem>)> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<(List<InventoryItem>, List<InventoryItem>)> _load() async {
    final repo = context.read<ExpirationRepository>();
    final soon = await repo.expiringSoon();
    final expired = await repo.expired();
    return (soon, expired);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: MedColors.bgMid,
      appBar: AppBar(
        backgroundColor: MedColors.bgTop,
        foregroundColor: MedColors.text,
        title: const Text('Expiration'),
      ),
      body: RefreshIndicator(
        onRefresh: () async => setState(() => _future = _load()),
        child: FutureBuilder<(List<InventoryItem>, List<InventoryItem>)>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState != ConnectionState.done) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return ListView(children: const [
                Padding(
                  padding: EdgeInsets.all(32),
                  child: Text(
                    'Could not load expiry data. Pull to retry.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: MedColors.textMuted),
                  ),
                ),
              ]);
            }
            final (soon, expired) = snap.data!;
            if (soon.isEmpty && expired.isEmpty) {
              return ListView(children: const [
                Padding(
                  padding: EdgeInsets.all(40),
                  child: Text(
                    'Nothing expiring soon. Your cabinet is in good shape.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontFamily: MedText.serif,
                      fontStyle: FontStyle.italic,
                      fontSize: 16,
                      color: MedColors.textMuted,
                    ),
                  ),
                ),
              ]);
            }
            return ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
              children: [
                if (expired.isNotEmpty) ...[
                  const _SectionLabel('EXPIRED — DO NOT TAKE'),
                  for (final it in expired) _ExpiryTile(item: it),
                  const SizedBox(height: 18),
                ],
                if (soon.isNotEmpty) ...[
                  const _SectionLabel('EXPIRING SOON'),
                  for (final it in soon) _ExpiryTile(item: it),
                ],
              ],
            );
          },
        ),
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String text;
  const _SectionLabel(this.text);

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 0, 4, 8),
        child: Text(text, style: MedText.sectionLabel),
      );
}

class _ExpiryTile extends StatelessWidget {
  final InventoryItem item;
  const _ExpiryTile({required this.item});

  @override
  Widget build(BuildContext context) {
    final expired = item.expiryStatus == ExpiryStatus.expired;
    final color = expired ? MedColors.danger : MedColors.late;
    final dateText = item.expirationDate == null
        ? 'No expiry date'
        : DateFormat.yMMMd().format(item.expirationDate!);
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        color: const Color(0x99FFFFFF),
        border: Border.all(color: color.withValues(alpha: 0.5)),
      ),
      child: Row(
        children: [
          Icon(expired ? Icons.dangerous_outlined : Icons.schedule,
              color: color, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.medicationName,
                    style: const TextStyle(
                        fontWeight: FontWeight.w700, color: MedColors.text)),
                const SizedBox(height: 2),
                Text(dateText,
                    style: TextStyle(fontSize: 12, color: color)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/features/medications/medication_dto.dart';
import 'package:medtrack/features/medications/medication_repository.dart';
import 'package:medtrack/theme/med_theme.dart';
import 'package:medtrack/ui/ai/ai_assistant_screen.dart';

/// Read-only leaflet view for one catalog medication: parsed leaflet sections as
/// glass cards, per-section read-aloud (TTS), semantic leaflet search, and a
/// shortcut into the RAG assistant scoped to this medicine. Grounded strictly in
/// the official leaflet (CLAUDE.md §7) — shows the pharmacist guardrail when no
/// leaflet exists.
class MedicationDetailScreen extends StatefulWidget {
  final String catalogId;
  final String name;

  const MedicationDetailScreen({
    super.key,
    required this.catalogId,
    required this.name,
  });

  @override
  State<MedicationDetailScreen> createState() => _MedicationDetailScreenState();
}

class _MedicationDetailScreenState extends State<MedicationDetailScreen> {
  final FlutterTts _tts = FlutterTts();
  final _searchCtrl = TextEditingController();
  CatalogMedicationDetail? _detail;
  List<LeafletSearchHit> _searchHits = const [];
  bool _loading = true;
  bool _searching = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _tts.stop();
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final d = await context.read<CatalogRepository>().leaflet(widget.catalogId);
      if (mounted) setState(() {
        _detail = d;
        _loading = false;
      });
    } catch (_) {
      if (mounted) setState(() {
        _error = 'Could not load the leaflet.';
        _loading = false;
      });
    }
  }

  Future<void> _runSearch() async {
    final q = _searchCtrl.text.trim();
    if (q.isEmpty) {
      setState(() => _searchHits = const []);
      return;
    }
    setState(() => _searching = true);
    try {
      final hits =
          await context.read<CatalogRepository>().searchLeaflet(widget.catalogId, q);
      if (mounted) setState(() => _searchHits = hits);
    } catch (_) {
      if (mounted) setState(() => _searchHits = const []);
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  Future<void> _speak(String text) async {
    await _tts.stop();
    await _tts.speak(text);
  }

  void _openAssistant() {
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => AiAssistantScreen(
        medicationId: widget.catalogId,
        medicationName: widget.name,
      ),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: MedColors.bgMid,
      appBar: AppBar(
        backgroundColor: MedColors.bgTop,
        foregroundColor: MedColors.text,
        title: Text(widget.name, style: MedText.screenTitle.copyWith(fontSize: 20)),
      ),
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [MedColors.bgTop, MedColors.bgMid, MedColors.bgBottom],
            stops: [0, 0.55, 1],
          ),
        ),
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : _buildBody(),
      ),
    );
  }

  Widget _buildBody() {
    final detail = _detail;
    final sections = detail?.leafletSections.entries ?? const <(String, String)>[];
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        _AssistantBanner(onTap: _openAssistant),
        const SizedBox(height: 16),
        if (_error != null)
          _GuardrailCard(
            text: _error!,
          )
        else if (sections.isEmpty)
          const _GuardrailCard(
            text:
                'No official leaflet information is available for this medicine. '
                'Please ask your pharmacist or doctor.',
          )
        else ...[
          _SearchField(
            controller: _searchCtrl,
            searching: _searching,
            onSubmit: _runSearch,
          ),
          if (_searchHits.isNotEmpty) ...[
            const SizedBox(height: 12),
            const Text('SEARCH RESULTS', style: MedText.sectionLabel),
            const SizedBox(height: 8),
            for (final hit in _searchHits)
              _LeafletCard(
                title: hit.section,
                body: hit.snippet,
                onRead: () => _speak(hit.snippet),
              ),
            const SizedBox(height: 8),
            const Divider(),
          ],
          const SizedBox(height: 12),
          const Text('LEAFLET', style: MedText.sectionLabel),
          const SizedBox(height: 8),
          for (final (label, text) in sections)
            _LeafletCard(
              title: label,
              body: text,
              onRead: () => _speak('$label. $text'),
            ),
        ],
      ],
    );
  }
}

class _AssistantBanner extends StatelessWidget {
  final VoidCallback onTap;
  const _AssistantBanner({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xD908282C), Color(0xC70D3C40)],
          ),
          boxShadow: const [
            BoxShadow(
                color: Color(0x4D062A2A), offset: Offset(0, 8), blurRadius: 24),
          ],
        ),
        child: Row(
          children: [
            const Icon(Icons.auto_awesome, color: MedColors.tealText),
            const SizedBox(width: 12),
            const Expanded(
              child: Text(
                'Ask the assistant about this medicine — answers come only from '
                'its leaflet.',
                style: TextStyle(color: Color(0xD9D2F5F3), fontSize: 12.5, height: 1.4),
              ),
            ),
            const Icon(Icons.chevron_right, color: MedColors.tealTextSoft),
          ],
        ),
      ),
    );
  }
}

class _SearchField extends StatelessWidget {
  final TextEditingController controller;
  final bool searching;
  final VoidCallback onSubmit;

  const _SearchField({
    required this.controller,
    required this.searching,
    required this.onSubmit,
  });

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      textInputAction: TextInputAction.search,
      onSubmitted: (_) => onSubmit(),
      decoration: InputDecoration(
        hintText: 'Search this leaflet…',
        prefixIcon: const Icon(Icons.search, color: MedColors.textMuted),
        suffixIcon: searching
            ? const Padding(
                padding: EdgeInsets.all(12),
                child: SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              )
            : IconButton(
                icon: const Icon(Icons.arrow_forward, color: MedColors.tealDeep),
                onPressed: onSubmit,
              ),
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }
}

class _LeafletCard extends StatelessWidget {
  final String title;
  final String body;
  final VoidCallback onRead;

  const _LeafletCard({
    required this.title,
    required this.body,
    required this.onRead,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.fromLTRB(16, 14, 8, 14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: const Color(0xCCFFFFFF),
        boxShadow: const [
          BoxShadow(
              color: Color(0x1F403626), offset: Offset(0, 4), blurRadius: 14),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(title,
                    style: MedText.sheetTitle.copyWith(fontSize: 18)),
              ),
              Semantics(
                button: true,
                label: 'Read aloud',
                child: IconButton(
                  icon: const Icon(Icons.volume_up, color: MedColors.tealDeep),
                  onPressed: onRead,
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: Text(
              body,
              style: const TextStyle(
                  fontSize: 14, height: 1.5, color: MedColors.textSoft),
            ),
          ),
        ],
      ),
    );
  }
}

class _GuardrailCard extends StatelessWidget {
  final String text;
  const _GuardrailCard({required this.text});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: const Color(0x14B98A2E),
        border: Border.all(color: const Color(0x40B98A2E)),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: MedColors.late),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(
                fontFamily: MedText.serif,
                fontStyle: FontStyle.italic,
                fontSize: 15,
                height: 1.5,
                color: MedColors.textSoft,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

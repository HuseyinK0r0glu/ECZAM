import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/features/auth/auth_state.dart';
import 'package:medtrack/features/profile/profile_dto.dart';
import 'package:medtrack/features/profile/profile_repository.dart';
import 'package:medtrack/theme/med_theme.dart';

/// Profile + notification preferences. The low-stock / expiry-warning
/// thresholds feed the cabinet badges and the expiration screen.
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  UserProfile? _profile;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final p = await context.read<ProfileRepository>().me();
      if (mounted) setState(() {
        _profile = p;
        _loading = false;
      });
    } catch (_) {
      if (mounted) setState(() {
        _error = 'Could not load your profile.';
        _loading = false;
      });
    }
  }

  Future<void> _setLowStock(int value) async {
    final updated = await context
        .read<ProfileRepository>()
        .updatePreferences(lowStockThreshold: value);
    if (mounted) setState(() => _profile = updated);
  }

  Future<void> _setExpiryDays(int value) async {
    final updated = await context
        .read<ProfileRepository>()
        .updatePreferences(expiryWarningDays: value);
    if (mounted) setState(() => _profile = updated);
  }

  Future<void> _setPush(bool value) async {
    final updated =
        await context.read<ProfileRepository>().updatePreferences(push: value);
    if (mounted) setState(() => _profile = updated);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: MedColors.bgMid,
      appBar: AppBar(
        backgroundColor: MedColors.bgTop,
        foregroundColor: MedColors.text,
        title: const Text('Profile'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Text(_error!,
                      style: const TextStyle(color: MedColors.textMuted)))
              : _buildBody(context, _profile!),
    );
  }

  Widget _buildBody(BuildContext context, UserProfile p) {
    final prefs = p.preferences;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: [
        _Card(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(p.displayName?.isNotEmpty == true ? p.displayName! : 'You',
                  style: MedText.sheetTitle),
              const SizedBox(height: 4),
              Text(p.email,
                  style: const TextStyle(color: MedColors.textMuted)),
              if (!p.emailVerified) ...[
                const SizedBox(height: 6),
                const Text('Email not verified',
                    style: TextStyle(fontSize: 12, color: MedColors.late)),
              ],
            ],
          ),
        ),
        const SizedBox(height: 16),
        const Text('NOTIFICATIONS', style: MedText.sectionLabel),
        const SizedBox(height: 8),
        _Card(
          child: SwitchListTile(
            value: prefs.push,
            onChanged: _setPush,
            activeColor: MedColors.teal,
            contentPadding: EdgeInsets.zero,
            title: const Text('Push reminders'),
            subtitle: const Text(
                'Local reminders always work; server push is a documented gap '
                'for the native app.'),
          ),
        ),
        const SizedBox(height: 16),
        const Text('THRESHOLDS', style: MedText.sectionLabel),
        const SizedBox(height: 8),
        _Card(
          child: Column(
            children: [
              _Stepper(
                label: 'Low-stock warning',
                suffix: 'units',
                value: prefs.lowStockThreshold,
                min: 0,
                max: 60,
                onChanged: _setLowStock,
              ),
              const Divider(height: 1),
              _Stepper(
                label: 'Expiry warning',
                suffix: 'days',
                value: prefs.expiryWarningDays,
                min: 0,
                max: 180,
                step: 5,
                onChanged: _setExpiryDays,
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        OutlinedButton.icon(
          onPressed: () => context.read<AuthState>().logout(),
          icon: const Icon(Icons.logout, color: MedColors.danger),
          label: const Text('Sign out',
              style: TextStyle(color: MedColors.danger)),
          style: OutlinedButton.styleFrom(
            side: const BorderSide(color: MedColors.dangerBorder),
            padding: const EdgeInsets.symmetric(vertical: 14),
          ),
        ),
      ],
    );
  }
}

class _Card extends StatelessWidget {
  final Widget child;
  const _Card({required this.child});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          color: const Color(0x99FFFFFF),
        ),
        child: child,
      );
}

class _Stepper extends StatelessWidget {
  final String label;
  final String suffix;
  final int value;
  final int min;
  final int max;
  final int step;
  final ValueChanged<int> onChanged;

  const _Stepper({
    required this.label,
    required this.suffix,
    required this.value,
    required this.min,
    required this.max,
    required this.onChanged,
    this.step = 1,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Expanded(child: Text(label, style: const TextStyle(fontSize: 14))),
          IconButton(
            onPressed:
                value > min ? () => onChanged((value - step).clamp(min, max)) : null,
            icon: const Icon(Icons.remove_circle_outline),
            color: MedColors.tealDeep,
          ),
          SizedBox(
            width: 64,
            child: Text('$value $suffix',
                textAlign: TextAlign.center,
                style: const TextStyle(fontWeight: FontWeight.w700)),
          ),
          IconButton(
            onPressed:
                value < max ? () => onChanged((value + step).clamp(min, max)) : null,
            icon: const Icon(Icons.add_circle_outline),
            color: MedColors.tealDeep,
          ),
        ],
      ),
    );
  }
}

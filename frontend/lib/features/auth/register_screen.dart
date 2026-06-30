import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/features/auth/auth_state.dart';
import 'package:medtrack/features/auth/auth_widgets.dart';
import 'package:medtrack/theme/med_theme.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _nameCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  String? _error;
  Map<String, String> _fieldErrors = const {};

  @override
  void dispose() {
    _nameCtrl.dispose();
    _emailCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  /// Mirrors the backend password policy (≥8, upper/lower/digit/special) so the
  /// user gets immediate feedback before the round-trip; the server still
  /// enforces it authoritatively (WEAK_PASSWORD).
  String? _localPasswordIssue(String pw) {
    if (pw.length < 8) return 'At least 8 characters.';
    final hasUpper = pw.contains(RegExp(r'[A-Z]'));
    final hasLower = pw.contains(RegExp(r'[a-z]'));
    final hasDigit = pw.contains(RegExp(r'[0-9]'));
    final hasSpecial = pw.contains(RegExp(r'[^A-Za-z0-9]'));
    if (!(hasUpper && hasLower && hasDigit && hasSpecial)) {
      return 'Add upper, lower, a digit and a symbol.';
    }
    return null;
  }

  Future<void> _submit() async {
    final name = _nameCtrl.text.trim();
    final email = _emailCtrl.text.trim();
    final password = _passwordCtrl.text;
    setState(() {
      _error = null;
      _fieldErrors = const {};
    });
    if (email.isEmpty) {
      setState(() => _fieldErrors = {'email': 'Enter your email.'});
      return;
    }
    final pwIssue = _localPasswordIssue(password);
    if (pwIssue != null) {
      setState(() => _fieldErrors = {'password': pwIssue});
      return;
    }
    try {
      await context.read<AuthState>().register(
            email: email,
            password: password,
            displayName: name.isEmpty ? null : name,
          );
      // On success the gate in main.dart swaps in the app shell; this route is
      // discarded with it.
    } catch (e) {
      final described = describeAuthError(e);
      if (!mounted) return;
      setState(() {
        _error = described.message;
        _fieldErrors = described.fields;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final busy = context.watch<AuthState>().busy;
    return AuthScaffold(
      title: 'Create account',
      subtitle: 'Track medicines, schedules and stock — securely.',
      children: [
        AuthErrorBanner(message: _error),
        AuthField(
          controller: _nameCtrl,
          hint: 'Name (optional)',
          icon: Icons.person_outline,
          keyboardType: TextInputType.name,
        ),
        AuthField(
          controller: _emailCtrl,
          hint: 'Email',
          icon: Icons.alternate_email,
          keyboardType: TextInputType.emailAddress,
          errorText: _fieldErrors['email'],
        ),
        AuthField(
          controller: _passwordCtrl,
          hint: 'Password',
          icon: Icons.lock_outline,
          obscure: true,
          errorText: _fieldErrors['password'],
          textInputAction: TextInputAction.done,
          onSubmitted: _submit,
        ),
        const SizedBox(height: 6),
        AuthPrimaryButton(label: 'Create account', busy: busy, onTap: _submit),
        const SizedBox(height: 16),
        TextButton(
          onPressed: busy ? null : () => Navigator.of(context).pop(),
          child: const Text.rich(
            TextSpan(
              text: 'Already have an account?  ',
              style: TextStyle(color: MedColors.textMuted, fontSize: 13),
              children: [
                TextSpan(
                  text: 'Sign in',
                  style: TextStyle(
                    color: MedColors.tealDeep,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

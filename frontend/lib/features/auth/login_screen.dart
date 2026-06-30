import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/features/auth/auth_state.dart';
import 'package:medtrack/features/auth/auth_widgets.dart';
import 'package:medtrack/features/auth/register_screen.dart';
import 'package:medtrack/theme/med_theme.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  String? _error;
  Map<String, String> _fieldErrors = const {};

  @override
  void dispose() {
    _emailCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final email = _emailCtrl.text.trim();
    final password = _passwordCtrl.text;
    setState(() {
      _error = null;
      _fieldErrors = const {};
    });
    if (email.isEmpty || password.isEmpty) {
      setState(() => _error = 'Enter your email and password.');
      return;
    }
    try {
      await context.read<AuthState>().login(email: email, password: password);
      // On success the gate in main.dart swaps in the app shell.
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
      title: 'ECZAM',
      subtitle: 'Sign in to your medicine cabinet.',
      children: [
        AuthErrorBanner(message: _error),
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
        AuthPrimaryButton(label: 'Sign in', busy: busy, onTap: _submit),
        const SizedBox(height: 16),
        TextButton(
          onPressed: busy
              ? null
              : () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const RegisterScreen(),
                    ),
                  ),
          child: const Text.rich(
            TextSpan(
              text: "New here?  ",
              style: TextStyle(color: MedColors.textMuted, fontSize: 13),
              children: [
                TextSpan(
                  text: 'Create an account',
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

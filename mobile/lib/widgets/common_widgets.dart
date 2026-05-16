import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/theme.dart';
import 'package:intl/intl.dart';

// Loading overlay
class LoadingOverlay extends StatelessWidget {
  final String? message;
  const LoadingOverlay({super.key, this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black26,
      child: Center(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const CircularProgressIndicator(),
                if (message != null) ...[
                  const SizedBox(height: 16),
                  Text(message!, style: const TextStyle(fontSize: 14)),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// KPI Card
class KpiCard extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color? color;
  final String? subtitle;
  final VoidCallback? onTap;

  const KpiCard({
    super.key,
    required this.label,
    required this.value,
    required this.icon,
    this.color,
    this.subtitle,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final c = color ?? Theme.of(context).colorScheme.primary;
    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: c.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, color: c, size: 20),
              ),
              const SizedBox(height: 12),
              Text(
                value,
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  color: c,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey.shade600,
                ),
              ),
              if (subtitle != null) ...[
                const SizedBox(height: 2),
                Text(
                  subtitle!,
                  style: TextStyle(fontSize: 11, color: Colors.grey.shade400),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

// Status badge
class StatusBadge extends StatelessWidget {
  final String status;
  const StatusBadge({super.key, required this.status});

  @override
  Widget build(BuildContext context) {
    final (Color bg, Color fg, String label) = switch (status) {
      'ACTIVE' || 'POSTED' => (AppTheme.success.withOpacity(0.1), AppTheme.success, status == 'POSTED' ? 'Validé' : 'Actif'),
      'VOIDED' || 'SUSPENDED' => (AppTheme.error.withOpacity(0.1), AppTheme.error, status == 'VOIDED' ? 'Annulé' : 'Suspendu'),
      'GRADUATED' => (AppTheme.warning.withOpacity(0.1), AppTheme.warning, 'Diplômé'),
      'PENDING' => (Colors.orange.withOpacity(0.1), Colors.orange, 'En attente'),
      'FAILED' => (AppTheme.error.withOpacity(0.1), AppTheme.error, 'Échec'),
      _ => (Colors.grey.withOpacity(0.1), Colors.grey, status),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: fg),
      ),
    );
  }
}

// Empty state
class EmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;

  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 64, color: Colors.grey.shade300),
            const SizedBox(height: 16),
            Text(
              title,
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.grey.shade600),
              textAlign: TextAlign.center,
            ),
            if (subtitle != null) ...[
              const SizedBox(height: 8),
              Text(
                subtitle!,
                style: TextStyle(fontSize: 14, color: Colors.grey.shade400),
                textAlign: TextAlign.center,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

// Amount text
class AmountText extends StatelessWidget {
  final int cents;
  final String? currency;
  final bool showPositiveSign;
  final TextStyle? style;

  const AmountText({
    super.key,
    required this.cents,
    this.currency,
    this.showPositiveSign = false,
    this.style,
  });

  @override
  Widget build(BuildContext context) {
    final numberFormat = NumberFormat('#,##0', 'fr_FR');
    final amount = cents / 100;
    final formatted = numberFormat.format(amount);
    final sign = showPositiveSign && amount > 0 ? '+' : '';
    final cur = currency ?? 'XAF';

    return Text(
      '$sign$formatted $cur',
      style: style ?? const TextStyle(fontWeight: FontWeight.w600),
    );
  }
}

// Section header
class SectionHeader extends StatelessWidget {
  final String title;
  final String? actionLabel;
  final VoidCallback? onAction;

  const SectionHeader({
    super.key,
    required this.title,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        if (actionLabel != null)
          TextButton(
            onPressed: onAction,
            style: TextButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              minimumSize: Size.zero,
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
            child: Text(actionLabel!, style: TextStyle(fontSize: 13, color: AppTheme.primary)),
          ),
      ],
    );
  }
}

// Transaction tile
class TransactionTile extends StatelessWidget {
  final String studentName;
  final String type;
  final String status;
  final int amountCents;
  final DateTime date;
  final VoidCallback? onTap;

  const TransactionTile({
    super.key,
    required this.studentName,
    required this.type,
    required this.status,
    required this.amountCents,
    required this.date,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('dd/MM/yy HH:mm', 'fr_FR');
    final isVoided = status == 'VOIDED';
    final isPayment = type == 'CASH_PAYMENT';

    return ListTile(
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: isVoided
            ? AppTheme.error.withOpacity(0.1)
            : isPayment
                ? AppTheme.success.withOpacity(0.1)
                : Colors.orange.withOpacity(0.1),
        child: Icon(
          isVoided ? Icons.cancel_outlined : isPayment ? Icons.payments_outlined : Icons.swap_horiz,
          color: isVoided ? AppTheme.error : isPayment ? AppTheme.success : Colors.orange,
          size: 20,
        ),
      ),
      title: Text(
        studentName,
        style: TextStyle(
          fontWeight: FontWeight.w500,
          decoration: isVoided ? TextDecoration.lineThrough : null,
          color: isVoided ? Colors.grey : null,
        ),
      ),
      subtitle: Text('${isPayment ? 'Encaissement' : type} • ${dateFormat.format(date)}'),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          AmountText(
            cents: amountCents,
            style: TextStyle(
              fontWeight: FontWeight.w600,
              fontSize: 14,
              color: isVoided ? Colors.grey : AppTheme.success,
              decoration: isVoided ? TextDecoration.lineThrough : null,
            ),
          ),
          const SizedBox(height: 2),
          StatusBadge(status: status),
        ],
      ),
    );
  }
}

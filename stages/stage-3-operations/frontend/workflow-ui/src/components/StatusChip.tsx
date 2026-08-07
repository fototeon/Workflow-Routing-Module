import { Chip } from '@mui/material';
import { colors } from '../colors';

const PALETTE: Record<'success' | 'warning' | 'error' | 'info' | 'neutral', { text: string; bg: string }> = {
  success: colors.status.success,
  warning: colors.status.warning,
  error: { text: '#F87171', bg: 'rgba(248, 113, 113, 0.1)' },
  info: { text: '#818CF8', bg: 'rgba(129, 140, 248, 0.1)' },
  neutral: { text: colors.text.secondary, bg: 'rgba(255, 255, 255, 0.06)' },
};

export type StatusTone = keyof typeof PALETTE;

export function StatusChip({ label, tone }: { label: string; tone: StatusTone }) {
  const { text, bg } = PALETTE[tone];
  return (
    <Chip
      size="small"
      label={label}
      sx={{
        color: text,
        backgroundColor: bg,
        border: 'none',
      }}
    />
  );
}

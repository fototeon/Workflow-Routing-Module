import { Box, Paper, Typography } from '@mui/material';
import type { RoutingRule } from '../api/types';
import { colors } from '../colors';

/**
 * Route map of the template as a graph (REQ-02-008): start node, one node per routed step in
 * priority order, and the terminal node. The step the instance currently sits on is highlighted.
 */
export function ProcessMap({ rules, currentStepCode }: { rules: RoutingRule[]; currentStepCode: string | null }) {
  if (rules.length === 0) {
    return null;
  }

  const ordered = [...rules].sort((a, b) => a.priority - b.priority);
  const nodeWidth = 190;
  const nodeHeight = 56;
  const gapY = 34;
  const startY = 10;
  const branchX = 250;
  const width = branchX + nodeWidth + 40;
  const endY = startY + (ordered.length + 1) * (nodeHeight + gapY);
  const height = endY + nodeHeight + 20;
  const startCx = 100;

  return (
    <Paper sx={{ p: 2.5, mb: 3, overflowX: 'auto' }}>
      <Typography variant="subtitle1" sx={{ color: colors.text.heading, mb: 1 }}>
        Карта маршрута
      </Typography>
      <Typography variant="caption" sx={{ color: colors.text.caption }}>
        Ветки шаблона в порядке приоритета: выигрывает первое подошедшее условие.
      </Typography>
      <Box sx={{ mt: 2 }}>
        <svg width={width} height={height} role="img" aria-label="Карта маршрута процесса">
          <Node x={startCx - nodeWidth / 2} y={startY} width={nodeWidth} height={nodeHeight} title="Старт" subtitle="Заявка принята" />

          {ordered.map((rule, index) => {
            const y = startY + (index + 1) * (nodeHeight + gapY);
            const active = currentStepCode === rule.targetStepCode;
            return (
              <g key={rule.id}>
                <path
                  d={`M ${startCx} ${startY + nodeHeight} V ${y + nodeHeight / 2} H ${branchX}`}
                  fill="none"
                  stroke={active ? colors.accent.buttonHover : colors.glass.panelStroke}
                  strokeWidth={active ? 2 : 1.5}
                />
                <text x={startCx + 10} y={y + nodeHeight / 2 - 8} fill={colors.text.caption} fontSize="11">
                  {`приоритет ${rule.priority}`}
                </text>
                <Node
                  x={branchX}
                  y={y}
                  width={nodeWidth}
                  height={nodeHeight}
                  title={rule.targetStepCode}
                  subtitle={rule.targetRole ? `роль: ${rule.targetRole}` : 'без роли'}
                  active={active}
                />
              </g>
            );
          })}

          <path
            d={`M ${startCx} ${startY + nodeHeight} V ${endY}`}
            fill="none"
            stroke={colors.glass.panelStroke}
            strokeWidth={1.5}
            strokeDasharray="4 4"
          />
          <Node
            x={startCx - nodeWidth / 2}
            y={endY}
            width={nodeWidth}
            height={nodeHeight}
            title="Завершение"
            subtitle="нет подходящих правил"
          />
        </svg>
      </Box>
    </Paper>
  );
}

function Node({
  x,
  y,
  width,
  height,
  title,
  subtitle,
  active,
}: {
  x: number;
  y: number;
  width: number;
  height: number;
  title: string;
  subtitle: string;
  active?: boolean;
}) {
  return (
    <g>
      <rect
        x={x}
        y={y}
        width={width}
        height={height}
        rx={10}
        fill={active ? colors.accent.activeTabBg : colors.glass.cardFill}
        stroke={active ? colors.accent.buttonHover : colors.glass.cardStroke}
        strokeWidth={active ? 2 : 1}
      />
      <text x={x + 14} y={y + 24} fill={colors.text.heading} fontSize="13" fontWeight="600">
        {title}
      </text>
      <text x={x + 14} y={y + 42} fill={colors.text.secondary} fontSize="11">
        {subtitle}
      </text>
    </g>
  );
}

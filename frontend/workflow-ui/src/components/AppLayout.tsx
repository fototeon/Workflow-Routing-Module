import type { ReactNode } from 'react';
import {
  Avatar,
  Box,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Toolbar,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/authContext';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import AssignmentTurnedInOutlinedIcon from '@mui/icons-material/AssignmentTurnedInOutlined';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined';
import HelpOutlineIcon from '@mui/icons-material/HelpOutlineOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import { getRoles, ROLES } from '../auth/authConfig';
import { colors } from '../colors';

const DRAWER_WIDTH = 260;

const NAV_ITEMS = [
  { path: '/processes', label: 'Процессы', icon: DescriptionOutlinedIcon, allow: [ROLES.COORDINATOR, ROLES.MANAGER, ROLES.ADMIN, ROLES.ANALYST] },
  { path: '/tasks', label: 'Задачи', icon: AssignmentTurnedInOutlinedIcon, allow: [ROLES.COORDINATOR, ROLES.MANAGER, ROLES.ADMIN] },
  { path: '/analytics', label: 'Аналитика', icon: InsightsOutlinedIcon, allow: [ROLES.ANALYST, ROLES.MANAGER, ROLES.ADMIN] },
  { path: '/templates', label: 'Шаблоны процессов', icon: AccountTreeOutlinedIcon, allow: [ROLES.ADMIN] },
  { path: '/sla-policies', label: 'SLA политики', icon: ScheduleOutlinedIcon, allow: [ROLES.ADMIN] },
];

const PAGE_TITLES: Record<string, string> = {
  '/analytics': 'Аналитика',
  '/processes': 'Реестр процессов',
  '/tasks': 'Реестр задач',
  '/templates': 'Шаблоны процессов',
  '/sla-policies': 'SLA политики',
};

export function AppLayout({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const roles = getRoles(auth.accessToken);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);

  const visibleItems = NAV_ITEMS.filter((item) => item.allow.some((role) => roles.includes(role)));
  const activeItem = visibleItems.find((item) => location.pathname.startsWith(item.path));
  const pageTitle = Object.entries(PAGE_TITLES).find(([path]) => location.pathname.startsWith(path))?.[1] ?? '';
  const username = auth.username;
  const initials = username.slice(0, 2).toUpperCase();

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box', display: 'flex', flexDirection: 'column' },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2.5, py: 3 }}>
          <Avatar
            sx={{
              width: 40,
              height: 40,
              background: `linear-gradient(135deg, ${colors.accent.logoGradientFrom}, ${colors.accent.logoGradientTo})`,
              fontWeight: 700,
            }}
          >
            W
          </Avatar>
          <Typography variant="subtitle2" sx={{ color: colors.text.heading, fontWeight: 700, lineHeight: 1.35 }}>
            Модуль управления процессами и маршрутизацией
          </Typography>
        </Box>

        <List sx={{ px: 1.5, flexGrow: 1 }}>
          {visibleItems.map((item) => {
            const Icon = item.icon;
            const selected = activeItem?.path === item.path;
            return (
              <ListItemButton
                key={item.path}
                selected={selected}
                onClick={() => navigate(item.path)}
                sx={{ mb: 0.5 }}
              >
                <ListItemIcon sx={{ minWidth: 36, color: selected ? colors.text.heading : colors.text.secondary }}>
                  <Icon fontSize="small" />
                </ListItemIcon>
                <ListItemText
                  primary={item.label}
                  slotProps={{
                    primary: {
                      sx: { color: selected ? colors.text.heading : colors.text.secondary, fontSize: '0.9rem' },
                    },
                  }}
                />
              </ListItemButton>
            );
          })}
        </List>

        <Box sx={{ p: 2 }}>
          <Box
            sx={{
              p: 2,
              borderRadius: 2,
              backgroundColor: colors.glass.cardFill,
              border: `1px solid ${colors.glass.cardStroke}`,
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <HelpOutlineIcon fontSize="small" sx={{ color: colors.accent.buttonHover }} />
              <Typography variant="subtitle2" sx={{ color: colors.text.heading }}>
                Справка
              </Typography>
            </Box>
            <Typography variant="caption" sx={{ color: colors.text.secondary, lineHeight: 1.5 }}>
              Документация и регламенты работы в модуле управления процессами и маршрутизацией.
            </Typography>
          </Box>
        </Box>
      </Drawer>

      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <Toolbar
          sx={{
            backgroundColor: colors.glass.panelFill,
            backdropFilter: `blur(${colors.glass.blur})`,
            borderBottom: `1px solid ${colors.glass.panelStroke}`,
            justifyContent: 'space-between',
          }}
        >
          <Typography variant="h6" sx={{ color: colors.text.heading }}>
            {pageTitle}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Box
              onClick={(e) => setMenuAnchor(e.currentTarget)}
              sx={{ display: 'flex', alignItems: 'center', gap: 0.5, cursor: 'pointer' }}
            >
              <Typography variant="body2" sx={{ color: colors.text.secondary }}>
                Роль: <span style={{ color: colors.text.body }}>{roles.join(', ') || '—'}</span>
              </Typography>
              <KeyboardArrowDownIcon fontSize="small" sx={{ color: colors.text.secondary }} />
            </Box>
            <Avatar
              onClick={(e) => setMenuAnchor(e.currentTarget)}
              sx={{
                width: 36,
                height: 36,
                cursor: 'pointer',
                background: `linear-gradient(135deg, ${colors.accent.logoGradientFrom}, ${colors.accent.logoGradientTo})`,
                fontSize: '0.85rem',
                fontWeight: 700,
              }}
            >
              {initials || '?'}
            </Avatar>
            <Menu anchorEl={menuAnchor} open={!!menuAnchor} onClose={() => setMenuAnchor(null)}>
              <MenuItem disabled>{username}</MenuItem>
              <MenuItem
                onClick={() => {
                  setMenuAnchor(null);
                  void auth.logout();
                }}
              >
                <LogoutIcon fontSize="small" sx={{ mr: 1 }} />
                Выйти
              </MenuItem>
            </Menu>
          </Box>
        </Toolbar>
        <Box sx={{ p: 3, flexGrow: 1 }}>{children}</Box>
      </Box>
    </Box>
  );
}

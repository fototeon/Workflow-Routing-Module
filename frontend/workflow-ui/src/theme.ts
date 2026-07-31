import { createTheme } from '@mui/material/styles';
import { colors } from './colors';

export const theme = createTheme({
  palette: {
    mode: 'dark',
    background: {
      default: colors.background,
      paper: colors.glass.panelFill,
    },
    primary: { main: colors.accent.buttonFill, light: colors.accent.buttonHover },
    secondary: { main: colors.accent.logoGradientFrom },
    success: { main: colors.status.success.text },
    warning: { main: colors.status.warning.text },
    text: {
      primary: colors.text.heading,
      secondary: colors.text.secondary,
    },
    divider: colors.glass.panelStroke,
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: colors.background,
          backgroundImage: [
            `radial-gradient(circle at 15% 50%, ${colors.meshGradients.blue}, transparent 50%)`,
            `radial-gradient(circle at 85% 10%, ${colors.meshGradients.purple}, transparent 50%)`,
            `radial-gradient(circle at 50% 100%, ${colors.meshGradients.cyan}, transparent 50%)`,
          ].join(', '),
          backgroundAttachment: 'fixed',
          minHeight: '100vh',
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          backgroundColor: colors.glass.panelFill,
          border: `1px solid ${colors.glass.panelStroke}`,
          backdropFilter: `blur(${colors.glass.blur})`,
        },
        outlined: {
          backgroundColor: colors.glass.cardFill,
          border: `1px solid ${colors.glass.cardStroke}`,
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: colors.glass.panelFill,
          backdropFilter: `blur(${colors.glass.blur})`,
          borderBottom: `1px solid ${colors.glass.panelStroke}`,
          boxShadow: 'none',
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: colors.glass.panelFill,
          backdropFilter: `blur(${colors.glass.blur})`,
          borderRight: `1px solid ${colors.glass.panelStroke}`,
          backgroundImage: 'none',
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: colors.input.fill,
          '& .MuiOutlinedInput-notchedOutline': { borderColor: colors.input.stroke },
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255, 255, 255, 0.2)' },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
            borderColor: colors.input.focusStroke,
            borderWidth: 1,
          },
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          borderRadius: 8,
          '&.MuiButton-containedPrimary': {
            backgroundColor: colors.accent.buttonFill,
          },
          '&.MuiButton-containedPrimary:hover': {
            backgroundColor: colors.accent.buttonHover,
          },
        },
      },
    },
    MuiTabs: {
      styleOverrides: {
        indicator: { backgroundColor: colors.accent.buttonFill },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          color: colors.text.secondary,
          textTransform: 'none',
          '&.Mui-selected': { color: colors.text.heading },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600 },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderColor: colors.glass.panelStroke,
          color: colors.text.body,
        },
        head: {
          color: colors.text.secondary,
          fontSize: '0.75rem',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          '&.Mui-selected': {
            backgroundColor: colors.accent.activeTabBg,
            '&:hover': { backgroundColor: colors.accent.activeTabBg },
          },
        },
      },
    },
  },
});

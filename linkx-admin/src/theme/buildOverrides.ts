/**
 * 作者：yangleduo
 */
import type { GlobalThemeOverrides } from 'naive-ui'
import type { AppTheme } from '@/i18n'
import { hexToRgb, primaryPalette } from './colorUtils'
import { darkThemeOverrides, lightThemeOverrides } from './overrides'

function primaryRgba(hex: string, alpha: number) {
  const rgb = hexToRgb(hex)
  if (!rgb) return `rgba(24, 144, 255, ${alpha})`
  return `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, ${alpha})`
}

function withPrimary(base: GlobalThemeOverrides, primaryHex: string): GlobalThemeOverrides {
  const pal = primaryPalette(primaryHex)
  const activeBg = primaryRgba(primaryHex, 0.16)
  const activeBgHover = primaryRgba(primaryHex, 0.2)
  const tdHover = primaryRgba(primaryHex, 0.08)

  return {
    ...base,
    common: {
      ...base.common,
      primaryColor: pal.primary,
      primaryColorHover: pal.hover,
      primaryColorPressed: pal.pressed,
      primaryColorSuppl: pal.primary,
      infoColor: pal.primary,
      infoColorHover: pal.hover,
      infoColorPressed: pal.pressed,
    },
    Tabs: {
      ...base.Tabs,
      barColor: pal.primary,
    },
    Menu: {
      ...base.Menu,
      itemTextColorActive: pal.primary,
      itemTextColorActiveHover: pal.primary,
      itemTextColorChildActive: pal.primary,
      itemTextColorChildActiveHover: pal.primary,
      itemIconColorActive: pal.primary,
      itemIconColorActiveHover: pal.primary,
      itemIconColorChildActive: pal.primary,
      itemIconColorChildActiveHover: pal.primary,
      itemColorActive: activeBg,
      itemColorActiveHover: activeBgHover,
      arrowColorChildActive: pal.primary,
    },
    DataTable: {
      ...base.DataTable,
      tdColorHover: tdHover,
    },
    Switch: {
      ...base.Switch,
      railColorActive: pal.primary,
    },
    Checkbox: {
      ...base.Checkbox,
      colorChecked: pal.primary,
      borderChecked: `1px solid ${pal.primary}`,
      borderFocus: `1px solid ${pal.primary}`,
    },
    Radio: {
      ...base.Radio,
      dotColorActive: pal.primary,
      boxShadowActive: `inset 0 0 0 1px ${pal.primary}`,
      boxShadowFocus: `inset 0 0 0 1px ${pal.primary}`,
    },
    Progress: {
      ...base.Progress,
      fillColor: pal.primary,
      iconColor: pal.primary,
    },
    LoadingBar: {
      ...base.LoadingBar,
      colorLoading: pal.primary,
    },
    Steps: {
      ...base.Steps,
      indicatorColorProcess: pal.primary,
      indicatorTextColorProcess: '#ffffff',
      splitorColorProcess: pal.primary,
    },
    Badge: {
      ...base.Badge,
      color: pal.primary,
    },
    Slider: {
      ...base.Slider,
      fillColor: pal.primary,
      fillColorHover: pal.hover,
    },
  }
}

export function buildThemeOverrides(
  theme: AppTheme,
  primaryHex: string,
  rounded: boolean
): GlobalThemeOverrides {
  const radius = rounded ? '8px' : '4px'
  const base = theme === 'dark' ? darkThemeOverrides : lightThemeOverrides
  const merged = withPrimary(base, primaryHex)
  return {
    ...merged,
    common: {
      ...merged.common,
      borderRadius: radius,
      borderRadiusSmall: radius,
    },
    Button: {
      ...merged.Button,
      borderRadiusTiny: radius,
      borderRadiusSmall: radius,
      borderRadiusMedium: radius,
      borderRadiusLarge: radius,
    },
    Input: {
      ...merged.Input,
      borderRadius: radius,
    },
    Card: {
      ...merged.Card,
      borderRadius: radius,
    },
    Modal: {
      ...merged.Modal,
      borderRadius: radius,
    },
    Drawer: {
      ...merged.Drawer,
      borderRadius: radius,
    },
    Tag: {
      ...merged.Tag,
      borderRadius: radius,
    },
    Dialog: {
      ...merged.Dialog,
      borderRadius: radius,
    },
    Popover: {
      ...merged.Popover,
      borderRadius: radius,
    },
    FloatButton: {
      ...merged.FloatButton,
      borderRadius: radius,
    },
  }
}

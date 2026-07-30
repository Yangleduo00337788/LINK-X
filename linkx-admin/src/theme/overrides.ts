import type { GlobalThemeOverrides } from 'naive-ui'

const fontFamily = '"IBM Plex Sans", "Segoe UI", sans-serif'
const radius = '16px'
const primary = {
  primaryColor: '#5b8def',
  primaryColorHover: '#7aa3f5',
  primaryColorPressed: '#3d6fd4',
  primaryColorSuppl: '#5b8def',
}

const buttonRadius = {
  Button: {
    borderRadiusMedium: radius,
    borderRadiusLarge: radius,
    borderRadiusSmall: radius,
    borderRadiusTiny: radius,
  },
  Input: {
    borderRadius: radius,
  },
  Card: {
    borderRadius: radius,
  },
  Dialog: {
    borderRadius: radius,
  },
  Modal: {
    borderRadius: radius,
  },
  Tag: {
    borderRadius: radius,
  },
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: radius,
      },
    },
  },
  DataTable: {
    borderRadius: radius,
  },
  Pagination: {
    itemBorderRadius: radius,
  },
  FloatButton: {
    borderRadius: radius,
  },
}

export const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    ...primary,
    bodyColor: '#0f1115',
    cardColor: '#171a21',
    modalColor: '#171a21',
    popoverColor: '#1c2029',
    tableColor: '#171a21',
    borderColor: '#2a2f3a',
    dividerColor: '#2a2f3a',
    textColorBase: '#e8eaed',
    textColor1: '#e8eaed',
    textColor2: '#a8b0bd',
    textColor3: '#7a8494',
    fontFamily,
    borderRadius: radius,
    borderRadiusSmall: radius,
  },
  Layout: {
    siderColor: '#12151b',
    headerColor: '#12151b',
    color: '#0f1115',
  },
  Menu: {
    itemTextColor: '#a8b0bd',
    itemTextColorHover: '#e8eaed',
    itemTextColorActive: '#e8eaed',
    itemTextColorActiveHover: '#e8eaed',
    itemTextColorChildActive: '#e8eaed',
    itemTextColorChildActiveHover: '#e8eaed',
    itemIconColor: '#7a8494',
    itemIconColorHover: '#e8eaed',
    itemIconColorActive: '#5b8def',
    itemIconColorActiveHover: '#5b8def',
    itemIconColorChildActive: '#5b8def',
    itemIconColorChildActiveHover: '#5b8def',
    itemColorActive: 'rgba(91, 141, 239, 0.12)',
    itemColorActiveHover: 'rgba(91, 141, 239, 0.16)',
    itemColorHover: 'rgba(255, 255, 255, 0.04)',
    arrowColorChildActive: '#e8eaed',
    borderRadius: radius,
  },
  ...buttonRadius,
}

export const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    ...primary,
    bodyColor: '#eef1f6',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    tableColor: '#ffffff',
    borderColor: '#dde2eb',
    dividerColor: '#dde2eb',
    textColorBase: '#1a1d24',
    textColor1: '#1a1d24',
    textColor2: '#5c6573',
    textColor3: '#8b93a1',
    fontFamily,
    borderRadius: radius,
    borderRadiusSmall: radius,
  },
  Layout: {
    siderColor: '#ffffff',
    headerColor: '#ffffff',
    color: '#eef1f6',
  },
  Menu: {
    itemTextColor: '#5c6573',
    itemTextColorHover: '#1a1d24',
    itemTextColorActive: '#1a1d24',
    itemTextColorActiveHover: '#1a1d24',
    itemTextColorChildActive: '#1a1d24',
    itemTextColorChildActiveHover: '#1a1d24',
    itemIconColor: '#8b93a1',
    itemIconColorHover: '#1a1d24',
    itemIconColorActive: '#5b8def',
    itemIconColorActiveHover: '#5b8def',
    itemIconColorChildActive: '#5b8def',
    itemIconColorChildActiveHover: '#5b8def',
    itemColorActive: 'rgba(91, 141, 239, 0.12)',
    itemColorActiveHover: 'rgba(91, 141, 239, 0.16)',
    itemColorHover: 'rgba(26, 29, 36, 0.04)',
    arrowColorChildActive: '#1a1d24',
    borderRadius: radius,
  },
  ...buttonRadius,
}

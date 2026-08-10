/**
 * 作者：yangleduo
 */
import type { GlobalThemeOverrides } from 'naive-ui'

const fontFamily = '"PingFang SC", "Microsoft YaHei", "IBM Plex Sans", "Segoe UI", sans-serif'
const radius = '4px'
const primary = {
  primaryColor: '#1890ff',
  primaryColorHover: '#40a9ff',
  primaryColorPressed: '#096dd9',
  primaryColorSuppl: '#1890ff',
  infoColor: '#1890ff',
  infoColorHover: '#40a9ff',
  infoColorPressed: '#096dd9',
  successColor: '#52c41a',
  successColorHover: '#73d13d',
  successColorPressed: '#389e0d',
  warningColor: '#faad14',
  warningColorHover: '#ffc53d',
  warningColorPressed: '#d48806',
  errorColor: '#cf1322',
  errorColorHover: '#ff4d4f',
  errorColorPressed: '#a8071a',
}

const controlHeight = {
  heightTiny: '24px',
  heightSmall: '28px',
  heightMedium: '32px',
  heightLarge: '36px',
}

const sharedComponents = {
  Button: {
    ...controlHeight,
    borderRadiusTiny: radius,
    borderRadiusSmall: radius,
    borderRadiusMedium: radius,
    borderRadiusLarge: radius,
    fontSizeTiny: '12px',
    fontSizeSmall: '13px',
    fontSizeMedium: '14px',
    fontSizeLarge: '14px',
    paddingMedium: '0 15px',
    paddingSmall: '0 11px',
    paddingLarge: '0 15px',
  },
  Input: {
    borderRadius: radius,
    heightSmall: '28px',
    heightMedium: '32px',
    heightLarge: '36px',
    fontSizeSmall: '13px',
    fontSizeMedium: '14px',
    fontSizeLarge: '14px',
  },
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: radius,
        heightSmall: '28px',
        heightMedium: '32px',
        heightLarge: '36px',
        fontSizeSmall: '13px',
        fontSizeMedium: '14px',
        fontSizeLarge: '14px',
      },
    },
  },
  Card: {
    borderRadius: radius,
    paddingMedium: '20px',
    paddingSmall: '16px',
    paddingLarge: '24px',
    titleFontSizeMedium: '16px',
    titleFontWeight: '600',
  },
  Dialog: {
    borderRadius: radius,
    padding: '20px 24px',
    titleFontSize: '16px',
    titleFontWeight: '600',
  },
  Modal: {
    borderRadius: radius,
  },
  Drawer: {
    borderRadius: radius,
  },
  Tag: {
    borderRadius: radius,
    heightSmall: '22px',
    heightMedium: '24px',
    heightLarge: '26px',
    fontSizeSmall: '12px',
    fontSizeMedium: '12px',
    fontSizeLarge: '13px',
    fontWeightStrong: '500',
  },
  Form: {
    labelFontSizeTopMedium: '14px',
    labelFontSizeLeftMedium: '14px',
    labelHeightMedium: '32px',
    feedbackFontSizeMedium: '12px',
  },
  Pagination: {
    itemBorderRadius: radius,
    buttonBorderRadius: radius,
    itemSizeMedium: '32px',
    itemFontSizeMedium: '14px',
  },
  Tabs: {
    tabFontSizeMedium: '14px',
    tabFontWeightActive: '600',
    tabGapMediumLine: '32px',
    barColor: '#1890ff',
  },
  Switch: {
    railBorderRadius: '12px',
    buttonBorderRadius: '10px',
  },
  Checkbox: {
    borderRadius: radius,
    sizeMedium: '16px',
  },
  Radio: {
    buttonBorderRadius: radius,
  },
  Slider: {
    handleSize: '14px',
    railHeight: '4px',
  },
  Progress: {
    railHeight: '8px',
  },
  Alert: {
    borderRadius: radius,
    padding: '10px 16px',
    fontSize: '14px',
  },
  Divider: {
    color: '#f0f0f0',
  },
  Dropdown: {
    borderRadius: radius,
    optionHeightMedium: '32px',
    fontSizeMedium: '14px',
  },
  Popover: {
    borderRadius: radius,
    padding: '12px 16px',
  },
  Tooltip: {
    borderRadius: radius,
  },
  Message: {
    borderRadius: radius,
  },
  Notification: {
    borderRadius: radius,
  },
  Breadcrumb: {
    fontSize: '12px',
  },
  PageHeader: {
    titleFontSize: '18px',
    titleFontWeight: '600',
  },
  FloatButton: {
    borderRadius: radius,
  },
  Upload: {
    borderRadius: radius,
  },
  DatePicker: {
    peers: {
      Input: {
        borderRadius: radius,
        heightMedium: '32px',
      },
    },
  },
  TimePicker: {
    peers: {
      Input: {
        borderRadius: radius,
        heightMedium: '32px',
      },
    },
  },
  InputNumber: {
    peers: {
      Input: {
        borderRadius: radius,
        heightMedium: '32px',
      },
    },
  },
  AutoComplete: {
    peers: {
      InternalSelectMenu: {
        borderRadius: radius,
      },
    },
  },
  Statistic: {
    labelFontSize: '13px',
    valueFontSize: '24px',
  },
  Empty: {
    fontSizeMedium: '14px',
  },
  Result: {
    titleFontSizeMedium: '20px',
    fontSizeMedium: '14px',
  },
  Badge: {
    fontSize: '12px',
  },
  Spin: {
    sizeMedium: '28px',
  },
}

export const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    ...primary,
    bodyColor: '#141414',
    cardColor: '#1f1f1f',
    modalColor: '#1f1f1f',
    popoverColor: '#262626',
    tableColor: '#1f1f1f',
    borderColor: '#303030',
    dividerColor: '#303030',
    textColorBase: '#e8e8e8',
    textColor1: '#e8e8e8',
    textColor2: '#a6a6a6',
    textColor3: '#737373',
    fontFamily,
    borderRadius: radius,
    borderRadiusSmall: radius,
    lineHeight: '1.5715',
  },
  Layout: {
    siderColor: '#1f1f1f',
    headerColor: '#1f1f1f',
    color: '#141414',
    footerColor: '#1f1f1f',
  },
  Menu: {
    itemTextColor: '#a6a6a6',
    itemTextColorHover: '#e8e8e8',
    itemTextColorActive: '#1890ff',
    itemTextColorActiveHover: '#1890ff',
    itemTextColorChildActive: '#1890ff',
    itemTextColorChildActiveHover: '#1890ff',
    itemIconColor: '#737373',
    itemIconColorHover: '#e8e8e8',
    itemIconColorActive: '#1890ff',
    itemIconColorActiveHover: '#1890ff',
    itemIconColorChildActive: '#1890ff',
    itemIconColorChildActiveHover: '#1890ff',
    itemColorActive: 'rgba(24, 144, 255, 0.16)',
    itemColorActiveHover: 'rgba(24, 144, 255, 0.2)',
    itemColorHover: 'rgba(255, 255, 255, 0.04)',
    arrowColor: '#737373',
    arrowColorChildActive: '#1890ff',
    borderRadius: '0',
    fontSize: '14px',
    itemHeight: '44px',
  },
  DataTable: {
    borderRadius: radius,
    thColor: '#262626',
    thColorModal: '#262626',
    thFontWeight: '600',
    tdColorHover: 'rgba(24, 144, 255, 0.08)',
    fontSizeMedium: '13px',
  },
  ...sharedComponents,
}

export const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    ...primary,
    bodyColor: '#f5f7fa',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    tableColor: '#ffffff',
    borderColor: '#e8eaed',
    dividerColor: '#ebeef5',
    textColorBase: '#303133',
    textColor1: '#303133',
    textColor2: '#606266',
    textColor3: '#757575',
    fontFamily,
    borderRadius: radius,
    borderRadiusSmall: radius,
    lineHeight: '1.5715',
  },
  Layout: {
    siderColor: '#ffffff',
    headerColor: '#ffffff',
    color: '#f5f7fa',
    footerColor: '#ffffff',
  },
  Menu: {
    itemTextColor: '#606266',
    itemTextColorHover: '#303133',
    itemTextColorActive: '#1890ff',
    itemTextColorActiveHover: '#1890ff',
    itemTextColorChildActive: '#1890ff',
    itemTextColorChildActiveHover: '#1890ff',
    itemIconColor: '#909399',
    itemIconColorHover: '#303133',
    itemIconColorActive: '#1890ff',
    itemIconColorActiveHover: '#1890ff',
    itemIconColorChildActive: '#1890ff',
    itemIconColorChildActiveHover: '#1890ff',
    itemColorActive: 'rgba(24, 144, 255, 0.1)',
    itemColorActiveHover: 'rgba(24, 144, 255, 0.14)',
    itemColorHover: 'rgba(0, 0, 0, 0.04)',
    arrowColor: '#909399',
    arrowColorChildActive: '#1890ff',
    borderRadius: '0',
    fontSize: '14px',
    itemHeight: '44px',
  },
  DataTable: {
    borderRadius: radius,
    thColor: '#fafafa',
    thColorModal: '#fafafa',
    thTextColor: '#303133',
    thFontWeight: '600',
    tdTextColor: '#303133',
    tdColorHover: '#f5f7fa',
    borderColor: '#ebeef5',
    fontSizeMedium: '13px',
  },
  ...sharedComponents,
  Tag: {
    borderRadius: radius,
    heightSmall: '22px',
    heightMedium: '24px',
    heightLarge: '26px',
    fontSizeSmall: '12px',
    fontSizeMedium: '12px',
    fontSizeLarge: '13px',
    fontWeightStrong: '500',
    colorSuccess: '#e8f8ef',
    textColorSuccess: '#237804',
    borderSuccess: '1px solid #b7eb8f',
    colorWarning: '#fffbe6',
    textColorWarning: '#ad6800',
    borderWarning: '1px solid #ffe58f',
    colorError: '#fff1f0',
    textColorError: '#cf1322',
    borderError: '1px solid #ffccc7',
    colorInfo: '#e6f4ff',
    textColorInfo: '#096dd9',
    borderInfo: '1px solid #91caff',
    color: '#f5f5f5',
    textColor: '#595959',
    border: '1px solid #d9d9d9',
  },
}

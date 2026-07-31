import type { Component } from 'vue'
import {
  SpeedometerOutline,
  PeopleOutline,
  ShieldCheckmarkOutline,
  RibbonOutline,
  KeyOutline,
  MenuOutline,
  TimeOutline,
  DocumentTextOutline,
  LogInOutline,
  ChatboxEllipsesOutline,
  SettingsOutline,
  CubeOutline,
  GridOutline,
  AppsOutline,
  WarningOutline,
  EyeOutline,
  NotificationsOutline,
  StatsChartOutline,
  BanOutline,
  PhonePortraitOutline,
} from '@vicons/ionicons5'

const iconMap: Record<string, Component> = {
  Dashboard: SpeedometerOutline,
  People: PeopleOutline,
  Shield: ShieldCheckmarkOutline,
  Badge: RibbonOutline,
  Key: KeyOutline,
  Menu: MenuOutline,
  History: TimeOutline,
  Document: DocumentTextOutline,
  LogIn: LogInOutline,
  Chatbox: ChatboxEllipsesOutline,
  Settings: SettingsOutline,
  Cube: CubeOutline,
  Users: PeopleOutline,
  Message: ChatboxEllipsesOutline,
  Warning: WarningOutline,
  Eye: EyeOutline,
  Bell: NotificationsOutline,
  Notifications: NotificationsOutline,
  Chart: StatsChartOutline,
  Stats: StatsChartOutline,
  Ban: BanOutline,
  Phone: PhonePortraitOutline,
  Devices: PhonePortraitOutline,
}

export function resolveMenuIcon(name?: string): Component {
  if (!name) return AppsOutline
  return iconMap[name] || GridOutline
}

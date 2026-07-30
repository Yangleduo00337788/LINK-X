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
}

export function resolveMenuIcon(name?: string): Component {
  if (!name) return AppsOutline
  return iconMap[name] || GridOutline
}

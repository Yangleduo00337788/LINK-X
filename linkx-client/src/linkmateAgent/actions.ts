/**
 * 作者：yangleduo
 */
import type { LinkMateActionDefinition, LinkMateAgentToolName } from './types'

const low = (name: LinkMateAgentToolName, labelKey: string): LinkMateActionDefinition => ({
  name,
  risk: 'low',
  requireConfirm: false,
  labelKey
})

const medium = (name: LinkMateAgentToolName, labelKey: string): LinkMateActionDefinition => ({
  name,
  risk: 'medium',
  requireConfirm: true,
  labelKey
})

const high = (name: LinkMateAgentToolName, labelKey: string): LinkMateActionDefinition => ({
  name,
  risk: 'high',
  requireConfirm: true,
  labelKey
})

export const LINKMATE_AGENT_ACTIONS: Record<LinkMateAgentToolName, LinkMateActionDefinition> = {
  navigate: low('navigate', 'linkmateAgent.actionNavigate'),
  open_linkmate: low('open_linkmate', 'linkmateAgent.actionOpenLinkmate'),
  open_chat: low('open_chat', 'linkmateAgent.actionOpenChat'),
  open_search: low('open_search', 'linkmateAgent.actionOpenSearch'),
  open_calendar: low('open_calendar', 'linkmateAgent.actionOpenCalendar'),
  open_contacts: low('open_contacts', 'linkmateAgent.actionOpenContacts'),
  send_message: medium('send_message', 'linkmateAgent.actionSendMessage'),
  add_friend: medium('add_friend', 'linkmateAgent.actionAddFriend'),
  handle_friend_request: medium('handle_friend_request', 'linkmateAgent.actionHandleFriendRequest'),
  handle_group_invitation: medium(
    'handle_group_invitation',
    'linkmateAgent.actionHandleGroupInvitation'
  ),
  create_calendar_event: medium('create_calendar_event', 'linkmateAgent.actionCreateEvent'),
  update_calendar_event: medium('update_calendar_event', 'linkmateAgent.actionUpdateEvent'),
  delete_calendar_event: medium('delete_calendar_event', 'linkmateAgent.actionDeleteEvent'),
  add_favorite: medium('add_favorite', 'linkmateAgent.actionAddFavorite'),
  update_favorite: medium('update_favorite', 'linkmateAgent.actionUpdateFavorite'),
  delete_favorite: medium('delete_favorite', 'linkmateAgent.actionDeleteFavorite'),
  tag_favorite: medium('tag_favorite', 'linkmateAgent.actionTagFavorite'),
  create_folder: medium('create_folder', 'linkmateAgent.actionCreateFolder'),
  upload_file: medium('upload_file', 'linkmateAgent.actionUploadFile'),
  publish_moment: medium('publish_moment', 'linkmateAgent.actionPublishMoment'),
  publish_short_video: medium('publish_short_video', 'linkmateAgent.actionPublishShortVideo'),
  send_red_packet: high('send_red_packet', 'linkmateAgent.actionSendRedPacket'),
  start_call: high('start_call', 'linkmateAgent.actionStartCall'),
  create_group: high('create_group', 'linkmateAgent.actionCreateGroup'),
  add_group_members: high('add_group_members', 'linkmateAgent.actionAddGroupMembers'),
  update_setting: medium('update_setting', 'linkmateAgent.actionUpdateSetting'),
  recharge_balance: high('recharge_balance', 'linkmateAgent.actionRechargeBalance')
}

export function getActionDefinition(name: LinkMateAgentToolName): LinkMateActionDefinition {
  return LINKMATE_AGENT_ACTIONS[name]
}

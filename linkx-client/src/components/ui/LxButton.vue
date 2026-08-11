<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 公共文本按钮。样式由 styles/ui-components.css 的 .lx-btn 系列控制。
 */
import { computed, useAttrs } from 'vue'

type LxButtonVariant =
  | 'default'
  | 'primary'
  | 'ghost'
  | 'block'
  | 'block-danger'
  | 'danger'
  | 'modal'
  | 'modal-primary'
  | 'sm'
  | 'sm-primary'
  | 'comfortable'
  | 'primary-comfortable'
  | 'compact'
  | 'compact-ghost'
  | 'compact-primary'
  | 'confirm-block'
  | 'outline'
  | 'send'
  | 'search'
  | 'share'
  | 'upload'
  | 'notif-accept'
  | 'notif-reject'
  | 'conference-ghost'
  | 'conference-primary'
  | 'moments-submit'
  | 'composer-tool'
  | 'mode-switch'
  | 'profile-action'
  | 'week-footer'
  | 'crop-action'
  | 'crop-action-primary'
  | 'toolbar-primary'
  | 'toolbar-ghost'
  | 'toolbar-ghost-xs'
  | 'toolbar-primary-grow'
  | 'login'
  | 'pill-primary'
  | 'link-refresh'
  | 'primary-sm'
  | 'primary-lg'
  | 'link'
  | 'link-md'
  | 'link-muted'
  | 'link-danger'
  | 'add-dashed'
  | 'submit-block'
  | 'modal-danger'
  | 'skip'
  | 'redpacket-open'
  | 'moments-tool'
  | 'moments-tool-danger'
  | 'join'
  | 'join-self'

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    variant?: LxButtonVariant
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
    title?: string
  }>(),
  {
    variant: 'default',
    type: 'button',
    disabled: false
  }
)

const attrs = useAttrs()

const variantClassMap: Record<LxButtonVariant, string | string[]> = {
  default: '',
  primary: 'lx-btn--primary',
  ghost: 'lx-btn--ghost',
  block: 'lx-btn--block',
  'block-danger': ['lx-btn--block', 'lx-btn--danger'],
  danger: 'lx-btn--danger',
  modal: 'lx-btn--modal',
  'modal-primary': ['lx-btn--modal', 'lx-btn--primary'],
  sm: 'lx-btn--sm',
  'sm-primary': ['lx-btn--sm', 'lx-btn--primary'],
  comfortable: 'lx-btn--comfortable',
  'primary-comfortable': ['lx-btn--primary', 'lx-btn--comfortable'],
  compact: 'lx-btn--compact',
  'compact-ghost': ['lx-btn--compact', 'lx-btn--ghost'],
  'compact-primary': ['lx-btn--compact', 'lx-btn--primary'],
  'confirm-block': ['lx-btn--primary', 'lx-btn--confirm-block'],
  outline: 'lx-btn--outline',
  send: 'lx-btn--send',
  search: 'lx-btn--search',
  share: 'lx-btn--share',
  upload: 'lx-btn--upload',
  'notif-accept': ['lx-btn--notif', 'lx-btn--notif-accept'],
  'notif-reject': ['lx-btn--notif', 'lx-btn--notif-reject'],
  'conference-ghost': ['lx-btn--conference', 'lx-btn--conference-ghost'],
  'conference-primary': ['lx-btn--conference', 'lx-btn--conference-primary'],
  'moments-submit': ['lx-btn--primary', 'lx-btn--moments-submit'],
  'composer-tool': 'lx-btn--composer-tool',
  'mode-switch': 'lx-btn--mode-switch',
  'profile-action': 'lx-btn--profile-action',
  'week-footer': 'lx-btn--week-footer',
  'crop-action': 'lx-btn--crop-action',
  'crop-action-primary': ['lx-btn--crop-action', 'lx-btn--primary'],
  'toolbar-primary': ['lx-btn--primary', 'lx-btn--toolbar', 'lx-btn--toolbar-primary'],
  'toolbar-ghost': ['lx-btn--toolbar', 'lx-btn--toolbar-ghost'],
  'toolbar-ghost-xs': ['lx-btn--toolbar-ghost', 'lx-btn--toolbar-xs'],
  'toolbar-primary-grow': ['lx-btn--primary', 'lx-btn--toolbar', 'lx-btn--toolbar-grow'],
  login: 'lx-btn--login',
  'pill-primary': ['lx-btn--primary', 'lx-btn--pill'],
  'link-refresh': ['lx-link-btn', 'lx-link-btn--refresh'],
  'primary-sm': ['lx-btn--primary', 'lx-btn--primary-sm'],
  'primary-lg': ['lx-btn--primary', 'lx-btn--primary-lg'],
  link: 'lx-link-btn',
  'link-md': ['lx-link-btn', 'lx-link-btn--md'],
  'link-muted': ['lx-link-btn', 'lx-link-btn--muted'],
  'link-danger': ['lx-link-btn', 'lx-link-btn--danger'],
  'add-dashed': 'lx-btn--add-dashed',
  'submit-block': ['lx-btn--primary', 'lx-btn--submit-block'],
  'modal-danger': ['lx-btn--modal', 'lx-btn--danger'],
  skip: 'lx-btn--skip',
  'redpacket-open': 'lx-btn--redpacket-open',
  'moments-tool': 'lx-btn--moments-tool',
  'moments-tool-danger': ['lx-btn--moments-tool', 'lx-btn--moments-tool-danger'],
  join: 'lx-join-btn',
  'join-self': ['lx-join-btn', 'is-self']
}

const rootClass = computed(() => {
  const mapped = variantClassMap[props.variant]
  const base = ['lx-btn', mapped]
  // search/share/upload/mode-switch/week-footer/crop-action 本身是独立类，不强制依赖 .lx-btn 布局时仍保留 lx-btn 无害
  if (
    props.variant === 'search' ||
    props.variant === 'share' ||
    props.variant === 'upload' ||
    props.variant === 'mode-switch' ||
    props.variant === 'week-footer' ||
    props.variant === 'login' ||
    props.variant === 'link-refresh' ||
    props.variant === 'link' ||
    props.variant === 'link-md' ||
    props.variant === 'link-muted' ||
    props.variant === 'link-danger' ||
    props.variant === 'add-dashed' ||
    props.variant === 'skip' ||
    props.variant === 'redpacket-open' ||
    props.variant === 'moments-tool' ||
    props.variant === 'moments-tool-danger' ||
    props.variant === 'join' ||
    props.variant === 'join-self'
  ) {
    return [mapped, attrs.class]
  }
  if (props.variant === 'crop-action' || props.variant === 'crop-action-primary') {
    return [mapped, attrs.class]
  }
  return [...base, attrs.class]
})
</script>

<template>
  <button
    :type="type"
    :class="rootClass"
    :disabled="disabled"
    :title="title"
    v-bind="{ ...attrs, class: undefined }"
  >
    <slot />
  </button>
</template>

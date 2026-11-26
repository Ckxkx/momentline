import type { ReactNode } from 'react'

export type IconName = 'plus' | 'settings' | 'search' | 'back' | 'edit' | 'trash' | 'close' | 'chevron' | 'upload' | 'download' | 'install' | 'note' | 'offline' | 'refresh' | 'check'

const paths: Record<IconName, ReactNode> = {
  plus: <path d="M12 5v14M5 12h14" />,
  settings: <><circle cx="12" cy="12" r="3" /><path d="M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.3 1a8 8 0 0 0-1.8-1L14.5 3h-5l-.3 3.1a8 8 0 0 0-1.8 1l-2.3-1-2 3.4 2 1.5a7 7 0 0 0 0 2l-2 1.5 2 3.4 2.3-1a8 8 0 0 0 1.8 1l.3 3.1h5l.3-3.1a8 8 0 0 0 1.8-1l2.3 1 2-3.4-2-1.5a7 7 0 0 0 .1-1Z" /></>,
  search: <><circle cx="11" cy="11" r="7" /><path d="m16 16 5 5" /></>,
  back: <path d="m15 18-6-6 6-6" />,
  edit: <path d="m4 16-.8 4.8L8 20 19 9l-4-4L4 16Zm9-9 4 4" />,
  trash: <path d="M4 7h16M9 7V4h6v3m-9 0 1 13h10l1-13M10 11v5M14 11v5" />,
  close: <path d="m6 6 12 12M18 6 6 18" />,
  chevron: <path d="m9 18 6-6-6-6" />,
  upload: <path d="M12 16V4m-5 5 5-5 5 5M5 14v5h14v-5" />,
  download: <path d="M12 4v12m-5-5 5 5 5-5M5 14v5h14v-5" />,
  install: <><rect x="5" y="3" width="14" height="18" rx="3" /><path d="M12 7v8m-3-3 3 3 3-3M10 18h4" /></>,
  note: <path d="M6 3h9l4 4v14H6V3Zm9 0v5h4M9 12h7M9 16h7" />,
  offline: <><path d="m3 3 18 18M5 9a12 12 0 0 1 3-1.6M12 5a12 12 0 0 1 9 4M8 13a7 7 0 0 1 4-1.7M16 13a7 7 0 0 0-1.4-1.2M10.5 16.5a3 3 0 0 1 3 0" /><circle cx="12" cy="20" r=".7" /></>,
  refresh: <path d="M20 7v5h-5M4 17v-5h5M6.1 8a7 7 0 0 1 11.8-1L20 12M4 12l2.1 5a7 7 0 0 0 11.8-1" />,
  check: <path d="m5 12 4 4L19 6" />
}

export function Icon({ name, className }: { name: IconName; className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>
}
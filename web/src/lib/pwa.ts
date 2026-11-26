import { registerSW } from 'virtual:pwa-register'

export interface PwaStatus {
  online: boolean
  offlineReady: boolean
  preparationError: boolean
}

let status: PwaStatus = {
  online: typeof navigator === 'undefined' ? true : navigator.onLine,
  offlineReady: false,
  preparationError: false
}
const listeners = new Set<() => void>()

function emit(patch: Partial<PwaStatus>): void {
  status = { ...status, ...patch }
  listeners.forEach(listener => listener())
}

export function getPwaStatus(): PwaStatus {
  return status
}

export function subscribeToPwaStatus(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function registerAppPwa(): void {
  window.addEventListener('online', () => emit({ online: true }))
  window.addEventListener('offline', () => emit({ online: false }))
  registerSW({
    immediate: true,
    onOfflineReady: () => emit({ offlineReady: true, preparationError: false }),
    onRegisterError: () => emit({ preparationError: true })
  })
  if ('serviceWorker' in navigator) {
    void navigator.serviceWorker.ready.then(() => emit({ offlineReady: true, preparationError: false })).catch(() => emit({ preparationError: true }))
  }
}

export async function retryOfflinePreparation(): Promise<void> {
  if (!('serviceWorker' in navigator)) throw new Error('当前浏览器不支持离线预缓存')
  emit({ preparationError: false })
  let timeout = 0
  try {
    const registration = await navigator.serviceWorker.getRegistration()
    if (registration) await registration.update()
    else await navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`)
    await Promise.race([
      navigator.serviceWorker.ready,
      new Promise<never>((_resolve, reject) => {
        timeout = window.setTimeout(() => reject(new Error('离线资源准备超时')), 15000)
      })
    ])
    emit({ offlineReady: true, preparationError: false })
  } catch (error) {
    emit({ preparationError: true })
    throw error
  } finally {
    window.clearTimeout(timeout)
  }
}
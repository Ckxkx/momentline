import { useState } from 'react'
import { Modal } from './Modal'

interface ConfirmDialogProps {
  title: string
  description: string
  confirmLabel: string
  onClose: () => void
  onConfirm: () => Promise<void>
}

export function ConfirmDialog({ title, description, confirmLabel, onClose, onConfirm }: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const confirm = async () => {
    setBusy(true)
    setError('')
    try {
      await onConfirm()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '操作未完成，请重试')
    } finally {
      setBusy(false)
    }
  }

  return <Modal title={title} onClose={() => { if (!busy) onClose() }}>
    <div className="confirm-content">
      <p>{description}</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions">
        <button data-autofocus className="button button-secondary" onClick={onClose} disabled={busy}>取消</button>
        <button className="button button-danger" onClick={() => void confirm()} disabled={busy}>{busy ? '处理中…' : confirmLabel}</button>
      </div>
    </div>
  </Modal>
}
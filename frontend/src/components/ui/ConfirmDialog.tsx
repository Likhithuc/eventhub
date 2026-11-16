import { AlertTriangle } from 'lucide-react'
import { useEffect, useRef, type ReactNode } from 'react'
import { Spinner } from './Spinner'

interface Props {
  open: boolean
  title: string
  children: ReactNode
  confirmLabel: string
  tone?: 'danger' | 'primary'
  busy?: boolean
  /** e.g. until a required reason is entered */
  confirmDisabled?: boolean
  cancelLabel?: string
  onConfirm: () => void
  onCancel: () => void
}

/** Accessible modal confirmation built on <dialog>. */
export function ConfirmDialog({
  open, title, children, confirmLabel, tone = 'primary', busy, confirmDisabled, cancelLabel = 'Keep it', onConfirm, onCancel,
}: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      className="dialog"
      onCancel={(e) => {
        e.preventDefault()
        if (!busy) onCancel()
      }}
      aria-labelledby="dialog-title"
    >
      <div className="dialog-body">
        {tone === 'danger' && (
          <div className="dialog-icon">
            <AlertTriangle size={22} />
          </div>
        )}
        <h2 id="dialog-title">{title}</h2>
        <div className="dialog-text">{children}</div>
      </div>
      <div className="dialog-actions">
        <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
          {cancelLabel}
        </button>
        <button
          type="button"
          className={`btn ${tone === 'danger' ? 'btn-danger' : 'btn-primary'}`}
          onClick={onConfirm}
          disabled={busy || confirmDisabled}
        >
          {busy && <Spinner small />} {confirmLabel}
        </button>
      </div>
    </dialog>
  )
}

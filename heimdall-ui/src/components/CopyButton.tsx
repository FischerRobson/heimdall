import { useEffect, useState, type MouseEvent } from 'react'

interface Props {
  text: string
  label?: string
  className?: string
}

async function copyToClipboard(text: string) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text)
    return
  }
  // fallback for plain-http hosts, where the async clipboard API is unavailable
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  textarea.remove()
}

export function CopyButton({ text, label = 'copy', className = '' }: Props) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')

  useEffect(() => {
    if (state === 'idle') return
    const timer = setTimeout(() => setState('idle'), 1500)
    return () => clearTimeout(timer)
  }, [state])

  const onClick = (e: MouseEvent) => {
    // buttons often sit inside clickable rows — don't toggle them
    e.stopPropagation()
    copyToClipboard(text)
      .then(() => setState('copied'))
      .catch(() => setState('failed'))
  }

  return (
    <button
      type="button"
      onClick={onClick}
      title="copy to clipboard"
      className={`font-mono text-xs px-2 py-0.5 rounded border transition-colors shrink-0 ${
        state === 'copied'
          ? 'border-green-700 text-green-400'
          : state === 'failed'
          ? 'border-red-800 text-red-400'
          : 'border-gray-700 text-gray-500 hover:border-gray-500 hover:text-gray-300 hover:bg-white/5'
      } ${className}`}
    >
      {state === 'copied' ? 'copied' : state === 'failed' ? 'failed' : label}
    </button>
  )
}

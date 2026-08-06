export function formatVND(amount) {
  return new Intl.NumberFormat('vi-VN').format(amount) + ' ₫'
}

export function formatTime(dateStr) {
  return new Date(dateStr).toLocaleString('vi-VN', {
    hour: '2-digit', minute: '2-digit',
    day: '2-digit', month: '2-digit', year: 'numeric',
  })
}

export function formatTimeShort(dateStr) {
  return new Date(dateStr).toLocaleString('vi-VN', {
    hour: '2-digit', minute: '2-digit',
    day: '2-digit', month: '2-digit',
  })
}

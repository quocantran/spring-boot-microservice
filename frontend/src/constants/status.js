export const STATUS_MAP = {
  PENDING: { label: 'Đang chờ xử lý', icon: '⏳', color: 'var(--warning)' },
  SEATS_RESERVED: { label: 'Đã giữ ghế', icon: '🪑', color: 'var(--primary-light)' },
  PAYMENT_PROCESSED: { label: 'Đã thanh toán', icon: '💳', color: 'var(--success)' },
  CONFIRMED: { label: 'Thành công', icon: '✅', color: 'var(--success)' },
  CANCELLED: { label: 'Thất bại', icon: '❌', color: 'var(--danger)' },
}

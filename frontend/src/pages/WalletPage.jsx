import { useState, useEffect, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { apiFetch } from '../utils/api'
import { formatVND, formatTime } from '../utils/format'

const QUICK_AMOUNTS = [50000, 100000, 200000, 500000]

const STATUS_MAP = {
  PENDING: { label: 'Chờ thanh toán', icon: '⏳', cls: 'status-PENDING' },
  PAID: { label: 'Thành công', icon: '✅', cls: 'status-CONFIRMED' },
  CANCELLED: { label: 'Đã huỷ', icon: '❌', cls: 'status-CANCELLED' },
  EXPIRED: { label: 'Hết hạn', icon: '⏰', cls: 'status-CANCELLED' },
  FAILED: { label: 'Thất bại', icon: '❌', cls: 'status-CANCELLED' },
}

export default function WalletPage({ walletBalance, addToast, onBalanceChange }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const [amount, setAmount] = useState('')
  const [loading, setLoading] = useState(false)
  const [history, setHistory] = useState([])
  const [historyLoading, setHistoryLoading] = useState(true)
  const [cancellingId, setCancellingId] = useState(null)

  const fetchHistory = useCallback(async () => {
    setHistoryLoading(true)
    const { ok, data } = await apiFetch('/topup/history')
    if (ok && data?.data) {
      setHistory(data.data)
    }
    setHistoryLoading(false)
  }, [])

  useEffect(() => {
    fetchHistory()
  }, [fetchHistory])

  useEffect(() => {
    const topupResult = searchParams.get('topup')
    const orderCode = searchParams.get('orderCode') || ''
    if (!topupResult) return

    const resultKey = `${topupResult}:${orderCode}`
    const lastHandledResultKey = sessionStorage.getItem('wallet:lastTopupResult')
    if (lastHandledResultKey === resultKey) return
    sessionStorage.setItem('wallet:lastTopupResult', resultKey)

    if (topupResult === 'success') {
      addToast('success', '🎉 Nạp tiền thành công! Số dư đã được cập nhật.')
      onBalanceChange?.()
      fetchHistory()
      setSearchParams({}, { replace: true })
    } else if (topupResult === 'failed') {
      addToast('error', 'Nạp tiền thất bại hoặc đã bị huỷ.')
      fetchHistory()
      setSearchParams({}, { replace: true })
    } else if (topupResult === 'cancelled') {
      addToast('info', 'Bạn đã huỷ giao dịch nạp tiền.')
      fetchHistory()
      setSearchParams({}, { replace: true })
    } else if (topupResult === 'expired') {
      addToast('error', 'Giao dịch nạp tiền đã hết hạn.')
      fetchHistory()
      setSearchParams({}, { replace: true })
    } else if (topupResult === 'pending') {
      addToast('info', 'Thanh toán chưa hoàn tất, vui lòng kiểm tra lại lịch sử nạp tiền.')
      fetchHistory()
      setSearchParams({}, { replace: true })
    }
  }, [searchParams, setSearchParams, addToast, onBalanceChange, fetchHistory])

  const handleTopup = async () => {
    const numAmount = Number(amount)
    if (!numAmount || numAmount < 1000) {
      addToast('error', 'Số tiền nạp tối thiểu là 1.000 ₫')
      return
    }

    setLoading(true)
    const { ok, data } = await apiFetch('/topup', {
      method: 'POST',
      body: JSON.stringify({ amount: numAmount }),
    })
    setLoading(false)

    if (ok && data?.data?.checkoutUrl) {
      const opened = window.open(data.data.checkoutUrl, '_blank', 'noopener,noreferrer')
      if (!opened) {
        addToast('error', 'Trình duyệt đã chặn tab mới. Vui lòng cho phép popup và thử lại.')
      }
    } else {
      addToast('error', data?.message || 'Tạo link thanh toán thất bại')
    }
  }

  const handleCancel = async (orderCode) => {
    setCancellingId(orderCode)
    const { ok, data } = await apiFetch(`/topup/${orderCode}`, {
      method: 'DELETE',
    })
    setCancellingId(null)

    if (ok) {
      addToast('success', 'Đã huỷ giao dịch nạp tiền')
      fetchHistory()
    } else {
      addToast('error', data?.message || 'Không thể huỷ giao dịch')
    }
  }

  const handleQuickAmount = (val) => {
    setAmount(val.toString())
  }

  const formatInputAmount = (raw) => {
    const num = raw.replace(/\D/g, '')
    setAmount(num)
  }

  return (
    <div className="wallet-page">
      <div className="wallet-hero">
        <div className="wallet-hero-glow" />
        <div className="wallet-hero-content">
          <div className="wallet-hero-icon">💰</div>
          <div className="wallet-hero-label">Số dư ví</div>
          <div className="wallet-hero-balance">
            {walletBalance !== null ? formatVND(walletBalance) : '---'}
          </div>
        </div>
      </div>

      <div className="wallet-layout">
        <div className="wallet-topup-section">
          <div className="card" style={{ hover: 'none' }}>
            <h2 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
              💳 Nạp tiền vào ví
            </h2>

            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: 20, lineHeight: 1.6 }}>
              Chọn số tiền hoặc nhập số tiền tuỳ ý. Thanh toán qua chuyển khoản ngân hàng với payOS.
            </p>

            <div className="wallet-quick-amounts">
              {QUICK_AMOUNTS.map(val => (
                <button
                  key={val}
                  className={`wallet-quick-btn ${Number(amount) === val ? 'active' : ''}`}
                  onClick={() => handleQuickAmount(val)}
                >
                  {formatVND(val)}
                </button>
              ))}
            </div>

            <div className="wallet-input-group">
              <div className="wallet-input-wrapper">
                <input
                  type="text"
                  inputMode="numeric"
                  className="wallet-input"
                  placeholder="Nhập số tiền..."
                  value={amount ? Number(amount).toLocaleString('vi-VN') : ''}
                  onChange={(e) => formatInputAmount(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleTopup()}
                />
                <span className="wallet-input-suffix">₫</span>
              </div>
              {amount && Number(amount) < 1000 && (
                <span className="wallet-input-hint wallet-input-error">
                  Tối thiểu 1.000 ₫
                </span>
              )}
              {amount && Number(amount) >= 1000 && (
                <span className="wallet-input-hint">
                  Bạn sẽ nạp {formatVND(Number(amount))}
                </span>
              )}
            </div>

            <button
              className="btn btn-primary"
              style={{ width: '100%', justifyContent: 'center', marginTop: 8 }}
              disabled={loading || !amount || Number(amount) < 1000}
              onClick={handleTopup}
            >
              {loading ? (
                <>
                  <span className="spinner" style={{ width: 18, height: 18, marginRight: 0, borderWidth: 2 }} />
                  Đang tạo link...
                </>
              ) : (
                <>🔗 Tạo link thanh toán</>
              )}
            </button>

            <div className="wallet-payment-info">
              <div className="wallet-payment-info-icon">🏦</div>
              <div>
                <div style={{ fontWeight: 600, marginBottom: 2 }}>Thanh toán qua payOS</div>
                <div style={{ color: 'var(--text-muted)', fontSize: '0.78rem' }}>
                  Quét mã QR hoặc chuyển khoản ngân hàng. Tiền sẽ được cộng tự động sau khi thanh toán.
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="wallet-history-section">
          <h2 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
            📋 Lịch sử nạp tiền
            {!historyLoading && (
              <span style={{
                fontSize: '0.72rem', fontWeight: 600, background: 'rgba(148,163,184,0.1)',
                color: 'var(--text-muted)', padding: '3px 10px', borderRadius: 20,
              }}>
                {history.length} giao dịch
              </span>
            )}
          </h2>

          {historyLoading ? (
            <div className="loading">
              <div className="spinner" />
              Đang tải...
            </div>
          ) : history.length === 0 ? (
            <div className="wallet-history-empty">
              <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>📭</div>
              <div style={{ color: 'var(--text-muted)' }}>Chưa có giao dịch nạp tiền nào</div>
            </div>
          ) : (
            <div className="wallet-history-list">
              {history.map(item => {
                const st = STATUS_MAP[item.status] || STATUS_MAP.FAILED
                return (
                  <div key={item.id} className="wallet-history-item card">
                    <div className="wallet-history-item-top">
                      <div>
                        <div className="wallet-history-amount">
                          +{formatVND(item.amount)}
                        </div>
                        <div className="wallet-history-date">
                          {formatTime(item.createdAt)}
                        </div>
                      </div>
                      <span className={`status-badge ${st.cls}`}>
                        {st.icon} {st.label}
                      </span>
                    </div>

                    <div className="wallet-history-details">
                      <div className="wallet-history-detail-row">
                        <span className="wallet-history-detail-label">Mã đơn</span>
                        <span className="wallet-history-detail-value">#{item.orderCode}</span>
                      </div>
                      {item.status === 'PAID' && item.counterAccountBankName && (
                        <div className="wallet-history-detail-row">
                          <span className="wallet-history-detail-label">Ngân hàng</span>
                          <span className="wallet-history-detail-value">{item.counterAccountBankName}</span>
                        </div>
                      )}
                      {item.status === 'PAID' && item.transactionReference && (
                        <div className="wallet-history-detail-row">
                          <span className="wallet-history-detail-label">Mã GD</span>
                          <span className="wallet-history-detail-value" style={{ fontFamily: 'monospace', fontSize: '0.78rem' }}>
                            {item.transactionReference}
                          </span>
                        </div>
                      )}
                      {item.status === 'PAID' && item.paidAt && (
                        <div className="wallet-history-detail-row">
                          <span className="wallet-history-detail-label">Thanh toán lúc</span>
                          <span className="wallet-history-detail-value">{formatTime(item.paidAt)}</span>
                        </div>
                      )}
                    </div>

                    {item.status === 'PENDING' && (
                      <div className="wallet-history-actions">
                        <a
                          href={item.checkoutUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="btn btn-primary"
                          style={{ fontSize: '0.8rem', padding: '8px 16px' }}
                        >
                          💳 Thanh toán
                        </a>
                        <button
                          className="btn btn-secondary"
                          style={{ fontSize: '0.8rem', padding: '8px 16px' }}
                          disabled={cancellingId === item.orderCode}
                          onClick={() => handleCancel(item.orderCode)}
                        >
                          {cancellingId === item.orderCode ? 'Đang huỷ...' : '✕ Huỷ'}
                        </button>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

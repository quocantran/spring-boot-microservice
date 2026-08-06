import { useState } from 'react'
import { apiFetch, setToken } from '../utils/api'

export default function LoginPage({ onLogin, addToast }) {
  const [isRegister, setIsRegister] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      const endpoint = isRegister ? '/auth/register' : '/auth/login'
      const body = isRegister ? { name, email, password } : { email, password }
      const { ok, data } = await apiFetch(endpoint, { method: 'POST', body: JSON.stringify(body) })
      if (ok && data.access_token) {
        setToken(data.access_token)
        onLogin(data.user)
        addToast('success', `Xin chào, ${data.user.name}!`)
      } else {
        addToast('error', data?.message || 'Đăng nhập thất bại')
      }
    } catch (err) {
      addToast('error', `Lỗi kết nối: ${err.message}`)
    }
    setLoading(false)
  }

  const inputStyle = {
    width: '100%',
    padding: '10px 14px',
    background: 'var(--bg)',
    border: '1px solid var(--border)',
    borderRadius: 8,
    color: 'var(--text)',
    fontSize: '0.9rem',
    fontFamily: 'inherit',
    outline: 'none',
    boxSizing: 'border-box',
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div className="card" style={{ width: '100%', maxWidth: 420, padding: 40 }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{ fontSize: '2.5rem', marginBottom: 8 }}>🎬</div>
          <h1 style={{
            fontSize: '1.5rem', fontWeight: 800,
            background: 'linear-gradient(135deg, var(--primary-light), var(--accent))',
            WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
          }}>
            Movie Booking
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: 4 }}>
            {isRegister ? 'Tạo tài khoản mới' : 'Đăng nhập để đặt vé'}
          </p>
        </div>

        <form onSubmit={handleSubmit}>
          {isRegister && (
            <div style={{ marginBottom: 16 }}>
              <label style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Họ tên</label>
              <input type="text" value={name} onChange={e => setName(e.target.value)} required
                placeholder="Nguyễn Văn A" style={inputStyle} />
            </div>
          )}
          <div style={{ marginBottom: 16 }}>
            <label style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Email</label>
            <input type="email" value={email} onChange={e => setEmail(e.target.value)} required
              placeholder="email@example.com" style={inputStyle} />
          </div>
          <div style={{ marginBottom: 24 }}>
            <label style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Mật khẩu</label>
            <input type="password" value={password} onChange={e => setPassword(e.target.value)} required
              placeholder="••••••" style={inputStyle} />
          </div>
          <button type="submit" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }} disabled={loading}>
            {loading ? '⏳ Đang xử lý...' : isRegister ? '📝 Đăng ký' : '🔐 Đăng nhập'}
          </button>
        </form>

        <div style={{ textAlign: 'center', marginTop: 20 }}>
          <button onClick={() => setIsRegister(!isRegister)}
            style={{ background: 'none', border: 'none', color: 'var(--primary-light)', cursor: 'pointer', fontFamily: 'inherit', fontSize: '0.85rem' }}>
            {isRegister ? '← Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Đăng ký →'}
          </button>
        </div>

        <div style={{ marginTop: 24, padding: 16, background: 'rgba(99, 102, 241, 0.05)', borderRadius: 8, border: '1px solid rgba(99, 102, 241, 0.15)' }}>
          <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: 6 }}>📌 Tài khoản demo (mật khẩu: 123456)</p>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', lineHeight: 1.8 }}>
            <div>nguyenvana@email.com — <span style={{ color: 'var(--accent)', fontWeight: 600 }}>👑 ADMIN</span> — Ví: 500,000 ₫</div>
            <div>tranthib@email.com — Ví: 50,000 ₫ <span style={{ color: 'var(--danger)' }}>⚠️ không đủ tiền</span></div>
            <div>hoangvane@email.com — Ví: 3,000,000 ₫</div>
          </div>
        </div>
      </div>
    </div>
  )
}

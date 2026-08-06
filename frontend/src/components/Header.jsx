import { formatVND } from '../utils/format'
import { Link, useLocation } from 'react-router-dom'

export default function Header({ user, walletBalance, onLogout }) {
  const isAdmin = user?.role === 'ADMIN'
  const location = useLocation()
  const pathname = location.pathname

  const isMoviesActive = pathname === '/movies' || pathname.startsWith('/movies/')
  const isBookingsActive = pathname === '/bookings' || pathname.startsWith('/bookings/')
  const isRecommendationsActive = pathname === '/recommendations'
  const isCreateMovieActive = pathname === '/movies/new'
  const isWalletActive = pathname === '/wallet'

  return (
    <header className="header">
      <div className="container header-content">
        <Link to="/movies" className="logo" style={{ textDecoration: 'none' }}>
          🎬 Movie Booking
        </Link>

        <nav className="nav-tabs">
          <Link
            to="/movies"
            className={`nav-tab ${isMoviesActive ? 'active' : ''}`}
          >
            Phim
          </Link>

          {!isAdmin && (
            <Link
              to="/recommendations"
              className={`nav-tab ${isRecommendationsActive ? 'active' : ''}`}
            >
              🤖 Gợi ý cho bạn
            </Link>
          )}

          <Link
            to="/bookings"
            className={`nav-tab ${isBookingsActive ? 'active' : ''}`}
          >
            Đơn đặt vé
          </Link>

          {!isAdmin && (
            <Link
              to="/wallet"
              className={`nav-tab ${isWalletActive ? 'active' : ''}`}
              style={{
                background: isWalletActive ? 'rgba(16, 185, 129, 0.1)' : undefined,
                borderColor: isWalletActive ? 'var(--success)' : undefined,
                color: isWalletActive ? 'var(--success)' : undefined,
              }}
            >
              💰 Ví
            </Link>
          )}

          {isAdmin && (
            <Link
              to="/movies/new"
              className={`nav-tab ${isCreateMovieActive ? 'active' : ''}`}
              style={{
                background: isCreateMovieActive ? 'rgba(245, 158, 11, 0.15)' : 'transparent',
                borderColor: isCreateMovieActive ? 'var(--accent)' : 'transparent',
                color: isCreateMovieActive ? 'var(--accent)' : undefined,
              }}
            >
              ➕ Thêm phim
            </Link>
          )}
        </nav>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {walletBalance !== null && (
            <Link
              to="/wallet"
              style={{
                fontSize: '0.82rem',
                color: 'var(--success)',
                background: 'rgba(16, 185, 129, 0.1)',
                border: '1px solid rgba(16, 185, 129, 0.25)',
                padding: '4px 10px',
                borderRadius: 20,
                fontWeight: 600,
                letterSpacing: '0.01em',
                textDecoration: 'none',
                transition: 'all 0.2s',
              }}
            >
              💰 {formatVND(walletBalance)}
            </Link>
          )}

          <span style={{
            fontSize: '0.7rem',
            padding: '3px 8px',
            borderRadius: 12,
            fontWeight: 700,
            background: isAdmin ? 'rgba(245, 158, 11, 0.15)' : 'rgba(99, 102, 241, 0.15)',
            color: isAdmin ? 'var(--accent)' : 'var(--primary-light)',
            border: `1px solid ${isAdmin ? 'rgba(245, 158, 11, 0.3)' : 'rgba(99, 102, 241, 0.3)'}`,
          }}>
            {isAdmin ? '👑 ADMIN' : '👤 USER'}
          </span>

          <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            {user.name}
          </span>
          <button
            onClick={onLogout}
            style={{
              background: 'none',
              border: '1px solid var(--border)',
              color: 'var(--text-muted)',
              padding: '6px 12px',
              borderRadius: 6,
              cursor: 'pointer',
              fontSize: '0.8rem',
              fontFamily: 'inherit',
            }}
          >
            Đăng xuất
          </button>
        </div>
      </div>
    </header>
  )
}


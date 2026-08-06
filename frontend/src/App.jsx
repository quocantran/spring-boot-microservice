import { useState, useEffect, useCallback } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import { getToken, clearToken, apiFetch } from './utils/api'

import Toast from './components/Toast'
import Header from './components/Header'

import LoginPage from './pages/LoginPage'
import MovieListPage from './pages/MovieListPage'
import BookingPage from './pages/BookingPage'
import BookingDetailPage from './pages/BookingDetailPage'
import BookingsPage from './pages/BookingsPage'
import CreateMoviePage from './pages/CreateMoviePage'
import CreateShowtimePage from './pages/CreateShowtimePage'
import RecommendationsPage from './pages/RecommendationsPage'
import WalletPage from './pages/WalletPage'

export default function App() {
  const [user, setUser] = useState(null)
  const [toasts, setToasts] = useState([])
  const [authChecked, setAuthChecked] = useState(false)
  const [walletBalance, setWalletBalance] = useState(null)
  const navigate = useNavigate()

  const addToast = (type, message) => {
    const id = Date.now()
    setToasts(prev => [...prev, { id, type, message }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000)
  }

  const fetchWalletBalance = useCallback(() => {
    if (!getToken()) return
    apiFetch('/wallets/me').then(({ ok, data }) => {
      if (ok && data) setWalletBalance(data.balance)
    })
  }, [])

  useEffect(() => {
    const token = getToken()
    if (token) {
      apiFetch('/auth/me').then(({ ok, data }) => {
        if (ok) setUser(data)
        else clearToken()
      }).finally(() => setAuthChecked(true))
    } else {
      setAuthChecked(true)
    }
  }, [])

  useEffect(() => {
    if (!user) return
    fetchWalletBalance()
    const interval = setInterval(fetchWalletBalance, 10000)
    return () => clearInterval(interval)
  }, [user, fetchWalletBalance])

  const handleLogin = (userData) => setUser(userData)

  const handleLogout = () => {
    clearToken()
    setUser(null)
    setWalletBalance(null)
    navigate('/movies')
  }

  const handleSelectMovie = (movieOrId) => {
    const movieId = typeof movieOrId === 'string' ? movieOrId : movieOrId?.id
    if (!movieId) return
    navigate(`/movies/${movieId}/booking`, {
      state: typeof movieOrId === 'object' ? { movie: movieOrId } : undefined,
    })
  }

  const handleBookingCreated = (bookingId) => {
    navigate(`/bookings/${bookingId}`)
    setTimeout(fetchWalletBalance, 3000)
  }

  const handleViewDetail = (bookingId) => {
    navigate(`/bookings/${bookingId}`)
  }

  function BookingRoute() {
    const { movieId } = useParams()
    const location = useLocation()
    if (!movieId) return <Navigate to="/movies" replace />
    return (
      <BookingPage
        movieId={movieId}
        initialMovie={location.state?.movie || null}
        onBack={() => navigate('/movies')}
        addToast={addToast}
        onBookingCreated={handleBookingCreated}
      />
    )
  }

  function BookingDetailRoute() {
    const { bookingId } = useParams()
    if (!bookingId) return <Navigate to="/bookings" replace />
    return (
      <BookingDetailPage
        bookingId={bookingId}
        onBack={() => navigate('/bookings')}
      />
    )
  }

  function CreateShowtimeRoute() {
    const { movieId } = useParams()
    if (!movieId) return <Navigate to="/movies" replace />
    return (
      <CreateShowtimePage
        movieId={movieId}
        addToast={addToast}
        onBack={() => navigate(`/movies/${movieId}/booking`)}
      />
    )
  }

  if (!authChecked) {
    return <div className="loading"><div className="spinner" />Đang kiểm tra phiên đăng nhập...</div>
  }

  if (!user) {
    return (
      <>
        <Toast toasts={toasts} />
        <LoginPage onLogin={handleLogin} addToast={addToast} />
      </>
    )
  }

  return (
    <div>
      <Toast toasts={toasts} />

      <Header
        user={user}
        walletBalance={walletBalance}
        onLogout={handleLogout}
      />

      <main className="container" style={{ paddingTop: 32, paddingBottom: 64 }}>
        <Routes>
          <Route path="/" element={<Navigate to="/movies" replace />} />
          <Route path="/movies" element={<MovieListPage onSelectMovie={handleSelectMovie} />} />
          <Route path="/movies/:movieId/booking" element={<BookingRoute />} />
          <Route path="/bookings" element={<BookingsPage onViewDetail={handleViewDetail} />} />
          <Route path="/bookings/:bookingId" element={<BookingDetailRoute />} />

          {user?.role === 'ADMIN' && (
            <>
              <Route
                path="/movies/new"
                element={<CreateMoviePage addToast={addToast} onBack={() => navigate('/movies')} />}
              />
              <Route path="/movies/:movieId/showtimes/new" element={<CreateShowtimeRoute />} />
            </>
          )}

          {user?.role !== 'ADMIN' && (
            <>
              <Route
                path="/recommendations"
                element={<RecommendationsPage onSelectMovie={handleSelectMovie} />}
              />
              <Route
                path="/wallet"
                element={
                  <WalletPage
                    walletBalance={walletBalance}
                    addToast={addToast}
                    onBalanceChange={fetchWalletBalance}
                  />
                }
              />
            </>
          )}

          <Route path="*" element={<Navigate to="/movies" replace />} />
        </Routes>
      </main>
    </div>
  )
}

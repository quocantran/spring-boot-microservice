# Frontend

> Giao diện React + Vite cho khách hàng đăng nhập, duyệt phim, đặt vé và xem gợi ý AI

## Luồng sử dụng

1. 🔐 **Đăng nhập / Đăng ký** — Email + mật khẩu → nhận JWT token, tự động tạo ví 200,000 ₫ khi đăng ký
2. 🎬 **Duyệt phim** — Danh sách phim đang chiếu
3. 🕐 **Chọn suất chiếu** — Chọn phòng, giờ chiếu
4. 🪑 **Chọn ghế** — Bản đồ ghế trực quan (5 hàng × 8 cột)
5. 🎫 **Đặt vé** — Xác nhận và tạo đơn
6. 📊 **Theo dõi trạng thái** — Redirect sang trang chi tiết, auto-polling saga:
   - ⏳ Đang chờ xử lý (PENDING)
   - 🪑 Đã giữ ghế (SEATS_RESERVED)
   - 💳 Đã thanh toán (PAYMENT_PROCESSED)
   - ✅ Thành công (CONFIRMED)
   - ❌ Thất bại (CANCELLED — không đủ tiền hoặc ghế không khả dụng)
7. 🤖 **Gợi ý AI** — Xem các phim được AI gợi ý dựa trên lịch sử đặt vé

## Auth

- JWT token lưu trong `localStorage`
- Tự động gắn `Authorization: Bearer <token>` cho mọi request
- Nếu token hết hạn (401) → tự động logout + redirect về login
- Khi reload → gọi `/auth/me` để kiểm tra phiên

## Wallet (Số dư ví)

- Hiển thị số dư ví trực tiếp trên header (badge xanh)
- Tự động cập nhật mỗi 10 giây
- Refresh sau khi đặt vé (sau 3 giây)
- Gọi API `GET /wallets/me` qua Gateway

## Tech Stack

- **Framework**: React 18 + Vite 5
- **Styling**: Vanilla CSS (dark mode, modern design)
- **Port**: 3000

## Cấu trúc

```
src/
├── main.jsx                  # Entry point
├── App.jsx                   # Orchestrator — routing + state management
├── index.css                 # Global styles (dark mode, design system)
│
├── utils/
│   ├── api.js                # API fetch, auth token helpers (getToken, setToken, clearToken, apiFetch)
│   └── format.js             # Formatting utilities (formatVND, formatTime, formatTimeShort)
│
├── constants/
│   └── status.js             # Booking status mapping (STATUS_MAP)
│
├── components/
│   ├── Header.jsx            # Header — logo, nav tabs, wallet balance, user info, logout
│   └── Toast.jsx             # Toast notification component
│
└── pages/
    ├── LoginPage.jsx          # Đăng nhập / Đăng ký
    ├── MovieListPage.jsx      # Danh sách phim đang chiếu
    ├── CreateMoviePage.jsx    # Tạo phim mới (ADMIN)
    ├── RecommendationsPage.jsx # Gợi ý phim AI
    ├── BookingPage.jsx        # Chọn suất chiếu + ghế + xác nhận đặt vé
    ├── BookingDetailPage.jsx  # Chi tiết đơn + saga progress tracking
    └── BookingsPage.jsx       # Lịch sử đơn đặt vé (auto-polling)
```

## Environment Variables

```bash
cp .env.local.example .env.local
```

| Variable | Mô tả | Mặc định |
|----------|-------|----------|
| `VITE_API_URL` | URL của API Gateway | `http://localhost:8080` |

## Dev

```bash
cd frontend
npm install
npm run dev
```
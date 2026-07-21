# WearWise — Tủ đồ thông minh 👕

Ứng dụng quản lý tủ đồ cá nhân: quản lý quần áo, phối outfit, lên lịch mặc, gợi ý theo thời tiết và thống kê tần suất sử dụng.

## Cấu trúc dự án

```
wearwise/
├── src/            # Backend — Spring Boot (Java 17, MySQL)
├── frontend/       # Frontend — React + Vite (giao diện neobrutalism)
├── docker-compose.yml
└── pom.xml
```

## Chạy dự án

### 1. Database (MySQL qua Docker)

```bash
docker compose up -d mysql
```

### 2. Backend (cổng 8080)

```bash
./mvnw spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html

### 3. Frontend (cổng 5173)

```bash
cd frontend
npm install
npm run dev
```

Mở http://localhost:5173 — dev server proxy sẵn `/api` sang backend nên không cần cấu hình gì thêm.

## Tính năng

| Tính năng | Backend | Frontend |
|---|---|---|
| Đăng ký / đăng nhập (Bearer token) | `/api/auth/*` | Trang Đăng nhập |
| Quản lý quần áo (CRUD, lọc theo danh mục/mùa/phong cách/tình trạng, yêu thích, đánh dấu đã mặc) | `/api/clothing-items` | Tủ đồ |
| Phối outfit từ các món đồ | `/api/outfits` | Outfit |
| Lịch phối đồ theo ngày (lên kế hoạch, đánh dấu đã mặc) | `/api/outfit-plans` | Lịch phối đồ |
| Gợi ý outfit theo thời tiết (Open-Meteo, không cần API key) | `/api/outfits/suggestions` | Gợi ý thời tiết |
| Thống kê tủ đồ (phân bố, mặc nhiều nhất, lâu chưa mặc) | `/api/statistics` | Thống kê |

### Logic gợi ý theo thời tiết

Frontend lấy thời tiết hiện tại từ Open-Meteo (theo thành phố người dùng chọn), rồi gọi
`GET /api/outfits/suggestions?temperature=..&raining=..`. Backend chấm điểm từng outfit:

- Nhiệt độ < 20°C → ưu tiên outfit mùa đông; ≥ 26°C → mùa hè; outfit quanh năm luôn được cộng nhẹ
- Trời lạnh/mưa → cộng điểm outfit có áo khoác; trời mưa mà thiếu áo khoác → trừ điểm
- Cộng điểm outfit yêu thích, outfit lâu chưa mặc, outfit có đủ đồ ở trạng thái sẵn sàng

## Test

```bash
./mvnw test        # backend (77 tests)
cd frontend && npm run build   # kiểm tra build frontend
```

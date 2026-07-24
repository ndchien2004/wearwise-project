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
| Xác thực JWT: đăng ký/đăng nhập, refresh token, quên & đặt lại mật khẩu, đổi mật khẩu | `/api/auth/*` | Đăng nhập, Đặt lại mật khẩu |
| Quản lý quần áo (CRUD, lọc theo danh mục/mùa/phong cách/tình trạng, yêu thích, đánh dấu đã mặc) | `/api/clothing-items` | Tủ đồ |
| Phối outfit từ các món đồ | `/api/outfits` | Outfit |
| Lịch phối đồ theo ngày (lên kế hoạch, đánh dấu đã mặc) | `/api/outfit-plans` | Lịch phối đồ |
| Gợi ý outfit theo thời tiết (Open-Meteo, không cần API key) | `/api/outfits/suggestions` | Gợi ý thời tiết |
| Thống kê tủ đồ (phân bố, mặc nhiều nhất, lâu chưa mặc) | `/api/statistics` | Thống kê |

### Xác thực (JWT)

Access token là JWT HS256 ký bằng thư viện JJWT, mang các claim `jti`, `iss`, `sub`, `role`, `iat`, `exp`.
Token sống 15 phút; phiên dài được giữ bằng refresh token 7 ngày (chuỗi ngẫu nhiên, DB chỉ lưu bản băm SHA-256).

| Endpoint | Công khai | Mô tả |
|---|:---:|---|
| `POST /api/auth/register` | ✅ | Đăng ký (cần username, email, mật khẩu mạnh) |
| `POST /api/auth/login` | ✅ | Đăng nhập, trả access + refresh token |
| `POST /api/auth/refresh` | ✅ | Đổi refresh token lấy cặp token mới (xoay vòng) |
| `POST /api/auth/forgot-password` | ✅ | Gửi link đặt lại mật khẩu qua email |
| `GET /api/auth/reset-password/validate` | ✅ | Kiểm tra link còn hiệu lực |
| `POST /api/auth/reset-password` | ✅ | Đặt mật khẩu mới bằng mã trong email |
| `GET /api/auth/me` | ❌ | Thông tin tài khoản đang đăng nhập |
| `PUT /api/auth/me/email` | ❌ | Thêm/đổi email (phải nhập lại mật khẩu) |
| `POST /api/auth/change-password` | ❌ | Đổi mật khẩu khi đang đăng nhập |
| `POST /api/auth/logout` | ❌ | Thu hồi access token + refresh token |

Các biện pháp bảo vệ:

- **Xoay vòng refresh token** — mỗi lần dùng, token cũ bị thu hồi. Nếu một token đã thu hồi lại được
  dùng lần nữa (dấu hiệu bị đánh cắp), toàn bộ phiên của tài khoản đó bị hủy.
- **Chống dò mật khẩu** — sai 5 lần liên tiếp thì khóa tài khoản 15 phút (HTTP 423 kèm `Retry-After`).
- **Mật khẩu mạnh** — tối thiểu 8 ký tự, có cả chữ và số, không khoảng trắng (`@StrongPassword`);
  giao diện có thanh đo độ mạnh.
- **Không lộ danh sách email** — `/forgot-password` luôn trả cùng một thông điệp dù email có tồn tại hay không.
- **Mã đặt lại dùng một lần**, hết hạn sau 30 phút, tối đa 5 yêu cầu/giờ/tài khoản; DB chỉ lưu bản băm.
- **Đổi/đặt lại mật khẩu làm mọi phiên cũ hết hiệu lực** — refresh token bị thu hồi, và access token
  phát hành trước mốc `passwordChangedAt` bị từ chối.

#### Tài khoản tạo trước khi có tính năng này

Cột `email` cho phép NULL nên tài khoản cũ vẫn đăng nhập bình thường, nhưng chưa dùng được chức năng
quên mật khẩu. Trong ứng dụng, bấm vào tên người dùng ở thanh bên (có chấm đỏ nhắc nếu thiếu email)
để mở hộp thoại **Tài khoản** và bổ sung email — cần nhập lại mật khẩu để xác nhận.

#### Email đặt lại mật khẩu

Chưa cấu hình SMTP thì ứng dụng **không** gửi mail mà ghi thẳng link đặt lại ra log — đủ để chạy và
demo ngay khi dev. Muốn gửi mail thật, điền `spring.mail.*` trong `src/main/resources/application-secrets.properties`
(file đã gitignore, sẵn khung để điền; với Gmail phải dùng App password 16 ký tự chứ không phải mật khẩu thường).

### Logic gợi ý theo thời tiết

Frontend lấy thời tiết hiện tại từ Open-Meteo (theo thành phố người dùng chọn), rồi gọi
`GET /api/outfits/suggestions?temperature=..&raining=..`. Backend chấm điểm từng outfit:

- Nhiệt độ < 20°C → ưu tiên outfit mùa đông; ≥ 26°C → mùa hè; outfit quanh năm luôn được cộng nhẹ
- Trời lạnh/mưa → cộng điểm outfit có áo khoác; trời mưa mà thiếu áo khoác → trừ điểm
- Cộng điểm outfit yêu thích, outfit lâu chưa mặc, outfit có đủ đồ ở trạng thái sẵn sàng

## Test

```bash
./mvnw test        # backend (161 tests)
cd frontend && npm run build   # kiểm tra build frontend
```

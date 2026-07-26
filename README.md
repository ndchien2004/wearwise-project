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
| Lịch sử mặc theo ngày (mặc gì nhiều nhất trong tháng, số ngày có mặc) | `/api/statistics/history` | Trang chủ |
| Chia sẻ outfit/món đồ sang tài khoản khác bằng mã 8 ký tự | `/api/shares` | Chia sẻ |

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
| `POST /api/auth/change-password` | ❌ | Đổi mật khẩu khi đang đăng nhập |
| `POST /api/auth/logout` | ❌ | Thu hồi access token + refresh token |

`POST /api/auth/login` nhận **tên đăng nhập hoặc email** ở trường `username` — tên đăng nhập
không được chứa ký tự `@` nên hai không gian tên không bao giờ đụng nhau.

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

#### Email là cố định

Email được chốt một lần khi đăng ký và **không có API nào đổi được** — đây là địa chỉ duy nhất nhận
link đặt lại mật khẩu, nên khóa lại để kẻ chiếm được phiên đăng nhập không thể trỏ email khôi phục
sang hộp thư của mình.

Hệ quả: tài khoản tạo từ trước khi email trở thành bắt buộc (cột `email` cho phép NULL) vẫn đăng nhập
bình thường nhưng **vĩnh viễn không dùng được chức năng quên mật khẩu**, và không thể bổ sung email
qua giao diện. Cách duy nhất là sửa thẳng trong DB:

```sql
UPDATE app_users SET email = 'ban@gmail.com' WHERE username = 'ten-dang-nhap';
```

#### Email đặt lại mật khẩu

Chưa cấu hình SMTP thì ứng dụng **không** gửi mail mà ghi thẳng link đặt lại ra log — đủ để chạy và
demo ngay khi dev. Muốn gửi mail thật, điền `spring.mail.*` trong `src/main/resources/application-secrets.properties`
(file đã gitignore, sẵn khung để điền; với Gmail phải dùng App password 16 ký tự chứ không phải mật khẩu thường).

### Nhật ký mặc

Mỗi lượt mặc được ghi thành một dòng trong bảng `wear_logs` — một dòng cho outfit và một dòng cho
mỗi món trong bộ. Đây là nguồn sự thật của lịch sử; `wearCount` và `lastWornAt` trên món đồ/outfit
chỉ là bản tóm tắt được cập nhật kèm theo.

- **Mỗi ngày tối đa một lượt** cho mỗi món và mỗi outfit — bảo đảm bằng ràng buộc UNIQUE
  `(owner, clothing_item, worn_on)` và `(owner, outfit, worn_on)`, không phải bằng cách so `lastWornAt`.
- **Bỏ đánh dấu "đã mặc"** ở lịch sẽ xoá đúng những dòng do kế hoạch đó sinh ra, trừ lại `wearCount`
  và đọc lại `lastWornAt` từ dòng gần nhất còn lại.
- Tài khoản có sẵn dữ liệu từ trước được dựng lại một dòng `LEGACY` tại `lastWornAt` ngay lần khởi
  động đầu tiên, nên hoàn tác và màn hình lịch sử vẫn có dữ liệu để đọc.

### Logic gợi ý theo thời tiết

Frontend lấy thời tiết hiện tại từ Open-Meteo (theo thành phố người dùng chọn), rồi gọi
`GET /api/outfits/suggestions?temperature=..&raining=..`. Backend chấm điểm từng outfit:

- Nhiệt độ < 20°C → ưu tiên outfit mùa đông; ≥ 26°C → mùa hè; outfit quanh năm luôn được cộng nhẹ
- Trời lạnh/mưa → cộng điểm outfit có áo khoác; trời mưa mà thiếu áo khoác → trừ điểm
- Cộng điểm outfit yêu thích, outfit lâu chưa mặc, outfit có đủ đồ ở trạng thái sẵn sàng

### Chia sẻ trang phục

Chủ sở hữu bấm **🔗 Chia sẻ** ở trang chi tiết outfit/món đồ để lấy mã 8 ký tự (bảng chữ bỏ `0 O 1 I L`
cho khỏi đọc nhầm). Người nhận vào trang **Chia sẻ**, nhập mã, xem trước rồi chép về tủ đồ mình.

- Bản sao **độc lập hoàn toàn**: người chia sẻ sửa hay xóa đồ của mình không ảnh hưởng tới bản đã chép.
- Bản sao luôn "sạch": `wearCount = 0`, chưa từng mặc, trạng thái sẵn sàng, bỏ đánh dấu yêu thích.
- Chép cùng một mã hai lần không nhân bản món cũ — món trùng cả tên, loại và ảnh sẽ được tái dùng.
- Mã có thể **thu hồi** bất cứ lúc nào; bản người khác đã chép vẫn còn.
- Xóa outfit/món đồ sẽ gỡ luôn mã chia sẻ trỏ tới nó.

## Test

```bash
./mvnw test        # backend (229 tests)
cd frontend && npm run build   # kiểm tra build frontend
```

## Deploy

Frontend là SPA tĩnh nên hợp với Cloudflare Pages; backend cần JVM + MySQL nên phải đặt ở nơi khác
(Render, Railway, Fly.io, VPS...). Hai phần nối với nhau qua ba cấu hình:

| Nơi đặt | Biến | Giá trị |
|---|---|---|
| Cloudflare Pages (build) | `VITE_API_BASE_URL` | `https://<domain-backend>` |
| Backend | `WEARWISE_CORS_ALLOWED_ORIGINS` | `https://<domain-pages>,https://*.<domain-pages>` |
| Backend | `MAIL_RESET_URL_BASE` | `https://<domain-pages>/reset-password` |

### Cloudflare Pages

| Thiết lập | Giá trị |
|---|---|
| Root directory | `frontend` |
| Build command | `npm run build` |
| Build output directory | `dist` |
| Biến môi trường | `VITE_API_BASE_URL`, `NODE_VERSION=20` |

`VITE_API_BASE_URL` được **nhúng vào bundle lúc build**, không đọc lúc chạy — đổi giá trị thì phải
deploy lại. Bỏ trống thì frontend gọi API cùng origin (chế độ dev, hoặc khi backend phục vụ luôn
file tĩnh).

`frontend/public/_redirects` giữ SPA fallback (`/* /index.html 200`); thiếu file này thì mở thẳng
`/wardrobe/5` hay bấm F5 ở trang con sẽ ra 404 của Cloudflare.

### Bắt buộc: backend phải chạy HTTPS

Trang Pages chạy trên `https://`, trình duyệt sẽ chặn mọi request tới backend `http://`
(mixed content) — API sẽ "im lặng" không gọi được. Backend bắt buộc có chứng chỉ TLS.

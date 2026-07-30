# WearWise — Tủ đồ thông minh 👕

[![CI](https://github.com/ndchien2004/wearwise-project/actions/workflows/ci.yml/badge.svg)](https://github.com/ndchien2004/wearwise-project/actions/workflows/ci.yml)

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

Lệnh này chạy sẵn với profile `dev` (khai báo trong `pom.xml`), nên không cần cấu hình gì thêm.

**Chạy trong IntelliJ**: chọn cấu hình **`WearwiseApplication [dev]`** trong danh sách Run — nó
nằm sẵn trong thư mục `.run/` của repo. Bấm Run thẳng vào class `WearwiseApplication` sẽ chạy
*không* profile nào, và ứng dụng dừng ngay với thông báo thiếu khóa ký: thiết lập profile `dev`
trong `pom.xml` chỉ áp dụng cho lệnh Maven, IntelliJ không đọc nó.

Bản đóng gói `java -jar` cũng **không** có profile `dev` và sẽ dừng nếu thiếu
`WEARWISE_AUTH_TOKEN_SECRET` — xem [Khóa ký JWT](#khóa-ký-jwt).

Cấu trúc bảng do **Flyway** dựng từ `src/main/resources/db/migration/`, Hibernate chỉ được
`validate`. Muốn đổi bảng thì thêm file `V<n>__mo_ta.sql` mới, không sửa file cũ và không
trông chờ Hibernate tự sửa.

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
| Kế hoạch mặc nhiều ngày do AI sinh theo mục đích người dùng viết | `/api/ai/wear-plans`, `/api/wear-plans` | Lịch phối đồ, Trang chủ |
| Gợi ý outfit theo thời tiết (Open-Meteo, không cần API key) | `/api/outfits/suggestions` | Gợi ý thời tiết |
| Thống kê tủ đồ (phân bố, mặc nhiều nhất, lâu chưa mặc) | `/api/statistics` | Thống kê |
| Lịch sử mặc theo ngày (mặc gì nhiều nhất trong tháng, số ngày có mặc) | `/api/statistics/history` | Trang chủ |
| Nhập / xuất toàn bộ tủ đồ bằng CSV, có file mẫu | `/api/wardrobe/{export,template,import}` | Tủ đồ |
| Chia sẻ outfit/món đồ sang tài khoản khác bằng mã 8 ký tự | `/api/shares` | Chia sẻ |
| Vận hành: tài khoản, hạn mức, nhật ký kiểm toán (chỉ `ROLE_ADMIN`) | `/api/admin/*` | Quản trị |

### Bộ đồ "chưa mặc được"

Một bộ có món **đang giặt**, **hư hỏng** hay **chưa dùng được** sẽ nằm ở mục *Chưa mặc được* dưới
trang Outfit, kèm tên món đang vướng — thay vì nằm lẫn ở trên rồi báo lỗi lúc bấm "Mặc". Gợi ý
thời tiết và xếp hạng AI cũng bỏ qua những bộ này: gợi ý ra thứ không mặc được thì cũng vô dụng.

Đồ đang giặt chỉ chặn **hôm nay**. Bộ vẫn còn đủ món nên vẫn lên lịch cho ngày sau được — tới lúc
đó rất có thể đã giặt xong. Chỉ bộ **thiếu món** (có món bị ẩn khỏi tủ) mới không lên lịch được,
vì đó là hỏng thật, phải sửa bộ mới dùng lại.

Món lẻ cũng vậy: nút *Mặc* bị khóa kèm lý do ngay trên thẻ và trang chi tiết, thay vì bấm rồi mới
nhận thông báo từ chối. Lý do do server tính (`ClothingItemResponse.blockReason`) chứ không để
giao diện tự suy từ `status` và `condition` — chép lại luật sang JavaScript là mở đường cho hai bên
lệch nhau.

### Kế hoạch mặc do AI sinh

Bấm **✨ AI lên kế hoạch** — nút này có ở **cả hai** trang *Lịch phối đồ* và *Gợi ý thời tiết*, mở
cùng một hộp thoại. Gõ mong muốn bằng lời (*"7 ngày đi làm, thứ Sáu gặp khách nên cần lịch sự
hơn"*), chọn số ngày và ngày bắt đầu. AI xếp lịch từ chính các bộ đang có trong tủ, mỗi ngày kèm
một câu giải thích.

Mở từ trang *Gợi ý thời tiết* thì dự báo của thành phố đang chọn được gửi kèm, nên AI cân nhắc cả
nhiệt độ và khả năng mưa từng ngày. Mở từ trang *Lịch* thì không có dự báo và AI được dặn bỏ qua
tiêu chí đó thay vì đoán bừa — hộp thoại nói rõ nó đang dùng dự báo cho mấy ngày.

Kế hoạch hiện ra ở dạng **xem trước, chưa lưu gì**. Ngày nào bạn đã tự đặt lịch sẵn sẽ được đánh
dấu và **giữ nguyên** trừ khi bạn tự tick ghi đè. Bấm lưu thì các ngày đổ vào lịch tháng như kế
hoạch bình thường, còn cả đợt hiện thành một thẻ ở Trang chủ kèm tiến độ và bộ của hôm nay.

Luật xếp lịch nằm ở `src/main/resources/prompts/wear-plan.md` — sửa file đó là đổi cách AI gợi ý,
không cần đụng vào code Java.

### Quản trị viên

Vai trò `ADMIN` là vai trò **vận hành**: quản lý tài khoản, không xem nội dung của người dùng.

| Làm được | Cố tình KHÔNG làm được |
|---|---|
| Xem số liệu tổng hợp toàn hệ thống (ẩn danh) | Xem tủ đồ, outfit, ảnh cơ thể của người dùng |
| Xem danh sách tài khoản: tên, email, ngày tạo, trạng thái khóa | Đăng nhập hộ người khác |
| Khóa / mở khóa tài khoản (bắt buộc nhập lý do) | Đổi email hoặc đặt lại mật khẩu thay người dùng |
| Đặt hạn mức AI / dịch vụ ngoài riêng cho từng tài khoản | Xóa dữ liệu người dùng |
| Đọc nhật ký kiểm toán | Sửa hoặc xóa nhật ký kiểm toán |
| | **Cấp quyền quản trị cho tài khoản khác** |

Ảnh cơ thể dùng cho thử đồ là dữ liệu nhạy cảm — cho quản trị viên xem mặc định là rủi ro lớn hơn
nhiều so với lợi ích. Cần xử lý báo cáo lạm dụng thì phải làm luồng riêng có sự đồng ý của chủ
sở hữu.

**Tạo admin đầu tiên** — không có API nào làm việc này, phải sửa thẳng database:

```sql
UPDATE app_users SET role = 'ADMIN' WHERE username = 'ten-dang-nhap';
```

Ma sát này là chủ đích: leo thang đặc quyền không thể thực hiện chỉ bằng một phiên đăng nhập bị
chiếm, mà đòi hỏi quyền truy cập máy chủ. Vì lý do đó, quản trị viên cũng không khóa được tài
khoản quản trị khác, và không tự khóa được chính mình.

Sau khi đổi `role` trong database, **tải lại trang** để giao diện nhận vai trò mới — vai trò được
lấy từ `/api/auth/me` chứ không phải từ access token đang giữ.

Quản trị viên thấy một **menu hoàn toàn khác**, chỉ gồm ba mục **Tổng quan / Tài khoản / Nhật ký**
(và Đăng xuất). Đây là tài khoản vận hành, không có tủ đồ để quản lý nên các mục Tủ đồ, Outfit,
Thử đồ, Chia sẻ đều không xuất hiện; `/` cũng tự chuyển sang `/admin`.

Tab **Nhật ký chạy trực tiếp**: sự kiện mới hiện ra ngay khi phát sinh, không cần tải lại trang.
Kênh đẩy dùng Server-Sent Events (`GET /api/admin/audit-events/stream`), tự nối lại khi rớt mạng,
và tải lại danh sách sau mỗi lần nối lại vì sự kiện xảy ra lúc mất kết nối không được gửi bù.

Khóa tài khoản có hiệu lực **tức thì**: access token bị từ chối ngay ở bộ lọc xác thực và toàn bộ
refresh token bị thu hồi, nên không có đường vòng nào để xin phiên mới.

#### Nhật ký kiểm toán

Bảng `audit_events` **chỉ ghi thêm** — không có API sửa hay xóa, kể cả cho quản trị viên; nhật ký
mà người bị giám sát chỉnh được thì không còn là bằng chứng. Nội dung được ghi: đăng nhập thành
công/thất bại (kể cả vào tài khoản không tồn tại — dấu vết của đợt dò tài khoản), khóa tự động,
đăng xuất, đăng ký, đổi/đặt lại mật khẩu, phát hiện refresh token bị dùng lại, và mọi hành động
của quản trị viên kèm lý do.

Mỗi bản ghi nằm trong transaction riêng (`REQUIRES_NEW`), nên sự kiện phát sinh ngay trước một
lỗi vẫn được lưu thay vì bị rollback cuốn theo.

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

#### Nơi cất token

| Token | Sống | Cất ở đâu | JavaScript đọc được? |
|---|---|---|:---:|
| Access token | 15 phút | Biến trong bộ nhớ tab | ✅ (buộc phải, để gắn header `Authorization`) |
| Refresh token | 7 ngày | Cookie `HttpOnly` `wearwise_refresh`, `Path=/api/auth` | ❌ |

Refresh token **không bao giờ xuất hiện trong JSON**. Một lỗ XSS bất kỳ — kể cả từ gói npm phụ
thuộc — chỉ lấy được access token 15 phút, không mang được phiên 7 ngày đi nơi khác.

Đổi lại phải tự chống CSRF, vì cookie thì trình duyệt gửi kèm tự động: server chỉ chấp nhận cookie
khi request có header `X-Wearwise-Client`. Form HTML không đặt được header tự chế, còn JavaScript
từ origin lạ thì vấp CORS preflight (danh sách origin do `wearwise.cors.allowed-origins` quy định).

Tải lại trang làm mất access token trong bộ nhớ; ứng dụng tự gọi `/api/auth/refresh` bằng cookie
để khôi phục phiên trước khi dựng giao diện.

#### Khóa ký JWT

`wearwise.auth.token-secret` **không có giá trị mặc định**. Thiếu nó thì ứng dụng báo lỗi và dừng
ngay lúc khởi động — chạy êm bằng một khóa nằm sẵn trong git nghĩa là ai đọc được repo cũng tự
phát hành được token cho mọi tài khoản.

```bash
openssl rand -base64 48        # sinh khóa, đặt vào WEARWISE_AUTH_TOKEN_SECRET
```

```powershell
# Windows PowerShell
$b = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Convert]::ToBase64String($b)
```

Chỉ profile `dev`/`test`/`local` mới được dùng khóa dùng chung trong `application-dev.properties`;
`AuthTokenService` từ chối khởi động nếu thấy đúng khóa đó ngoài ba profile này. Chốt đó so sánh
**theo chuỗi**, nên khóa trong file dev phải trùng khít hằng số `AuthTokenService.DEV_ONLY_SECRET` —
có test canh việc này, đừng đổi một chỗ rồi bỏ chỗ kia.

Lưu ý giới hạn: chốt trên **không** chặn được việc deploy production mà bật `spring.profiles.active=dev`.
Bản `java -jar` không tự bật profile nào, nên đừng thêm nó vào lệnh khởi chạy ở môi trường thật.

> Gặp lỗi *"Chưa cấu hình wearwise.auth.token-secret"* khi chạy cục bộ? Gần như chắc chắn là
> profile `dev` chưa bật — xem [phần chạy backend](#2-backend-cổng-8080). Khóa nằm trong
> `application-dev.properties` chỉ được đọc khi profile đó đang hoạt động.

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

### Nhập / xuất tủ đồ bằng CSV

Trang **Tủ đồ** → **📁 Nhập / xuất**. Ba việc trong một hộp thoại: tải file mẫu, xuất tủ đồ hiện có,
nhập từ file CSV.

- File mẫu có dòng tiêu đề, hai dòng ví dụ và ghi chú giá trị hợp lệ của từng cột ngay trong file —
  người dùng sửa file bằng Excel chứ không mở tài liệu.
- Bản **xuất ra đúng định dạng file mẫu**, nên nhập lại được. Đó là điều kiện để dùng nó làm bản sao
  lưu hoặc để sửa hàng loạt rồi nhập lại.
- Bắt buộc bốn cột `name`, `category`, `season`, `style`; các cột còn lại để trống là dùng mặc định.
  Cột được so khớp **theo tên tiêu đề**, không theo vị trí.
- **Dòng sai không làm đổ cả file**: dòng hợp lệ vào tủ, dòng sai được báo kèm số dòng để mở Excel
  sửa. Nộp lại file đã sửa thì phần đã vào được bỏ qua nhờ luật trùng tên (có ô tick để tắt).
- Tối đa 500 dòng mỗi lần, file tối đa 1MB. Ảnh không nhập bằng file này — ảnh tải lên ở trang Tủ đồ.

### Logic gợi ý theo thời tiết

Frontend lấy thời tiết hiện tại từ Open-Meteo (theo thành phố người dùng chọn), rồi gọi
`GET /api/outfits/suggestions?temperature=..&raining=..`. Backend chấm điểm từng outfit:

- Nhiệt độ < 20°C → ưu tiên outfit mùa đông; ≥ 26°C → mùa hè; outfit quanh năm luôn được cộng nhẹ
- Trời lạnh/mưa → cộng điểm outfit có áo khoác; trời mưa mà thiếu áo khoác → trừ điểm
- Cộng điểm outfit yêu thích, outfit lâu chưa mặc, outfit có đủ đồ ở trạng thái sẵn sàng

Chỉ những bộ **mặc được ngay hôm nay** được chấm điểm (xem *Bộ đồ "chưa mặc được"* ở trên), nên mọi
bộ trong danh sách đều đã sẵn sàng.

Trang hiển thị ba loại gợi ý trong **một vùng kết quả có tab**, không xếp dọc thành ba mục:

| Tab | Trả lời | Tốn lượt AI? |
|---|---|:---:|
| ✨ Bộ phù hợp | Chấm điểm theo luật ở trên, có sẵn khi mở trang | không |
| 🤖 AI chọn giúp | Gemini xếp thứ tự các bộ **đã có** kèm lý do từng bộ | có |
| 🧩 AI phối bộ mới | Gemini ghép các món lẻ thành bộ chưa từng lưu | có |

Hai tab AI chỉ chạy khi bấm nút, và kết quả được giữ trong `sessionStorage` để đi sang trang khác
rồi quay lại không mất — mỗi lần chạy là một lượt gọi trả tiền trong hạn mức 40 lượt/giờ.

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
./mvnw test        # backend (307 tests)
cd frontend && npm run check  # frontend: lint + smoke render + build
```

### CI

`.github/workflows/ci.yml` chạy mỗi push và pull request, ba job song song:

| Job | Làm gì | Vì sao |
|---|---|---|
| `backend` | `./mvnw test` trên **JDK 17** | Không phải 21, để một API chỉ có từ Java 21 bị chặn tại đây thay vì lọt tới lúc deploy |
| `frontend` | `npm ci` + `npm run check` | `npm ci` theo đúng lockfile; `npm install` sẽ âm thầm nâng cấp và CI không còn kiểm chứng cây phụ thuộc thật |
| `migration` | Dựng MySQL 8.4 trống, `package` rồi khởi động jar trên đó | Đây là thứ 307 test **không** kiểm được: test chạy H2 với Flyway tắt nên không file `.sql` nào được thi hành. Khởi động được = migration đúng **và** khớp entity (`ddl-auto=validate`) |

Không cần secret nào: `spring.config.import=optional:application-secrets.properties` cho phép thiếu
file khóa thật, và cấu hình test tự đặt `token-secret`. Nhờ vậy CI cũng xanh trên fork.

## Deploy

Frontend là SPA tĩnh nên hợp với Cloudflare Pages; backend cần JVM + MySQL nên phải đặt ở nơi khác
(Render, Railway, Fly.io, VPS...). Hai phần nối với nhau qua các cấu hình sau:

| Nơi đặt | Biến | Giá trị |
|---|---|---|
| Cloudflare Pages (build) | `VITE_API_BASE_URL` | `https://<domain-backend>` |
| Backend | `WEARWISE_AUTH_TOKEN_SECRET` | chuỗi ngẫu nhiên ≥ 32 ký tự — **thiếu thì app không khởi động** |
| Backend | `WEARWISE_CORS_ALLOWED_ORIGINS` | `https://<domain-pages>,https://*.<domain-pages>` |
| Backend | `WEARWISE_AUTH_REFRESH_COOKIE_SAME_SITE` | `None` |
| Backend | `WEARWISE_AUTH_REFRESH_COOKIE_SECURE` | `true` |
| Backend | `MAIL_RESET_URL_BASE` | `https://<domain-pages>/reset-password` |

Hai biến cookie là **bắt buộc khi frontend và backend nằm ở hai domain khác nhau**: trình duyệt
không gửi cookie `SameSite=Lax` sang site khác, nên để nguyên mặc định thì người dùng bị đăng
xuất mỗi lần tải lại trang. `SameSite=None` bắt buộc đi kèm `Secure`, và ứng dụng từ chối khởi
động nếu đặt sai cặp này.

Sau khi chốt domain backend, nên siết `connect-src` trong `frontend/public/_headers` về đúng
domain đó (file có sẵn hướng dẫn).

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

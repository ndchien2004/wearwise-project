# CLAUDE.md

Ngữ cảnh dự án cho AI agent. Đọc file này trước khi sửa code.

> **Ngôn ngữ:** Toàn bộ comment, Javadoc, thông báo lỗi và tài liệu trong dự án viết bằng **tiếng
> Việt**. Giữ nguyên quy ước đó. Tên class/method/biến thì viết bằng tiếng Anh.

---

## 1. Dự án là gì

**WearWise** — ứng dụng quản lý tủ đồ cá nhân. Người dùng lưu quần áo của mình, phối thành outfit,
lên lịch mặc theo ngày, nhận gợi ý theo thời tiết, thử đồ ảo bằng AI và xem thống kê.

| | |
|---|---|
| Backend | Spring Boot 4.0.6, Java 17 (JDK cài sẵn là 21 — **đừng dùng API Java 21** như `Math.clamp`) |
| Database | MySQL 8.4 qua Docker; schema quản lý bằng Flyway |
| Frontend | React 18 + Vite 5, JavaScript thuần (không TypeScript), CSS tự viết theo phong cách neobrutalism |
| Test | JUnit 5 + Mockito + AssertJ, chạy trên H2. 275 test, tất cả phải xanh |
| Dịch vụ ngoài | Cloudinary (ảnh), Google Gemini (nhận diện + gợi ý), tryon-api.com (thử đồ ảo), Open-Meteo (thời tiết, không cần key) |

Quy mô: ~148 file Java, ~47 file JS/JSX. Đây là đồ án nhóm nhưng được xây theo chuẩn sản phẩm thật.

---

## 2. Chạy dự án

```bash
docker compose up -d mysql          # 1. Database
./mvnw spring-boot:run              # 2. Backend, cổng 8080 (profile dev tự bật)
cd frontend && npm install && npm run dev   # 3. Frontend, cổng 5173
```

**Cạm bẫy số 1 — profile `dev`.** `wearwise.auth.token-secret` không có giá trị mặc định; thiếu nó
thì ứng dụng **từ chối khởi động**. Khóa dùng cho máy lập trình viên nằm trong
`application-dev.properties`, chỉ được nạp khi profile `dev` đang bật:

- `./mvnw spring-boot:run` — tự bật (khai báo ở `spring-boot-maven-plugin` trong `pom.xml`)
- IntelliJ — dùng cấu hình `WearwiseApplication [dev]` trong thư mục `.run/`
- `java -jar` — phải tự thêm `--spring.profiles.active=dev`, hoặc đặt `WEARWISE_AUTH_TOKEN_SECRET`

Thấy lỗi *"Chưa cấu hình wearwise.auth.token-secret"* thì gần như chắc chắn là quên bật profile,
chứ không phải khóa sai.

**Kiểm tra trước khi báo xong việc:**

```bash
./mvnw test                      # backend — phải 275/275 xanh
cd frontend && npm run lint      # frontend — phải 0 lỗi
cd frontend && npm run build     # frontend — phải build được
```

**`npm run lint` là bắt buộc, không phải tùy chọn.** `vite build` chỉ dịch mã, nó **không** kiểm
tra biến có tồn tại hay không: một lần đổi tên còn sót chỗ dùng vẫn build xanh và chỉ vỡ lúc chạy,
đúng nhánh giao diện hiếm khi hiển thị, thành màn hình trắng. Đã xảy ra đúng một lần như vậy
(`wearableItems` ở trang Thử đồ). Cấu hình cố tình chỉ giữ luật bắt lỗi thật, không có luật định
dạng — thêm nhiễu là người ta quen tay bỏ qua cảnh báo, rồi bỏ qua luôn cảnh báo thật.

---

## 3. Bản đồ mã nguồn

```
src/main/java/org/group7/wearwise/
├── config/          Filter, SecurityConfig, cookie, rate limit, seed dữ liệu demo
├── controller/      REST controller — mỏng, chỉ điều phối
├── dto/request|response/   Java record, không dùng entity làm DTO
├── entity/          JPA entity
├── enums/           Danh mục, mùa, phong cách, loại sự kiện kiểm toán...
├── exception/       AppException + ErrorCode + GlobalExceptionHandler
├── repository/      Spring Data JPA (+ specification/ cho truy vấn động)
├── service/         Toàn bộ nghiệp vụ nằm ở đây
└── validation/      Annotation validation tự viết (@StrongPassword)

src/main/resources/
├── application.properties          Cấu hình chung, an toàn để commit
├── application-dev.properties       CHỈ dành cho máy lập trình viên
├── application-secrets.properties   API key thật — ĐÃ GITIGNORE, không commit
└── db/migration/                    Flyway: V1__init.sql … V5__wear_plans.sql

frontend/src/
├── api/         Một file cho mỗi nhóm endpoint; client.js là lớp fetch dùng chung
├── components/  Component tái sử dụng (ui.jsx chứa Button, Modal, Loading...)
├── context/     AuthContext (phiên đăng nhập), ConfirmContext (hộp thoại xác nhận)
└── pages/       Một file cho mỗi route
```

Kiến trúc phân tầng thẳng: **controller → service → repository**. Nghiệp vụ nằm ở service; controller
chỉ nhận request, gọi service, trả DTO. Đừng đưa logic vào controller hay repository.

---

## 4. Những quyết định đã chốt — đừng đảo ngược nếu không có lý do rõ ràng

Đây là phần quan trọng nhất của tài liệu này. Mỗi mục dưới đây từng là một lỗ hổng hoặc một cái bẫy
đã được vá; sửa lại theo hướng "đơn giản hơn" sẽ mở lại đúng lỗ hổng đó.

### 4.1 Nơi cất token

| Token | Sống | Cất ở đâu | JS đọc được? |
|---|---|---|:---:|
| Access token | 15 phút | Biến trong bộ nhớ tab (`frontend/src/api/client.js`) | ✅ bắt buộc, để gắn header |
| Refresh token | 7 ngày | Cookie `HttpOnly` `wearwise_refresh`, `Path=/api/auth` | ❌ |

- `AuthResponse.refreshToken` có `@JsonIgnore` — **không bao giờ** lọt vào JSON. Bỏ annotation đó
  là xóa sạch lợi ích của cookie HttpOnly.
- Đổi lại phải tự chống CSRF: server chỉ chấp nhận cookie khi request có header
  `X-Wearwise-Client`. Kiểm tra nằm ngay trong `RefreshTokenCookie.read()` — tức là **không có
  đường nào lấy được token mà lách qua được**, kể cả khi thêm endpoint mới sau này.
- Tải lại trang làm mất access token; `AuthContext` tự gọi `/api/auth/refresh` trước khi dựng
  giao diện (có màn hình chờ ngắn) để tránh nháy sang trang đăng nhập.
- Deploy khác domain (Cloudflare Pages + API riêng) **bắt buộc** đặt
  `WEARWISE_AUTH_REFRESH_COOKIE_SAME_SITE=None` và `..._SECURE=true`, nếu không trình duyệt không
  gửi cookie và người dùng bị đăng xuất mỗi lần F5.

### 4.2 Schema database — Flyway, không phải Hibernate

`spring.jpa.hibernate.ddl-auto=validate`. Hibernate **không còn quyền sửa cấu trúc bảng**.

- Đổi entity thì **phải** viết migration `V<n>__mo_ta.sql` tương ứng, nếu không ứng dụng không
  khởi động được (validate sẽ báo lệch).
- **Không bao giờ sửa file migration đã chạy** — Flyway lưu checksum và sẽ từ chối khởi động.
  Cần điều chỉnh thì thêm file mới.
- Database cũ đã có dữ liệu được xử lý bằng `baseline-on-migrate=true`.
- Test chạy trên H2 nên `spring.flyway.enabled=false` ở `src/test/resources/application.properties`;
  schema cho test do Hibernate dựng từ entity. Điều đó có nghĩa **test không phát hiện được
  migration sai** — phải tự kiểm chứng bằng cách chạy app với MySQL trống (xem mục 7).

### 4.3 Khóa ký JWT

Không có giá trị mặc định trong `application.properties`. `AuthTokenService` từ chối khởi động khi:
thiếu khóa, khóa ngắn hơn 32 ký tự, hoặc dùng khóa dev (`DEV_ONLY_SECRET`) ngoài profile
`dev`/`test`/`local`. Hỏng lúc khởi động rõ ràng hơn nhiều so với một hệ thống chạy êm mà không có
xác thực thật.

### 4.4 Giới hạn tần suất

Năm nhóm trong `RateLimitFilter`, xếp theo mức "đắt":

| Nhóm | Đường dẫn | Mặc định |
|---|---|---|
| `AI` | `/api/ai/**` (Gemini), trừ `/api/ai/status` | 40/giờ |
| `TRY_ON` | `POST /api/try-on/items/*`, `POST /api/try-on/outfits/*` | 15/giờ |
| `UPLOAD` | `POST` tới `/api/images/*`, `/api/try-on/body-photo`, `/api/auth/avatar` | 80/giờ |
| `AUTH` | `/api/auth/**` trừ `/me`, `/avatar`, `/logout` | 20/phút |
| `GENERAL` | còn lại | 240/phút |

- `TRY_ON` và `UPLOAD` **chỉ tính trên `POST`** — GET chỉ đọc kết quả đã lưu, không tốn tiền.
  `/api/ai/status` được loại trừ riêng vì giao diện gọi nó mỗi lần mở form.
- Thử đồ tách khỏi tải ảnh vì chênh lệch chi phí quá lớn: gộp chung một túi thì thêm vài món quần
  áo là hết lượt ghép ảnh một cách vô lý.
- Thêm endpoint gọi dịch vụ trả tiền thì **phải** xếp nó vào `TRY_ON` hoặc `UPLOAD` trong
  `groupOf()`, nếu không nó rơi vào GENERAL = 240 lượt/phút và đốt sạch quota.
- Quản trị viên đặt được hạn mức riêng cho từng tài khoản; giá trị nằm trong `app_users` và được
  đệm ở `UserRateLimitOverrides` (không truy vấn DB trong filter — filter chạy trên mọi request).
  Núm "dịch vụ ngoài" nới **cả** `TRY_ON` lẫn `UPLOAD` — tách thành hai núm cần thêm cột và một
  migration, chưa đáng khi chưa có nhu cầu thật.
- `RateLimiter` là token bucket **trong bộ nhớ mỗi tiến trình**. Chạy nhiều instance thì hạn mức
  nhân lên theo số instance. Đây không phải lớp chống DDoS.

### 4.5 Xử lý ảnh tải lên

Mọi ảnh đi qua `ImageValidator` — đừng viết lại kiểm tra riêng ở service mới.

- Đọc **kích thước từ header trước**, rồi mới giải mã. Đảo thứ tự là mở lại lỗ decompression bomb:
  tệp PNG 69 byte khai báo 40000×40000 sẽ ngốn ~6GB heap ở `ImageIO.read()`. Có test chứng minh.
- Trần: 10MB, 10.000px mỗi cạnh, 30 triệu điểm ảnh.
- Định dạng xác định theo **nội dung tệp**, không tin `Content-Type` do client khai.
- `CloudinaryService` upload kèm transformation `a_exif,fl_force_strip` — xoay theo EXIF **trước**,
  rồi xóa metadata. Đảo thứ tự thì ảnh chụp dọc bằng điện thoại nằm ngang vĩnh viễn; bỏ hẳn thì tọa
  độ GPS nơi chụp bị công bố qua URL Cloudinary công khai.

### 4.6 Vai trò quản trị viên

Xem bảng "làm được / không làm được" ở `README.md`. Nguyên tắc: **quản lý tài khoản, không chạm vào
nội dung**. Cụ thể, đừng thêm những thứ sau nếu không có yêu cầu rõ ràng kèm cân nhắc rủi ro:

- API cho admin xem tủ đồ / ảnh cơ thể của người dùng
- API đăng nhập hộ (impersonate)
- API cấp quyền admin (hiện phải `UPDATE app_users SET role='ADMIN'` thẳng trong DB — ma sát là
  chủ đích, để leo thang đặc quyền không thực hiện được chỉ bằng một phiên bị chiếm)
- Bất kỳ API nào sửa hoặc xóa `audit_events`

Phân quyền khai báo tập trung tại `SecurityConfig` (`.requestMatchers("/api/admin/**").hasRole("ADMIN")`),
không rải `@PreAuthorize` — endpoint admin mới được bảo vệ sẵn mà không phụ thuộc trí nhớ người viết.

Giao diện ở `frontend/src/pages/AdminPage.jsx`, ba route `/admin`, `/admin/users`, `/admin/audit`.
Việc `RequireAdmin` và menu ẩn đi **chỉ là lớp giao diện** — quyền thật do server quyết định, gõ
thẳng URL vẫn nhận 403. Đừng bao giờ coi kiểm tra ở frontend là biện pháp bảo mật.

Quản trị viên dùng **menu riêng** (`ADMIN_MENU_ITEMS` trong `Layout.jsx`), không phải menu thường
cộng thêm một mục: đây là tài khoản vận hành, không có tủ đồ để quản lý. `/` cũng chuyển hướng
sang `/admin` với vai trò này.

### 4.9 Nhật ký thời gian thực (SSE)

`GET /api/admin/audit-events/stream` đẩy sự kiện ngay khi phát sinh, qua `AuditEventBroadcaster`.

- `AuditLogService` phát **sau khi** `AuditEventWriter.write()` trả về, tức là sau khi transaction
  `REQUIRES_NEW` đã commit — không đẩy đi sự kiện mà database rốt cuộc không lưu.
- Frontend đọc stream bằng `fetch` + `ReadableStream`, **không dùng `EventSource`**: `EventSource`
  không đặt được header, mà access token nằm trong bộ nhớ chứ không phải cookie; cách duy nhất để
  dùng nó là nhét token vào query string, và token sẽ hiện trong log của mọi proxy trên đường đi.
- Đây là kênh **thông báo**, không phải nguồn dữ liệu: mất kết nối là mất sự kiện trong lúc đó, nên
  giao diện tải lại danh sách qua REST mỗi khi nối lại.
- Danh sách kết nối nằm trong bộ nhớ từng tiến trình — chạy nhiều instance thì admin nối vào
  instance A không thấy sự kiện sinh ở instance B. Cùng bài toán với `RateLimiter` và
  `UserRateLimitOverrides`; muốn đúng khi scale ngang thì cả ba phải chuyển sang Redis.
- Nhịp tim 25 giây dùng `ScheduledExecutorService` riêng, không bật `@EnableScheduling` toàn cục.

### 4.10 Thử đồ ảo

- Nhà cung cấp hiện dùng là **tryon-api.com** (`TryOnApiClient`). `GeminiImageClient` là lựa chọn
  thay thế, đã viết nhưng **chưa chạy thử được lần nào** — xem lý do ngay bên dưới.
- **Model sinh ảnh của Gemini đòi API key đã bật thanh toán.** Kiểm chứng 27/07/2026: cả bốn model
  ảnh đều trả `429 limit: 0` với key gói miễn phí, trong khi model text vẫn 200. Đừng mất thời gian
  debug code — đó là hạn mức, không phải lỗi lập trình.
- Model sinh ảnh **bắt buộc có hậu tố `-image`** (`gemini-3.1-flash-lite-image`). Model text như
  `gemini-3.1-flash-lite` không sinh được ảnh. Endpoint cũng khác: `/v1beta/interactions` chứ không
  phải `:generateContent`.
- **Một lời gọi = đúng một ảnh trang phục.** Trường `garment_images` của nhà cung cấp là mảng nên
  trông như gửi được cả bộ, nhưng model không ghép nổi nhiều món trong một lượt và trả lỗi. Vì vậy
  `TryOnApiClient.generateTryOn` chỉ nhận một `String`; đừng mở lại bản nhận `List`.
- **Thử cả bộ dùng ảnh riêng của outfit** (`outfits.image_url`), không phải ảnh của từng món gộp
  lại. Bộ chưa có ảnh riêng thì `generateForOutfit` từ chối ngay và `OutfitPicker` lọc bỏ khỏi
  danh sách — mời người dùng bấm một nút chắc chắn lỗi còn tệ hơn là không cho bấm. Ghép nối tiếp
  từng món không phải lời giải thay thế: mỗi lớp là một lần model vẽ lại toàn bộ ảnh nên mặt và
  dáng người trôi dần, lại tốn N lượt trong hạn mức 15 lượt/giờ.
- **Mặc chồng lớp**: `baseResultId` trỏ tới một kết quả trước đó, khi đó ảnh nền là ảnh kết quả đó
  thay vì ảnh cơ thể. Bắt buộc tra qua `findByIdAndOwner_Username` — tra theo mỗi id thì người dùng
  A truyền id của B là ghép được đồ lên ảnh cơ thể người khác.
- `base_image_url` được **lưu lại** chứ không suy ra từ hồ sơ: người dùng đổi ảnh cơ thể lúc nào
  cũng được, và khi đó mọi kết quả cũ sẽ bị đem so với một ảnh gốc không liên quan.
- Hướng dẫn chuẩn bị ảnh và viết prompt: `docs/TRY_ON_IMAGE_GUIDE.md`.

### 4.12 Khả dụng của outfit — hai câu hỏi khác nhau

Đừng gộp chúng làm một. Gộp rồi thì hoặc là không lên lịch được vì hôm nay có cái áo đang giặt,
hoặc là bấm "Mặc" xong mới nhận thông báo từ chối.

| Câu hỏi | Hàm | Tính những gì | Chặn cái gì |
|---|---|---|---|
| Bộ còn lành lặn không? | `OutfitService.isAvailable` | món bị **ẩn** | mặc, lên lịch, gợi ý |
| Hôm nay mặc được không? | `OutfitService.isWearableNow` | thêm **đang giặt / hư hỏng / chưa dùng được** | mặc, gợi ý hôm nay |

- Đồ đang giặt là **tạm thời** nên vẫn lên lịch cho ngày sau được — tới lúc đó rất có thể đã giặt
  xong. Vì vậy nó không đụng tới `available`, và `DayModal` ở trang Lịch vẫn lọc theo `available`.
- `ClothingItemService.blockReason()` là **nguồn sự thật duy nhất** cho câu "món này vướng gì".
  `assertWearable()` ném lỗi dựa trên chính nó. Tách làm hai chỗ thì sớm muộn cũng lệch: bộ nằm ở
  mục dùng được nhưng bấm vào lại báo lỗi.
- `OutfitResponse` trả kèm `wearableNow` + `blockingItems` (tên món + `ItemBlockReason`), nhờ đó
  giao diện xếp bộ vào mục "Chưa mặc được" kèm lý do **trước khi** người dùng bấm. Frontend rẽ
  nhánh theo hằng số, lời văn nằm ở `BLOCK_REASON_LABELS` trong `utils/labels.js`.
- Gợi ý thời tiết, xếp hạng AI và `planWeek` đều lọc theo `isWearableNow`.

### 4.13 Kế hoạch mặc do AI sinh

Người dùng gõ một câu ("7 ngày đi làm, thứ Sáu gặp khách"), AI xếp lịch từ **các bộ đã có**.

- **Sinh và lưu là hai bước tách bạch.** `POST /api/ai/wear-plans` chỉ trả về bản xem trước, không
  ghi gì vào database. Đổ thẳng vào lịch thì mỗi lần kế hoạch không ưng ý người dùng lại phải đi
  dọn từng ngày — và tệ hơn, những ngày họ tự đặt tay đã bị ghi đè mất.
- **Đường dẫn sinh nằm dưới `/api/ai/**` là bắt buộc**, để `RateLimitFilter` xếp vào nhóm AI. Dời
  sang `/api/wear-plans` là đẩy nó vào GENERAL = 240 lượt/phút.
- **Ngày đã có kế hoạch chỉ bị thay khi người dùng tick ghi đè** (`replaceExisting` từng ngày).
  Không tick thì bỏ qua ngày đó; mất một ngày trong đợt AI còn hơn mất kế hoạch họ tự đặt.
- `wear_plans` là **cái vỏ**: từng ngày vẫn là `outfit_plans` bình thường (có `wear_plan_id` trỏ
  về), nên lịch tháng, đánh dấu đã mặc và nhật ký mặc chạy y như cũ mà không cần biết ngày đó đến
  từ đâu. Lý do AI chọn bộ nằm ở `outfit_plans.note` — vốn là ghi chú của ngày, để đó thì lịch
  hiện sẵn.
- **Luật xếp lịch nằm ở `src/main/resources/prompts/wear-plan.md`, không nằm trong code.** Đó là
  phần phải chỉnh đi chỉnh lại nhiều nhất, và nó là văn bản tiếng Việt thuần. File nằm trong
  classpath (không phải `docs/`) để bản `java -jar` đọc được; phần trước dấu `---` là ghi chú cho
  lập trình viên và bị cắt bỏ trước khi gửi cho model. Nạp **một lần lúc dựng bean**: file hỏng thì
  hỏng ngay lúc khởi động, rõ hơn nhiều so với lỗi chỉ hiện khi có người bấm nút.
- Số ngày do **JSON schema** ép (`minItems`/`maxItems`), không phải dặn bằng lời — một câu "hãy trả
  đủ 7 ngày" thì model bỏ qua lúc nào không hay. Trần `WearPlan.MAX_DAYS` = 14.
- Model trả về id bịa hoặc ngày ngoài khoảng thì **bỏ dòng đó**, không ném lỗi cả lượt: mất một
  ngày còn hơn mất cả kế hoạch lẫn một lượt gọi trả tiền.
- `AiPlanModal` là **cửa duy nhất** vào tính năng này; trang Lịch và trang Gợi ý thời tiết cùng mở
  nó, khác nhau ở chỗ trang Gợi ý truyền thêm `forecast`. Trước đây trang Gợi ý có luồng riêng
  ("Để AI lên kế hoạch" → `/api/ai/weekly-plan`) chạy ngay không hỏi gì rồi thêm từng ngày vào lịch.
  Hai nút tên gần giống nhau ở hai trang khiến người dùng bấm nhầm và tưởng tính năng mới bị hỏng —
  đừng dựng lại lối vào thứ hai. **`/api/ai/weekly-plan` và `AiSuggestionService.planWeek` hiện
  không còn client nào gọi**, giữ lại chỉ vì chưa ai quyết định xóa.

### 4.11 Phân trang

**Mọi danh sách đều phân trang ở database** (`Pageable` → SQL `limit ?, ?` kèm `count(*)`), trả về
`Page<>` với `content` / `totalPages` / `totalElements`:

| Endpoint | Kích thước trang |
|---|---|
| `/api/admin/users` | 20 |
| `/api/admin/audit-events` | 25 |
| `/api/clothing-items` | 24 mặc định; Tủ đồ dùng 12, Thử đồ dùng 9 (kèm `hasImage=true`) |

Quy tắc bắt buộc khi thêm danh sách mới:

1. **Luôn có `Sort` xác định.** Thiếu `order by`, database không cam kết thứ tự giữa hai lần gọi —
   người dùng sẽ thấy một món lặp ở trang 2 còn món khác biến mất hẳn. `/api/clothing-items` sắp
   theo `id ASC` (giữ đúng thứ tự giao diện vẫn hiển thị từ trước).
2. **Chặn trên `size`** (hiện là 100) để một request không kéo cả bảng về.
3. **Đừng lọc ở phía client sau khi server đã cắt trang** — mỗi trang sẽ thiếu phần tử một cách
   ngẫu nhiên. Đó là lý do có tham số `hasImage`: trang Thử đồ trước đây lọc `item.imageUrl` bằng
   JavaScript.

`?unpaged=true` lấy trọn kết quả trong một lần gọi. **Chỉ** dùng cho ô chọn món khi phối outfit,
nơi người dùng phải chọn được bất kỳ món nào trong tủ (`findAllItems()` ở
`api/clothingItems.js`). Đừng dùng cho danh sách hiển thị.

Trang và bộ lọc nằm trên **query string** (`?page=2&keyword=...`), đọc qua hook dùng chung
`usePagedParams()` (`frontend/src/utils/usePagedParams.js`), hiện dùng ở Tủ đồ, Thử đồ và cả ba
tab quản trị. Nhờ vậy tải lại trang không mất chỗ, Back/Forward chạy đúng, và dán link cho người
khác thì họ thấy đúng cái mình thấy. Số trang trên URL đếm từ 1 cho khớp với thứ người dùng nhìn
thấy, còn API dùng chỉ số từ 0 theo quy ước Spring Data — quy đổi gói gọn trong hook đó.

### 4.7 Nhật ký kiểm toán

- `AuditLogService` → `AuditEventWriter` là **hai bean tách rời** có chủ đích: `@Transactional`
  hoạt động bằng proxy, gọi phương thức trong cùng class sẽ không đi qua proxy và `REQUIRES_NEW`
  âm thầm mất tác dụng. Gộp lại thành một class là làm hỏng thứ quan trọng nhất của nó.
- `REQUIRES_NEW` là bắt buộc: phần lớn sự kiện đáng ghi nhất xảy ra ngay trước một exception
  (đăng nhập sai, phát hiện token bị dùng lại), nếu chung transaction sẽ bị rollback cuốn theo.
- Ghi log không bao giờ được ném exception ra ngoài — log hỏng không được làm hỏng việc người dùng.
- **Tuyệt đối không** đưa mật khẩu, token hay dữ liệu cá nhân vào trường `detail`.
- Chỉ ghi sự kiện **ảnh hưởng tới quyền truy cập**. Đừng ghi hoạt động thường ngày (thêm áo, sửa
  outfit) — sẽ làm loãng nhật ký tới mức không ai đọc và biến bảng này thành điểm nghẽn ghi.

### 4.8 Email là cố định

Chốt một lần khi đăng ký, **không có API nào đổi được**. Đây là địa chỉ duy nhất nhận link đặt lại
mật khẩu, nên khóa lại để kẻ chiếm được phiên không trỏ được email khôi phục sang hộp thư của mình.

---

## 5. Quy ước khi viết code

**Xử lý lỗi.** Mọi lỗi nghiệp vụ kế thừa `AppException`, mang một `ErrorCode` (enum này giữ luôn
HTTP status). `GlobalExceptionHandler` chỉ có một nhánh xử lý. Thêm lỗi mới thì thêm `ErrorCode` +
một class con, đừng ném `RuntimeException` trần. Client rẽ nhánh theo `code`, không theo `message` —
nên đổi lời văn thoải mái, nhưng đổi mã là phá client.

**Quyền sở hữu dữ liệu.** Mọi truy vấn tủ đồ đều lọc theo `owner.username` lấy từ
`Authentication.getName()`. Không có endpoint nào nhận `userId` từ client. Giữ nguyên nếp này.

**Xóa mềm.** `ClothingItem.archivedAt` khác NULL nghĩa là đã ẩn. Truy vấn danh sách/thống kê phải
lọc `archivedAt is null`; riêng `findByIdAndOwner_Username` cố tình vẫn trả về món đã ẩn để mở được
trang chi tiết và bấm khôi phục.

**DTO.** Dùng Java record. Không trả entity thẳng ra API.

**Comment.** Giải thích **vì sao**, không mô tả **cái gì**. Code đã nói cái gì rồi. Các comment
hiện có trong dự án đều theo hướng này — hãy giữ đúng giọng đó.

**Frontend.** `apiFetch`/`apiUpload` trong `client.js` là đường duy nhất gọi API — chúng lo sẵn
header xác thực, header chống CSRF, `credentials: 'include'` và tự gia hạn token khi gặp 401. Đừng
gọi `fetch` trực tiếp ở component.

---

## 6. Trạng thái hiện tại

**Đã xong** (ngoài các tính năng nghiệp vụ trong README):

- Vá bảo mật: rate limit cho endpoint trả tiền, Flyway thay `ddl-auto=update`, fail-fast khóa JWT,
  refresh token sang cookie HttpOnly, chặn decompression bomb, strip EXIF, security headers
  (backend + `frontend/public/_headers`).
- Vai trò quản trị viên + nhật ký kiểm toán (đẩy real-time qua SSE) + hạn mức riêng theo tài khoản.
- Phân trang phía server cho toàn bộ danh sách, số trang và bộ lọc nằm trên URL (mục 4.10).

**Chưa làm — xếp theo giá trị/công sức.** Đây là danh sách gợi ý, không phải cam kết:

| Việc | Vì sao đáng làm |
|---|---|
| CI (GitHub Actions chạy `mvnw test` + `npm run build`) | Có 275 test mà không ai chạy tự động thì phí |
| Actuator + health check + Micrometer | Chưa có cách nào biết hệ thống đang sống hay đang chết; cũng là nền để đếm lượt gọi Gemini (hiện `AdminOverviewResponse` cố tình bỏ trống con số này thay vì bịa) |
| Request-id trong log (MDC) | User báo lỗi thì hiện không tra ngược được request nào |
| Index composite `(owner_id, archived_at, wear_count)` | Mọi truy vấn đều lọc theo bộ này |
| Cache thống kê | Dashboard chạy 11 truy vấn mỗi lần tải |
| Try-on chạy bất đồng bộ | Đang chặn thread request tới 60s; 200 người thử đồ là nghẽn toàn bộ API |
| Dọn token hết hạn bằng `@Scheduled` | Đang chạy inline trong luồng login, người dùng gánh chi phí DELETE |
| Cost-per-wear (giá mua + `wearCount`) | Đúng bài toán của app tủ đồ, hạ tầng đã có sẵn |

---

## 7. Cạm bẫy đã gặp

- **Java 17, không phải 21.** JDK cài sẵn là 21 nên `Math.clamp`, `SequencedCollection`... vẫn gợi ý
  được trong IDE nhưng sẽ hỏng lúc biên dịch.
- **Spring Boot 4 tách auto-configuration ra module riêng.** Khai báo `flyway-core` là chưa đủ —
  thư viện có mặt nhưng không ai kích hoạt nó, Flyway im lặng không chạy. Phải dùng
  `spring-boot-starter-flyway`. Quy tắc này áp dụng cho mọi tích hợp khác trong Boot 4.
- **Class có hai constructor thì Spring không biết chọn cái nào** — phải đánh dấu `@Autowired`
  (xem `AuthTokenService`).
- **Tự gọi phương thức `@Transactional` trong cùng class thì annotation vô tác dụng** (mục 4.7).
- **Migration chỉ được kiểm chứng thủ công.** Test chạy H2 nên không đụng tới file `.sql`. Sau khi
  viết migration, hãy chạy thật:

  ```bash
  # DB trống, để migration thực sự thi hành
  docker run -d --name mig-check -e MYSQL_DATABASE=wearwise_db -e MYSQL_USER=wearwise_user \
    -e MYSQL_PASSWORD=wearwise_pass -e MYSQL_ROOT_PASSWORD=rootpw -p 3399:3306 mysql:8.4
  ./mvnw -DskipTests package
  WEARWISE_AUTH_TOKEN_SECRET="chuoi-ngau-nhien-toi-thieu-32-ky-tu-abcdef" SERVER_PORT=8099 \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3399/wearwise_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" \
    java -jar target/wearwise-0.0.1-SNAPSHOT.jar
  # App khởi động được = migration đúng VÀ khớp với entity (nhờ ddl-auto=validate)
  docker rm -f mig-check
  ```

  So `information_schema.columns` giữa DB cũ và DB mới là cách nhanh nhất để chắc chắn migration tái
  hiện đúng schema.
- **`vite build` xanh không có nghĩa là code chạy được.** Xem mục 2 — luôn chạy `npm run lint`.
- **Toàn bộ cây React nằm trong `ErrorBoundary`** (`components/ErrorBoundary.jsx`). Lỗi render giờ
  hiện màn hình có nội dung kèm thông điệp lỗi thay vì nền trắng. Đây là lưới an toàn, không phải
  chỗ xử lý lỗi: lỗi nghiệp vụ vẫn phải bắt tại chỗ và hiển thị bằng `ErrorBanner`.
- **Cổng 8080 hay bị chiếm** bởi một instance đang chạy dở. Dùng `SERVER_PORT=8099` khi kiểm thử.
- **Test dùng chung database H2**; `AdminSecurityIntegrationTest` xóa sạch bảng ở `@BeforeEach`.
  Đăng nhập trong test cũng sinh ra sự kiện kiểm toán — cẩn thận khi viết assert đếm số bản ghi.

---

## 8. Trước khi báo cáo hoàn thành

1. `./mvnw test` — 275/275 xanh (con số này tăng khi thêm test; cập nhật lại README và file này).
2. `cd frontend && npm run build` — build được.
3. Sửa entity → đã có migration tương ứng chưa? Đã chạy thử trên DB trống chưa?
4. Thêm endpoint gọi dịch vụ trả tiền → đã xếp vào `TRY_ON`/`UPLOAD` trong `groupOf()` chưa?
5. Thêm endpoint `/api/admin/**` → có vô tình mở đường xem nội dung người dùng không?
6. Đổi hành vi bảo mật → đã cập nhật `README.md` và mục 4 của file này chưa?

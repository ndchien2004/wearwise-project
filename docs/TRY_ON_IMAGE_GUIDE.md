# Hướng dẫn làm ảnh thử đồ chuẩn

Tài liệu này gom hai thứ: **cách chuẩn bị ảnh đầu vào** để kết quả ghép đẹp, và **cách viết prompt**
nếu dùng model sinh ảnh (Gemini) thay cho tryon-api.com.

> Kết luận ngắn cho ai đang vội: chất lượng ảnh thử đồ phụ thuộc vào **ảnh đầu vào** nhiều hơn là
> vào model. Một tấm ảnh người chụp nghiêng, nền lộn xộn thì không model nào cứu được.

---

## 1. Ảnh người (ảnh cơ thể)

Đây là ảnh chỉ tải lên một lần và dùng cho mọi lần thử, nên đáng để chụp lại cho tử tế.

| Yếu tố | Nên | Tránh |
|---|---|---|
| Góc chụp | Chính diện, máy ngang tầm ngực | Chụp từ trên xuống, dưới lên, nghiêng người |
| Khung hình | Toàn thân hoặc từ đầu gối trở lên | Cắt mất vai, chỉ có nửa mặt |
| Tư thế | Đứng thẳng, hai tay buông tự nhiên, hơi tách khỏi thân | Khoanh tay, chống nạnh, tay che thân |
| Trang phục đang mặc | Đồ **bó sát, đơn giản** (áo thun, quần legging) | Áo khoác phồng, váy xòe, đồ nhiều lớp |
| Nền | Tường trơn một màu, sáng đều | Nền nhiều đồ đạc, người khác trong khung |
| Ánh sáng | Sáng đều từ phía trước | Ngược sáng, bóng đổ mạnh lên người |
| Số người | Đúng một | Từ hai người trở lên |

**Vì sao trang phục đang mặc lại quan trọng:** model phải "cởi" đồ cũ ra rồi mới mặc đồ mới vào.
Đồ cũ càng phồng, càng nhiều lớp thì phần thân người bị che càng nhiều, và model buộc phải đoán —
đó là lúc kết quả bị méo tay, méo eo.

Ràng buộc kỹ thuật hệ thống đang áp (xem `ImageValidator` và `TryOnService`):

- JPG hoặc PNG, tối đa **10MB**
- Mỗi cạnh tối thiểu **300px**, tối đa **10.000px**, tổng không quá **30 triệu điểm ảnh**
- Tỉ lệ khung trong khoảng **1:3 đến 3:1**

## 2. Ảnh trang phục

| Yếu tố | Nên | Tránh |
|---|---|---|
| Kiểu ảnh | Ảnh phẳng (flat-lay) hoặc trên ma-nơ-canh, nền trắng | Ảnh người mẫu đang mặc, chụp trong cửa hàng |
| Góc | Chính diện, thấy trọn món đồ | Chụp chéo, gấp đôi, treo trên móc bị che |
| Nền | Trắng hoặc trong suốt | Nền có hoa văn, có món đồ khác lọt vào |
| Nội dung | **Đúng một** món trong ảnh | Cả bộ trong một ảnh |

**Ảnh người mẫu đang mặc là lỗi phổ biến nhất.** Model sẽ lẫn giữa "trang phục cần lấy" và "người
mẫu trong ảnh", cho ra kết quả pha trộn hai khuôn mặt hoặc hai dáng người.

## 3. Cách dùng trong ứng dụng

### Ghép từng món và mặc chồng lớp

Trang **Thử đồ ảo** có ô so sánh trước/sau ngay ở giữa. Sau khi ghép xong một món, bật
**"Mặc chồng lên ảnh này"** rồi chọn món tiếp theo — món mới sẽ được mặc lên chính ảnh kết quả vừa
rồi. Đây là cách mặc quần trước, áo sau.

Đánh đổi cần biết: **mỗi lần ghép là một lần model vẽ lại toàn bộ ảnh.** Ghép càng nhiều lớp,
khuôn mặt và dáng người càng trôi xa ảnh gốc. Kinh nghiệm: **tối đa 2–3 lớp**; nhiều hơn thì nên
dùng chế độ cả bộ.

### Thử cả bộ

Tab **Cả bộ** gửi tất cả món trong outfit trong **một** lời gọi. Ưu điểm là chỉ vẽ lại một lần nên
giữ được khuôn mặt tốt hơn hẳn so với ghép ba lớp liên tiếp. Nhược điểm là kết quả phụ thuộc vào
việc nhà cung cấp có xử lý tốt nhiều món cùng lúc hay không.

**Nên chọn cái nào:** cả bộ trước; nếu kết quả sai (thiếu món, ghép nhầm chỗ) thì chuyển sang mặc
chồng lớp để kiểm soát từng bước.

---

## 4. Dùng Gemini để sinh ảnh

### Kết quả kiểm chứng — đọc trước khi làm gì thêm

Kiểm chứng ngày **27/07/2026** bằng chính API key trong `application-secrets.properties`:

| Model | Kết quả |
|---|---|
| `gemini-3.1-flash-lite-image` | HTTP 429 — `limit: 0` |
| `gemini-3.1-flash-image` | HTTP 429 — `limit: 0` |
| `gemini-2.5-flash-image` | HTTP 429 — `limit: 0` |
| `gemini-3-pro-image` | HTTP 429 — `limit: 0` |
| `gemini-3.1-flash-lite` (text, đang dùng cho nhận diện) | **HTTP 200 ✅** |

Nghĩa là: **key vẫn tốt**, nhưng gói miễn phí của Google AI có hạn mức **bằng 0** cho mọi model
sinh ảnh. Muốn dùng phải **bật thanh toán** cho project ở [Google AI Studio](https://aistudio.google.com/).
Không có đoạn code nào thay thế được bước này.

Hai nhầm lẫn dễ mắc:

1. **`gemini-3.1-flash-lite` không sinh được ảnh.** Đó là model *text*. Bản sinh ảnh luôn có hậu
   tố `-image`: `gemini-3.1-flash-lite-image`.
2. **Endpoint khác nhau.** Sinh chữ dùng `/v1beta/models/{model}:generateContent`; sinh ảnh dùng
   `/v1beta/interactions`. Gọi nhầm endpoint sẽ báo lỗi khó hiểu.

### Bật lên thế nào

```properties
# application-secrets.properties
wearwise.gemini.api-key=<key đã bật thanh toán>
wearwise.gemini.image-model=gemini-3.1-flash-lite-image
```

Rẻ → đắt, chất lượng thấp → cao:
`gemini-3.1-flash-lite-image` → `gemini-3.1-flash-image` → `gemini-3-pro-image`.

Với thử đồ ảo, khuyến nghị bắt đầu ở `gemini-3.1-flash-image`: bản lite nhanh và rẻ nhưng hay sai
chi tiết vải và đường may — đúng thứ mà thử đồ cần đúng.

> `GeminiImageClient` đã viết theo tài liệu chính thức và endpoint đã xác nhận là tới được model
> (lỗi trả về là lỗi **hạn mức**, không phải "model không tồn tại"). Nhưng phần **đọc phản hồi**
> chưa chạy thử được lần nào vì hạn mức bằng 0. Khi bật billing, việc đầu tiên là chạy thử và đối
> chiếu lại hàm `extractImage`.

### Prompt mẫu

Nguyên tắc: **mô tả việc cần làm, và nói rõ cái gì phải giữ nguyên.** Phần "giữ nguyên" quan trọng
hơn phần "thay đổi" — model sinh ảnh mặc định sẽ vẽ lại tất cả nếu không bị chặn.

**Ghép một món:**

```
Ảnh 1 là ảnh một người. Ảnh 2 là một món trang phục chụp phẳng trên nền trắng.

Hãy tạo một ảnh mới trong đó người ở ảnh 1 đang mặc món trang phục ở ảnh 2.

BẮT BUỘC GIỮ NGUYÊN, không được thay đổi:
- Khuôn mặt, kiểu tóc, màu da của người
- Dáng đứng và tỉ lệ cơ thể
- Nền và ánh sáng của ảnh 1

BẮT BUỘC TÁI HIỆN ĐÚNG từ ảnh 2:
- Màu sắc, họa tiết và chất liệu vải
- Kiểu dáng cổ, tay áo, độ dài
- Mọi chữ hoặc logo in trên trang phục

Trang phục phải đổ nếp và ôm theo dáng người một cách tự nhiên.
Chỉ trả về ảnh, không kèm chữ.
```

**Ghép cả bộ:**

```
Ảnh 1 là ảnh một người. Các ảnh tiếp theo là từng món trang phục riêng lẻ.

Hãy tạo một ảnh mới trong đó người ở ảnh 1 đang mặc TẤT CẢ các món đó cùng lúc,
xếp lớp đúng cách (áo bỏ trong hoặc ngoài quần theo kiểu dáng tự nhiên của món).

BẮT BUỘC GIỮ NGUYÊN: khuôn mặt, kiểu tóc, màu da, dáng đứng, nền, ánh sáng.
BẮT BUỘC TÁI HIỆN ĐÚNG: màu sắc, họa tiết, chất liệu và kiểu dáng của từng món.
Không được bỏ sót món nào, không được tự thêm phụ kiện không có trong ảnh.
Chỉ trả về ảnh, không kèm chữ.
```

### Vì sao prompt lại viết như vậy

| Câu trong prompt | Nó chặn lỗi gì |
|---|---|
| "Giữ nguyên khuôn mặt, kiểu tóc, màu da" | Lỗi nặng nhất: model vẽ ra một người khác |
| "Giữ nguyên nền và ánh sáng" | Ảnh kết quả nhìn như ảnh ghép vụng vì nền đổi |
| "Tái hiện đúng chữ hoặc logo" | Chữ trên áo biến thành ký tự vô nghĩa |
| "Đổ nếp và ôm theo dáng người" | Trang phục nhìn như dán phẳng lên người |
| "Chỉ trả về ảnh, không kèm chữ" | Model trả lời bằng văn bản thay vì sinh ảnh |
| "Không tự thêm phụ kiện" | Model tự thêm mũ, túi, giày không có trong tủ đồ |

### Khi kết quả xấu — chẩn đoán theo triệu chứng

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Mặt biến thành người khác | Ảnh người chụp xa hoặc mờ | Chụp lại gần hơn, rõ mặt hơn |
| Trang phục sai màu | Ảnh trang phục bị ám màu do đèn | Chụp lại dưới ánh sáng trắng, nền trắng |
| Tay/chân bị méo | Tư thế che thân, hoặc đã ghép quá nhiều lớp | Đổi tư thế đứng thẳng; giảm số lớp |
| Món đồ bị bỏ sót khi thử cả bộ | Nhà cung cấp xử lý kém khi nhiều món | Chuyển sang mặc chồng từng lớp |
| Kết quả nhìn phẳng, như dán | Ảnh trang phục chụp chéo | Dùng ảnh flat-lay chính diện |

---

## 5. Chi phí

Mỗi lần ghép là **một lần gọi API có tính phí**, dù kết quả đẹp hay xấu. Vài điểm cần nhớ:

- Ghép chồng 3 lớp = **3 lần** tính tiền, không phải một.
- Hệ thống đã giới hạn **30 lượt/giờ cho mỗi tài khoản** (nhóm `EXTERNAL` trong `RateLimitFilter`);
  quản trị viên chỉnh được riêng cho từng tài khoản ở trang Quản trị.
- Ảnh kết quả được tải về và lưu lên Cloudinary ngay, vì URL của nhà cung cấp có hạn dùng.

Muốn tiết kiệm: chuẩn bị ảnh cho tốt ngay từ đầu. Ghép lại năm lần vì ảnh đầu vào xấu tốn hơn hẳn
so với việc chụp lại một tấm ảnh tử tế.

# REST API Cart System - Phase 1

Base URL mặc định: `http://localhost:8081`. Request body dùng `Content-Type: application/json`. ID và dữ liệu trong ví dụ chỉ minh họa cấu trúc, không phải dữ liệu seed hoặc kết quả runtime được đảm bảo.

## Authentication và danh sách endpoint

Cart API cần `Authorization: Bearer <JWT>`. Controller lấy Long user ID từ JWT principal; không nhận user ID trong body/header `X-User-Id`. Auth, Product và Health là public theo SecurityFilterChain.

| Method | Endpoint | Authentication | Request | Response | Thành công |
|---|---|---|---|---|---|
| POST | `/api/auth/register` | Public | RegisterRequest | RegisterResponse | 201 |
| POST | `/api/auth/login` | Public | LoginRequest | LoginResponse | 200 |
| GET | `/api/products` | Public | Không | List<ProductResponse> | 200 |
| GET | `/api/products/{id}` | Public | Long path ID | ProductResponse | 200 |
| GET | `/api/cart` | Bearer JWT | Không | CartResponse | 200 |
| POST | `/api/cart/items` | Bearer JWT | AddCartItemRequest | CartResponse | 200 |
| PUT | `/api/cart/items/{productId}` | Bearer JWT | UpdateCartItemRequest | CartResponse | 200 |
| DELETE | `/api/cart/items/{productId}` | Bearer JWT | Long path productId | CartResponse | 200 |
| GET | `/api/health` | Public | Không | Map<String, String> | 200 |

Các API giỏ thao tác theo productId; không có endpoint itemId, tổng tiền, checkout hoặc CRUD sản phẩm.

## Authentication API

### POST `/api/auth/register`

RegisterRequest có `String username`, `String password`. Ví dụ dùng placeholder cho password:

```json
{"username":"demo_user","password":"<password-do-nguoi-dung-chon>"}
```

Service kiểm tra username đã tồn tại, BCrypt hash password trước khi save; ID do persistence tạo. Response không có password:

```json
{"id":1,"username":"demo_user"}
```

Username đã tồn tại khi Service kiểm tra -> 409 `UserAlreadyExistsException`. Chưa có annotation validation cho username rỗng/null, độ dài hoặc password. Lỗi unique constraint trong cuộc đua register chưa được chuyển riêng thành 409.

### POST `/api/auth/login`

LoginRequest có `String username`, `String password`, cùng cấu trúc body đăng ký. Username không tồn tại hoặc password không khớp -> 401 với message `Invalid username or password`.

Response LoginResponse:

```json
{"token":"<JWT-tra-ve-tu-login>"}
```

Không có refresh token hoặc expiration field trong DTO; thời hạn nằm trong JWT. Không ghi hoặc chia sẻ token thật trong tài liệu/log dùng chung.

## Product API

### GET `/api/products`

Trả mảng ProductResponse; có thể rỗng nếu database chưa có dữ liệu:

```json
[{"id":10,"name":"Keyboard","price":49.90,"stock":12}]
```

ProductResponse có `Long id`, `String name`, `BigDecimal price`, `Integer stock`. Chưa có pagination/filter/sort ở contract Controller.

### GET `/api/products/{id}`

Nhận `Long id`, trả một ProductResponse. Không tìm thấy -> 404 với message `Product not found: <id>`. Không có API tạo sản phẩm; cần dữ liệu chuẩn bị bên ngoài API này.

## Cart API

CartResponse có `Long id`, `Long userId`, `List<CartItemResponse> items`. Item response chỉ chứa `Long productId`, `Integer quantity`; không expose item ID/cart ID:

```json
{"id":20,"userId":1,"items":[{"productId":10,"quantity":2}]}
```

### GET `/api/cart`

Cache hit trả cart từ Redis. Miss đọc database; nếu user chưa có cart, Service tạo giỏ rỗng, cache và trả 200. Vì vậy GET này có thể tạo dữ liệu. Cart rỗng trả `items: []`.

### POST `/api/cart/items`

AddCartItemRequest:

```json
{"productId":10,"quantity":2}
```

- Quantity dạng int phải lớn hơn 0; kiểm tra trước repository calls.
- Product phải tồn tại; quantity không vượt stock.
- Nếu chưa có cart, tạo cart; item hiện có được cộng quantity, rồi kiểm tra tổng quantity không vượt stock.
- Save item, đồng bộ danh sách trong Cart và evict cache sau write thành công.

Quantity không hợp lệ hoặc stock không đủ -> 400; product không tồn tại -> 404.

### PUT `/api/cart/items/{productId}`

UpdateCartItemRequest chỉ có quantity:

```json
{"quantity":4}
```

Quantity phải lớn hơn 0; Service tìm product, tìm cart theo user ID, tìm item theo `(cartId, productId)`, kiểm tra stock, rồi thay quantity mới. Không cộng quantity ở thao tác update. Response là CartResponse sau cập nhật; evict cache sau save.

Quantity/stock không hợp lệ -> 400; thiếu product/cart/item -> 404. PUT không tạo item hoặc cart mới.

### DELETE `/api/cart/items/{productId}`

Không có request body. Tìm cart theo user và item theo `(cartId, productId)`, xóa item, cập nhật danh sách trong Cart, evict cache rồi trả CartResponse. Không xóa Product hoặc Cart. Thiếu cart/item -> 404; method không cần lookup Product.

Thêm/cập nhật item chỉ kiểm tra stock; không giữ chỗ hoặc giảm stock của Product. Không có tính toán price snapshot, subtotal hoặc tổng giỏ.

## Health API

GET `/api/health` trả:

```json
{"status":"UP"}
```

Đây là Controller trả status cố định; không kiểm tra sức khỏe database/Redis và không phải Actuator readiness check.

## ErrorResponse và HTTP status

Handler trả `int status`, `String message`, `Instant timestamp`:

```json
{"status":404,"message":"Product not found: 10","timestamp":"2026-10-12T00:00:00Z"}
```

| Trường hợp | HTTP |
|---|---|
| ProductNotFoundException, CartNotFoundException, CartItemNotFoundException | 404 |
| UserAlreadyExistsException | 409 |
| AuthenticationException từ Business login | 401 |
| InvalidCartItemException, InsufficientStockException | 400 |
| Exception không có handler cụ thể | 500, message `Internal server error` |

Lỗi JWT/access ở SecurityFilterChain dùng `sendError`: Cart thiếu JWT hoặc JWT không hợp lệ -> 401; endpoint bị deny với user đã authenticated -> 403. Không bảo đảm lỗi Security có cùng JSON ErrorResponse của ControllerAdvice.

## Giới hạn validation và Swagger

DTO chưa có Bean Validation annotations; Controller chưa dùng `@Valid`. Quy tắc quantity/stock nêu trên áp dụng khi input đã chuyển được thành kiểu dữ liệu Service cần. Quantity null có thể gây lỗi unboxing trước khi vào Service; body JSON không hợp lệ, path ID không parse được hoặc input null có thể bị catch-all trả 500. Chưa có test khẳng định các trường hợp đó trả 400.

Swagger ở `/swagger-ui/index.html`, JSON ở `/v3/api-docs`. Scheme `bearerAuth` có type HTTP, scheme bearer, format JWT; chỉ Cart có security requirement. Nhập JWT thuần vào Authorize.

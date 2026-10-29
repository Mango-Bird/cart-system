# Cart System - Phase 1

Cart System là backend REST API cho một giỏ hàng thương mại điện tử đơn giản. Người dùng có thể đăng ký, đăng nhập, xem sản phẩm và quản lý giỏ hàng của chính mình.

Mục tiêu của Phase 1 là hoàn thiện luồng nghiệp vụ cơ bản và tổ chức mã nguồn theo các lớp rõ ràng. Hệ thống chưa xử lý thanh toán, đặt hàng hay quản trị sản phẩm.

## 1. Nghiệp vụ chính

Hệ thống có bốn nhóm chức năng:

- **Tài khoản:** đăng ký, đăng nhập; password được hash bằng BCrypt trước khi lưu.
- **Sản phẩm:** xem danh sách và xem chi tiết sản phẩm.
- **Giỏ hàng:** xem giỏ, thêm sản phẩm, cập nhật số lượng và xóa sản phẩm.
- **Health check:** kiểm tra backend đang phản hồi.

Các quy tắc chính của giỏ hàng:

- Mỗi người dùng có tối đa một giỏ hàng.
- Nếu người dùng chưa có giỏ, hệ thống tạo giỏ rỗng khi truy cập giỏ lần đầu.
- Một sản phẩm chỉ xuất hiện một lần trong cùng giỏ. Thêm lại sản phẩm sẽ cộng dồn số lượng.
- Số lượng phải lớn hơn `0` và không được vượt quá tồn kho hiện tại.
- Thao tác cập nhật thay thế số lượng cũ; thao tác xóa chỉ xóa item, không xóa sản phẩm hoặc giỏ hàng.
- Danh tính người dùng được lấy từ JWT đã xác thực, không nhận `userId` từ request.

## 2. Kiến trúc

```text
Client
  │
  ▼
Spring Security / JWT Filter
  │
  ▼
Presentation (Controller, DTO, Exception Handler)
  │
  ▼
Business (Domain Model, Service, Repository Interface)
  │
  ▼
Infrastructure (JPA Adapter, JWT, BCrypt, Redis Cache)
  │
  ├── MySQL
  └── Redis
```

Mã nguồn được chia thành ba phần chính:

```text
src/main/java/com/example/cart
├── presentation/     REST Controller, DTO, xử lý lỗi HTTP
├── business/         model, service, repository interface
└── infrastructure/   JPA, MySQL, Redis, JWT, BCrypt, Spring Security
```

Business layer là Java thuần, không import Spring MVC, JPA, Hibernate hay Redis. Các service chỉ làm việc với interface như `UserRepository`, `CartRepository`, `CartCache`, `TokenService` và `PasswordHasher`. Infrastructure triển khai các interface này và được nối với Business bằng constructor injection.

Luồng đọc giỏ hàng dùng cache-aside:

```text
GET cart → Redis hit → trả kết quả
         → Redis miss → đọc MySQL → ghi Redis (TTL 5 phút) → trả kết quả
```

Khi thêm, sửa hoặc xóa item, dữ liệu được ghi vào MySQL trước rồi cache của người dùng được xóa. MySQL vẫn là nguồn dữ liệu chính; Redis chỉ là cache.

## 3. Công nghệ sử dụng

- Java 21, Spring Boot 4.1.1
- Spring MVC, Spring Security
- Spring Data JPA, Hibernate, MySQL
- Spring Data Redis
- JWT HS256 (JJWT), BCrypt
- OpenAPI/Swagger UI
- JUnit 6, Mockito, MockMvc
- Docker, Docker Compose
- Locust/Kaggle CPU cho kiểm thử tải

## 4. API

Base URL mặc định: `http://localhost:8081`.

| Method | Endpoint | Chức năng | Xác thực |
|---|---|---|---|
| POST | `/api/auth/register` | Đăng ký tài khoản | Public |
| POST | `/api/auth/login` | Đăng nhập, nhận JWT | Public |
| GET | `/api/products` | Lấy danh sách sản phẩm | Public |
| GET | `/api/products/{id}` | Lấy chi tiết sản phẩm | Public |
| GET | `/api/cart` | Lấy giỏ của người dùng hiện tại | Bearer JWT |
| POST | `/api/cart/items` | Thêm sản phẩm hoặc cộng số lượng | Bearer JWT |
| PUT | `/api/cart/items/{productId}` | Thay đổi số lượng | Bearer JWT |
| DELETE | `/api/cart/items/{productId}` | Xóa sản phẩm khỏi giỏ | Bearer JWT |
| GET | `/api/health` | Kiểm tra trạng thái backend | Public |

### Ví dụ đăng ký và đăng nhập

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "demo_user",
  "password": "<password>"
}
```

Đăng ký thành công trả `201 Created`:

```json
{
  "id": 1,
  "username": "demo_user"
}
```

Đăng nhập dùng cùng cấu trúc request và trả token:

```json
{
  "token": "<JWT>"
}
```

Các API giỏ hàng gửi token qua header:

```http
Authorization: Bearer <JWT>
```

### Ví dụ thêm sản phẩm vào giỏ

```http
POST /api/cart/items
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "productId": 10,
  "quantity": 2
}
```

Response trả lại trạng thái hiện tại của giỏ:

```json
{
  "id": 20,
  "userId": 1,
  "items": [
    {
      "productId": 10,
      "quantity": 2
    }
  ]
}
```

Lỗi nghiệp vụ có dạng chung:

```json
{
  "status": 404,
  "message": "Product not found: 10",
  "timestamp": "2026-10-27T10:00:00Z"
}
```

Các mã lỗi chính: `400` cho quantity/tồn kho không hợp lệ, `401` khi đăng nhập hoặc JWT không hợp lệ, `403` khi endpoint bị từ chối, `404` khi không tìm thấy dữ liệu và `409` khi username đã tồn tại.

Tài liệu chi tiết: [docs/api.md](docs/api.md).

Khi ứng dụng đang chạy:

- Swagger UI: <http://localhost:8081/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8081/v3/api-docs>

## 5. Mô hình dữ liệu

```text
users
- id (PK)
- username (unique)
- password (BCrypt hash)

products
- id (PK)
- name
- price
- stock

carts
- id (PK)
- user_id (unique)

cart_items
- id (PK)
- cart_id
- product_id
- quantity
- unique(cart_id, product_id)
```

Các quan hệ được lưu bằng scalar ID. Entity không dùng `@OneToMany` hoặc `@ManyToOne`; repository adapter chịu trách nhiệm ghép `Cart` với danh sách `CartItem`.

## 6. Chạy bằng Docker

Yêu cầu: Docker và Docker Compose.

Tạo file môi trường từ mẫu:

```bash
cp .env.example .env
```

Điền các giá trị còn trống trong `.env`:

```dotenv
MYSQL_ROOT_PASSWORD=<root-password>
MYSQL_PASSWORD=<app-password>
JWT_SECRET=<base64-secret>
```

`JWT_SECRET` phải là Base64 của ít nhất 32 byte ngẫu nhiên. Không commit file `.env` hoặc secret thật.

Khởi động hệ thống:

```bash
docker compose up --build
```

Kiểm tra:

```bash
curl http://localhost:8081/api/health
```

Kết quả mong đợi:

```json
{"status":"UP"}
```

Docker Compose khởi động ba service: backend, MySQL và Redis. Chi tiết xem tại [docs/docker.md](docs/docker.md).

## 7. Kiểm thử

Chạy toàn bộ automated tests:

```bash
./mvnw test
```

Trên Windows PowerShell:

```powershell
.\mvnw.cmd test
```

Test hiện bao phủ business service, controller, JWT, authentication filter, SecurityFilterChain, OpenAPI security và Spring application context.

Kịch bản kiểm thử tải bằng Locust đã được chuẩn bị trong thư mục [`load-testing`](load-testing/README.md) để chạy trên Kaggle CPU.

## 8. Đề xuất cho Phase 2

Phase 2 tập trung giải quyết các vấn đề dễ gặp khi hệ thống được sử dụng trong thực tế:

| Bài toán | Ví dụ thực tế | Hướng nâng cấp |
|---|---|---|
| 1. Chịu tải khi sale | Nhiều khách cùng xem sản phẩm và giỏ khiến phản hồi chậm | Đo tải, xác định điểm nghẽn; triển khai Nginx và nhiều backend khi phù hợp. |
| 2. Duy trì dịch vụ khi backend lỗi | Một backend dừng khiến khách không thể thao tác | Chạy nhiều instance, dùng health check và chuyển request sang instance còn hoạt động. |
| 3. Giữ đúng số lượng khi thao tác đồng thời | Khách mở hai tab, cùng thêm hàng nhưng số lượng bị mất cập nhật | Bổ sung transaction và kiểm soát cập nhật đồng thời. |
| 4. Không thêm hàng trùng khi mạng chậm | Khách bấm lại hoặc ứng dụng retry cùng request | Dùng idempotency key cho API thêm sản phẩm. |
| 5. Giỏ hàng hiển thị dữ liệu mới | Đã sửa số lượng nhưng Redis vẫn trả dữ liệu cũ | Invalidate cache sau commit, phục hồi khi eviction lỗi và xử lý cuộc đua cache. |



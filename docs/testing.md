# Kiểm thử Cart System - Phase 1

## Chiến lược và phạm vi

Repository có Business unit tests, Presentation slices bằng MockMvc, JWT unit tests, security integration slice, OpenAPI tests và một Spring context test. Chưa có test class riêng cho JPA adapters, database CRUD, Redis serialization/TTL/invalidation hoặc BCrypt implementation.

### Business unit tests

JUnit và MockitoExtension mock Repository, PasswordHasher, TokenService và CartCache, không khởi động Spring/database/Redis:

- `AuthServiceTest`: hash trước save, username trùng, login hợp lệ, user không tồn tại, password sai và không tạo token khi xác thực thất bại.
- `ProductServiceTest`: danh sách, chi tiết và product không tồn tại.
- `CartServiceTest`: cache hit/miss, tạo/cache giỏ rỗng khi chưa có, quantity không hợp lệ, product/cart/item thiếu, stock, cộng quantity item hiện có, update/delete, đồng bộ items và eviction.

### Presentation tests

Bốn `@WebMvcTest` dùng MockMvc và `@MockitoBean` cho Service. Chúng kiểm tra HTTP status, JSON DTO, lỗi Business và verify tham số gọi Service. Auth test kiểm tra không có password trong RegisterResponse. Cart tests tự đặt Long principal vào SecurityContext và xóa sau mỗi test.

Presentation slices cấu hình `addFilters=false` để kiểm tra Controller riêng. Kết quả của các tests này không chứng minh request đi qua JWT filter; phần đó được test riêng dưới đây.

### JWT và security integration tests

- `JwtTokenServiceTest`: token/claims đúng, token bị sửa, sai signing key, hết hạn, User thiếu ID và signing key quá ngắn. Dùng Clock kiểm soát thời gian và khóa giả riêng cho test.
- `JwtAuthenticationFilterTest`: thiếu Authorization, kiểu không phải Bearer, token hợp lệ gán Long principal và token không hợp lệ không authenticate.
- `SecurityIntegrationTest`: MockMvc với SecurityConfiguration và JWT filter/service thật cùng CartService mock. Kiểm tra health public, cart thiếu/sai JWT trả 401, JWT hợp lệ chuyển user ID, endpoint deny trả 403 và không tạo HTTP session.

Đây là integration giữa security và MVC slice, không phải end-to-end với MySQL/Redis.

### OpenAPI tests

`OpenApiSecurityTest` gọi `/v3/api-docs` bằng MockMvc, kiểm tra HTTP Bearer/JWT scheme, requirement của cả bốn Cart operations và không có requirement ở năm operations public. Slice dùng SecurityConfiguration và fixture JWT; ba Service được mock. Test JSON OpenAPI không thay thế kiểm tra trực quan Swagger UI trong browser.

### Spring context test

`CartSystemApplicationTests.contextLoads` dùng `@SpringBootTest`, vẫn mock AuthService, ProductService và CartService. Hạ tầng JPA/Security/Redis vẫn có thể được khởi tạo; test cần database cùng các biến cấu hình. Mocks Service không chứng minh wiring Service → adapter thật hoặc database CRUD.

## Lệnh Maven và môi trường

Chuẩn bị Java 21, Maven, database/schema, Redis và biến môi trường theo [README](../README.md). Chạy trong cùng terminal đã cấu hình:

```powershell
Set-Location D:\cart-system
mvn clean test
mvn clean package
```

Package chạy lại tests trước khi tạo JAR. Không dùng skip test để báo build đã kiểm chứng. Báo cáo nằm ở `target/surefire-reports/`; clean xóa reports cũ.

Có thể chạy phạm vi cụ thể để điều tra, nhưng không coi kết quả đó là toàn bộ suite:

```powershell
mvn '-Dtest=AuthServiceTest,ProductServiceTest,CartServiceTest' test
mvn '-Dtest=SecurityIntegrationTest,OpenApiSecurityTest' test
```

Nếu Mockito self-attach treo trong môi trường sandbox, chạy trong terminal local có quyền JVM attachment phù hợp. Không sửa application hoặc tắt Security để che lỗi môi trường.

## Kết quả kiểm chứng ngày 12/10/2026

Đã chạy `mvn clean test` trong phiên viết tài liệu:

| Nhóm | Tests | Assertion failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Business | 26 | 0 | 0 | 0 |
| Presentation | 15 | 0 | 0 | 0 |
| JWT/filter/security | 18 | 0 | 0 | 0 |
| OpenAPI | 2 | 0 | 0 | 0 |
| Application context | 1 | 0 | 1 | 0 |
| Tổng | 62 | 0 | 1 | 0 |

61 tests pass; build thất bại ở `CartSystemApplicationTests.contextLoads`. Log ghi `Communications link failure` và `Connection refused` khi kết nối MySQL. Hibernate không lấy được JDBC metadata nên báo không xác định được dialect; đây là hậu quả của lỗi kết nối, không phải bằng chứng thiếu dependency/dialect.

Không thay đổi code để làm suite pass. Chưa xác nhận MariaDB compatibility, persistence CRUD, Redis thật hoặc application wiring đầy đủ. Không chạy `mvn clean package` trong phiên tài liệu này; lệnh trên là hướng dẫn sử dụng.

## Kịch bản manual API đề xuất

Các bước dưới đây chưa được thực thi trong phiên này:

1. Chạy database/Redis, cấu hình và khởi động ứng dụng; chuẩn bị product thật trong database.
2. Đăng ký qua Swagger; kiểm tra response không có password và database lưu hash.
3. Đăng nhập đúng/sai; lấy JWT, nhập JWT thuần vào Authorize.
4. GET cart: lần đầu tạo giỏ rỗng nếu chưa có; GET tiếp để quan sát cache.
5. Thêm một product, thêm lại để kiểm tra cộng quantity; update quantity rồi delete.
6. Thử quantity 0/âm, vượt stock, product/item không tồn tại; đối chiếu [HTTP contract](api.md).
7. Thử Cart API thiếu/token sai và JWT của hai user khác nhau; kiểm tra mỗi request thao tác giỏ của principal tương ứng.
8. Kiểm tra endpoint public và Swagger không cần token. Health trả UP không chứng minh DB/Redis khỏe.

## Xác minh Redis thủ công

Chỉ làm trên dữ liệu local/test. Các lệnh giả định `redis-cli` đang nối đúng Redis ứng dụng; thay user ID bằng ID thực tế, không ghi JWT vào CLI/history dùng chung.

```powershell
redis-cli --scan --pattern 'cart:user:*'
redis-cli GET 'cart:user:<userId>'
redis-cli TTL 'cart:user:<userId>'
redis-cli EXISTS 'cart:user:<userId>'
```

Nếu dùng WSL, thêm prefix `wsl` như phần chuẩn bị môi trường. Redis ở host/port khác cần truyền `-h`/`-p` theo cấu hình.

- Sau GET cache miss, mong đợi key có JSON Cart và TTL tối đa khoảng 300 giây.
- Sau GET cache hit, TTL tiếp tục giảm; không tự reset về 300.
- Sau add/update/delete thành công, kiểm tra key bị xóa trước khi GET tiếp; GET tiếp sẽ tạo lại key từ database.
- So sánh `cart_items` trong database với response. Redis không phải nguồn dữ liệu chính.
- Trên môi trường test riêng, thử lỗi cache để quan sát DB fallback/log; không kết luận eviction thành công khi Redis không truy cập được.

Chưa có automated test hoặc kết quả manual xác nhận các quan sát này. Khi eviction lỗi, cache cũ có thể còn tới hết TTL; request đồng thời cũng cần test riêng.

## Giới hạn và hướng kiểm thử tiếp

Cần kiểm thử chuyên sâu MariaDB compatibility, JPA constraints, adapter CRUD/hydration, BCrypt, Redis failure, đăng ký và write đồng thời, malformed/null request và standardized Security error body. Kết quả mới với MySQL/Redis Docker, API/cache smoke và Locust local ngày 15/10/2026 nằm trong [báo cáo load testing](load-testing.md); phần kết quả ngày 12/10 ở trên được giữ làm lịch sử. Chưa có benchmark Kaggle hoặc phép đo so sánh để định lượng lợi ích riêng của Redis.

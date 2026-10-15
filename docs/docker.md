# Docker — Cart System

## Phạm vi và cấu hình

Stack gồm backend Java 21, MySQL 8.4.6 và Redis 7.4.5. MySQL Connector/J 9.7.0 được quản lý bởi Spring Boot 4.1.1; không đổi driver, schema domain hoặc cấu hình local. Dockerfile build Maven multi-stage, runtime Temurin JRE 21.0.8+9, user UID/GID 10001. Các image dùng version tag cụ thể, chưa khóa digest; cần cập nhật bản vá có kiểm thử trước khi triển khai lâu dài.

Backend dùng `mysql:3306` và `redis:6379` trên Compose network. Database/Redis không có published port trong compose.yaml. MySQL lưu tại named volume `mysql-data`; Redis là cache có thể dựng lại, không lưu bền vững. Database là nguồn dữ liệu chính.

## Chuẩn bị và chạy

Yêu cầu Docker Engine với Linux containers, Docker Compose có BuildKit và kết nối tải image/Maven dependencies. Thực hiện tại root repository:

```powershell
Copy-Item .env.example .env
```

Điền MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD bằng các giá trị riêng và JWT_SECRET bằng Base64 của ít nhất 32 byte ngẫu nhiên. Ví dụ tạo riêng một khóa để đưa vào file local:

```powershell
python -c "import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

Không dùng giá trị minh họa làm secret. `.env` bị Git ignore và không vào Docker build context. Compose vẫn truyền credentials qua environment; người có quyền Docker có thể đọc chúng. Đây chưa phải giải pháp quản lý secrets của production.

| Biến | Ý nghĩa |
|---|---|
| MYSQL_ROOT_PASSWORD | Password quản trị MySQL, không dùng cho backend |
| MYSQL_PASSWORD | Password user `cart_app`, được backend dùng qua DB_PASSWORD |
| JWT_SECRET | Khóa Base64 cho HS256, không cung cấp cho Locust/Kaggle |
| JWT_TTL | Mặc định PT15M |
| BACKEND_PORT | Cổng host, mặc định 8081; chọn 18081 nếu 8081 bận |
| BACKEND_BIND_ADDRESS | Mặc định 127.0.0.1; đổi chỉ khi đã duyệt việc cho phép truy cập bên ngoài |

```powershell
docker compose config --quiet
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://127.0.0.1:8081/api/health
docker compose logs --tail 100 backend
```

Nếu đổi BACKEND_PORT, thay URL tương ứng. Không cần dùng cổng 8080. Không chạy đồng thời backend local và Docker trên cùng cổng host. Tránh chia sẻ output `docker compose config` không có `--quiet` vì có thể chứa secrets.

MySQL healthcheck thực hiện SELECT 1 bằng application user; backend chờ MySQL/Redis healthy theo `service_healthy`. Backend healthcheck gọi `/api/health`; endpoint này trả UP cố định nên **không chứng minh readiness DB/cache** sau khi khởi động. Dockerfile chạy package với `-DskipTests` vì context test cần dịch vụ bên ngoài; đây chỉ là bước đóng gói, không thay thế `mvn test`/`mvn package` riêng.

## Schema và dữ liệu thử

MySQL image tạo database `cart_system` và user `cart_app` ở lần khởi tạo volume đầu. Hibernate hiện tạo/cập nhật bảng bằng `ddl-auto=update`. Không thêm DDL hoặc migration giả. Sau backend healthy, có thể chủ động nạp hai sản phẩm trên database test:

```powershell
Get-Content -Raw load-testing/seed-products.sql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u cart_app cart_system'
```

SQL dùng đúng các cột `products(name, price, stock)` hiện có, không gán ID. Chạy lại không thêm sản phẩm có cùng tên; không chạy song song seed. Không tự nạp vào database production và không xóa dữ liệu người dùng. Chi tiết tài khoản workload: [load-testing](../load-testing/README.md).

## Persistence và kiểm tra chức năng

```powershell
docker compose restart mysql backend
docker compose ps
```

Sau restart, xác nhận có thể login bằng tài khoản test trước đó và products/cart còn tồn tại. Có thể dừng stack bằng `docker compose down`; named volume vẫn giữ. `down -v` sẽ xóa dữ liệu, không dùng để restart. Đổi password trong `.env` không thay password của user đã có trong volume: cần quy trình ALTER USER có kiểm soát, không tự xóa volume.

Kiểm tra API thực qua [prepare.py và smoke workload](../load-testing/README.md). Redis có thể kiểm tra riêng bằng `docker compose exec -T redis redis-cli ping`; PONG không chứng minh cache hit. Với user test, kiểm tra key `cart:user:<userId>` sau GET và xác nhận key bị xóa sau write.

## Kiểm thử Maven với dịch vụ container

Compose chính cố ý không publish MySQL/Redis. Maven chạy trên host cần database/Redis local đã cấu hình, hoặc override **chỉ cho kiểm thử**, publish cổng vào 127.0.0.1. Truyền DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PORT, JWT_SECRET cho terminal chạy Maven. Không thay application.properties hoặc tắt context test để bỏ qua dịch vụ.

```powershell
mvn test
mvn package
```

Kết quả kiểm chứng thực tế và giới hạn được ghi trong [báo cáo load testing](load-testing.md). Cấu hình này không tự tạo tunnel, TLS, reverse proxy hoặc endpoint Internet. Kaggle cần backend được triển khai tới địa chỉ đã được cho phép truy cập, xem [networking](load-testing.md).

Tham khảo: [Docker Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/), [Compose interpolation](https://docs.docker.com/reference/compose-file/interpolation/).

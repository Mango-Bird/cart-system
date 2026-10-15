# Docker và kiểm thử tải — Báo cáo kiểm chứng

## 1. Mục tiêu và trạng thái

Đóng gói backend cùng MySQL/Redis, kiểm tra hoạt động API thực và cung cấp bộ phát tải Locust chạy trên Kaggle CPU. Không sửa Business, domain, API, JWT hoặc schema để phục vụ benchmark.

**VERIFIED — Kaggle CPU Smoke: hai lượt 1 user / 30 giây, 47 requests tổng cộng, 0 failures.**

Ngày 15/10/2026 đã chạy từ Kaggle Interactive CPU qua HTTPS backend do người dùng cung cấp. Xem [kết quả và bằng chứng](kaggle-benchmark.md#8-kết-quả-kaggle-cpu-thực-tế--15102026). Không tạo tunnel hoặc public MySQL/Redis. Bảng mục 4 là phép đo **local** riêng; không trộn với Kaggle và không dùng để khẳng định capacity production. Kaggle Low/Medium/High: NOT RUN.

## 2. Audit và môi trường thực tế

Kiểm tra ngày 15/10/2026 (Asia/Bangkok):

| Thành phần | Thực tế |
|---|---|
| Architecture | Presentation → Business ports → Infrastructure adapters; Business Java thuần |
| Application | Spring Boot 4.1.1, Java 21; port container 8081 |
| Database | MySQL 8.4.6 container; Connector/J 9.7.0 do Spring Boot quản lý |
| Persistence | JPA/Hibernate, ddl-auto=update; không có migration/seed sẵn |
| Cache | Redis 7.4.5, Cache-Aside, TTL 5 phút, evict sau write |
| Authentication | JWT HS256 qua Bearer; Cart lấy user ID từ principal; TTL mặc định 15 phút |
| Backend runtime | Docker Desktop Linux, non-root UID/GID 10001; Temurin JRE 21.0.8+9 |
| Tài nguyên Docker Engine | 16 logical CPUs, 8,161,751,040 byte memory; không đặt giới hạn riêng cho mỗi container |
| Máy phát tải local | Windows 11 build 26200, Python 3.12.14, 16 logical CPUs |
| Công cụ phát tải | Locust 2.46.7, một process; Matplotlib 3.10.8; nbformat 5.10.4 |
| Kaggle CPU | Interactive, Accelerator=None, Internet on; Linux, Python 3.13.15, 4 logical CPUs, 33,658,318,848 bytes memory; Smoke đã chạy |

Máy phát tải và Docker cùng host, tài nguyên bị chia sẻ. Không đo CPU/RAM/connection pool/slow query phía backend trong quá trình phát tải; chưa xác định bottleneck. MariaDB không được sử dụng trong kiểm chứng này.

## 3. Cấu hình và workload

Các thay đổi gồm Dockerfile multi-stage, Compose có health dependency, MySQL volume, `.env.example` và `.dockerignore`. Cấu hình local application.properties được giữ nguyên. Backend dùng service names; Compose chính chỉ publish backend vào loopback. Kiểm tra Maven dùng override tạm trong target để publish database/Redis vào cổng loopback ngẫu nhiên; override không thuộc cấu hình triển khai.

Chi tiết vận hành: [Docker](docker.md). Chi tiết script và profile: [load-testing/README](../load-testing/README.md).

Dữ liệu chuẩn bị: hai sản phẩm có stock 1000, seed SQL chủ động sau khi Hibernate tạo bảng. Tài khoản `lt_<runId>_<index>` được tạo qua API, mỗi user một cart; prepare login và dọn item test trước khi đo. Không có password/token trong manifest hay báo cáo. Sản phẩm chung không bị trừ stock trong Business hiện tại.

Locust phân phối task GET products (weight 4), GET product detail (3), GET cart (4) và chuỗi POST quantity 1 → PUT quantity 2 → DELETE (2). Mỗi user thực hiện tuần tự; nghỉ 0.5–1.5 giây giữa các task. Login/relogin được tính vào metrics, preparation không tính. Ramp-up và graceful completion nằm trong phạm vi phép đo, không tách steady-state/warm-up.

Tất cả requests workload kỳ vọng HTTP 200. 4xx/5xx/transport errors hoặc DTO sai được tính failure. Register 409 cho tài khoản đã có chỉ được chấp nhận ở preparation và phải login thành công. Không trộn lỗi cố ý vào benchmark để làm giảm failure rate.

## 4. Kết quả local thực tế

| Profile | Users / spawn rate | Thời lượng cấu hình | Total requests | RPS | Average ms | Median ms | p95 ms | p99 ms | Failure rate |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| Smoke local | 1 / 1 user/s | 30s | 34 | 1.1548 | 22.4222 | 17 | 64 | 91 | 0% |
| Low local | 10 / 2 user/s | 2m | 1521 | 12.6951 | 17.6609 | 10 | 50 | 110 | 0% |

Smoke có thời gian thực tế 29.953s, low 119.891s theo event hooks; peak observed users lần lượt là 1 và 10, cả hai Locust exit code 0. RPS và percentiles được lấy từ row Aggregated của final-stats.csv, không tính trung bình các endpoint. Snapshot cuối đối chiếu khớp HTTP status counters: 34/34 và 1521/1521 requests, tất cả HTTP 200. Locust dùng histogram nên percentile là ước lượng. Thời gian event hooks có thể lệch run-time cấu hình do lịch chạy và graceful stop.

CSV định kỳ của Locust có thể thiếu vài request cuối; vì vậy reporter bổ sung final-stats.csv tại shutdown và dùng snapshot này cho summary, đối chiếu với HTTP status counters. CSV/HTML/summary thực tế được giữ local trong `load-testing/results/` (Git ignore), không commit dữ liệu nhạy cảm. Có `http-status.csv` phân bố status theo endpoint và outcome. Bản báo cáo lưu số liệu tổng hợp, không chứa username, token hoặc password.

Medium/high chưa chạy. Low có RPS lớn hơn smoke trong hai lần chạy này; không suy ra capacity hoặc hiệu năng tốt hơn chỉ từ hai mẫu. p95 thấp hơn ở low có thể chịu ảnh hưởng warm-up/JIT/cache và biến động máy dùng chung; chưa đo riêng các yếu tố đó. Bảng cấu hình sau không phải kết quả:

| Profile đề xuất | Users | Spawn rate | Duration |
|---|---:|---:|---|
| Smoke | 1 | 1/s | 30s |
| Low | 10 | 2/s | 2m |
| Medium | 50 | 5/s | 3m |
| High | 100 | 10/s | 5m |

## 5. Commands và verification

Đã thực hiện với quyền truy cập Docker daemon và Maven cache phù hợp:

```text
mvn test
mvn package
docker compose config --quiet
docker compose build backend
docker compose -f compose.yaml -f target/compose.verify.yaml up --build -d
docker compose ps
python -m py_compile load-testing/locustfile.py load-testing/prepare.py load-testing/run.py
python -m unittest discover -s load-testing -p test_suite.py -v
python load-testing/prepare.py --users 10 --output load-testing/results/accounts.json
python load-testing/run.py --profile smoke --output load-testing/results/final-smoke
python load-testing/run.py --profile low --output load-testing/results/final-low
```

Các lệnh Python thực tế dùng interpreter trong `load-testing/.venv`; environment credentials được cấp qua môi trường, không đưa vào dòng lệnh hoặc log báo cáo. Dockerfile package bỏ chạy tests khi build image, nhưng Maven test/package riêng ở đây không skip tests.

| Hạng mục | Kết quả |
|---|---|
| Maven test | PASS — 62 tests, 0 failures, 0 errors, 0 skipped, có contextLoads |
| Maven package | PASS — chạy lại suite 62 tests, tạo executable JAR |
| Docker config/build | PASS |
| Compose startup | PASS — backend, MySQL, Redis healthy |
| Backend non-root | PASS — UID/GID 10001 |
| Functional smoke | PASS — chín endpoint, Bearer guard, DTO, read/add/update/delete |
| Redis integration | PASS — PONG, key sau GET, TTL trong 1..300s, key bị evict sau các write |
| Database persistence sau restart | PASS — login, sản phẩm và item đã lưu còn sau restart; xóa cache trước khi đọc lại |
| Python tooling tests | PASS — 5 tests; sandbox tempfile ban đầu bị từ chối, chạy lại ngoài sandbox thành công |
| Notebook JSON schema và code cells | PASS — notebook chưa có execution/output; kiểm tra không thực thi benchmark |
| Locust local smoke / low | PASS — exit code 0; 34 / 1521 requests; 0 failures |
| Kaggle actual benchmark | VERIFIED Smoke: 2 runs, 1 user/30s each, 0 failures; Low/Medium/High NOT RUN |

Một lần `up --wait` khi chuyển override ngay sau restart báo backend unhealthy. Sau khi MySQL khởi động lại, backend tự phục hồi theo restart policy; chạy lại `docker compose up -d --wait --wait-timeout 120` thành công và xác nhận persistence/API. Không bỏ qua lần chờ thất bại này hoặc sửa Business/Security để che lỗi.

## 6. Kaggle CPU và networking

Quy trình mới nhất: [Kaggle CPU từng bước](kaggle-benchmark.md). Notebook đã dùng cấu hình không tương tác, Secret fail-closed, HTTPS preflight không redirect, evidence theo từng run và công cụ report đối chiếu artifacts. Các số liệu local ở trên được giữ nguyên; những cải tiến tooling này **không phải** một lần chạy Kaggle.

Notebook [kaggle_load_test.ipynb](../load-testing/kaggle_load_test.ipynb) nhận suite từ Kaggle Dataset, cài dependencies, lấy password qua Kaggle Secrets, kiểm tra HTTP/product data, chuẩn bị accounts rồi chạy Locust headless. Nó đọc CSV thật, tính metrics, vẽ RPS/concurrency và xuất ZIP/CSV/HTML. Không có cell output giả hoặc benchmark data dựng sẵn.

Kaggle là **máy phát tải**, không chạy Spring Boot/database/Redis trong thiết kế này. `localhost` của Kaggle không trỏ tới Windows. Backend cần origin HTTPS được phép truy cập từ Kaggle; không giả định Notebook có inbound traffic. Kiểm tra setting Internet, Accelerator=None và các giới hạn phiên thực tế trước khi chạy. Không đổi compose bind address hoặc tạo tunnel tự động.

Quy trình cho lần chạy mới (không tự tăng tải): triển khai backend test tại địa chỉ đã duyệt; chuẩn bị sản phẩm; đính kèm suite Dataset; bật Internet và cấp Secret LOAD_TEST_PASSWORD; chạy smoke trước rồi tăng tải có kiểm soát; tải artifacts và cập nhật bảng so sánh bằng số liệu thực. Ghi cả cấu hình backend và generator, revision source, vị trí mạng và resource limits để có thể diễn giải kết quả.

## 7. Hạn chế và hướng cải thiện

- Smoke/low local là kiểm chứng tooling và chức năng dưới tải nhỏ; chưa phải xác nhận SLA hoặc capacity. Chưa chạy lặp nhiều lần, chưa tách cold/warm cache, chưa đo steady-state.
- Kaggle Smoke đã chạy hai lượt; chưa chạy Low/Medium/High. Chưa xác định bottleneck hoặc capacity production; số liệu chi tiết ở kaggle-benchmark.md.
- JWT không có refresh endpoint; workload relogin qua API hiện có. Shared test password chỉ dùng cho tài khoản test, không phải tài khoản thật; mỗi VU vẫn có identity/cart riêng.
- Single-process Locust; không hỗ trợ chia sẻ manifest giữa workers. Cần phân vùng tài khoản trước khi thiết kế distributed load.
- Test accounts còn trong DB; script không có API xóa user và không tự thực hiện SQL phá dữ liệu. Seed SQL chỉ dùng trong môi trường test.
- Cache best-effort có thể stale khi eviction lỗi hoặc request đồng thời; workflow Business chưa có transaction bao trùm/optimistic locking. Các cơ chế này được giữ nguyên.
- Backend health UP không chứng minh DB/cache readiness. Compose dùng service health dependency lúc startup, không thay thế quan sát hệ thống liên tục.
- Các image có version tag rõ, chưa pin digest. Credentials qua environment phù hợp thử nghiệm nhưng cần quy trình quản lý secrets riêng khi triển khai lâu dài.
- Muốn xác định bottleneck cần thu thập CPU/RAM, GC, request threads, pool JDBC, query timing và Redis hit/miss cùng mốc thời gian, sau đó mới đề xuất tối ưu.

Tham khảo: [Locust event hooks](https://docs.locust.io/en/stable/extending-locust.html), [Docker startup order](https://docs.docker.com/compose/how-tos/startup-order/), [Kaggle Notebooks](https://www.kaggle.com/docs/notebooks).

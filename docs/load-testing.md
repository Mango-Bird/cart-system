# Báo cáo kiểm thử tải Cart System

Ngày cập nhật tài liệu: 29/10/2026. Số liệu trong báo cáo là kết quả lịch sử ngày 15/10/2026, không phải kết quả chạy mới. Lượt cập nhật này chỉ đọc source và artifacts local.

## 1. Mục tiêu và phạm vi

Kiểm tra các API sản phẩm và giỏ hàng dưới tải HTTP bằng Locust, đồng thời xác nhận bộ phát tải có thể chạy trên Kaggle CPU. Backend, MySQL và Redis là hệ thống được kiểm thử; Locust là máy phát tải riêng.

- Backend: Java 21, Spring Boot 4.1.1, Spring MVC, JPA/Hibernate, MySQL, Redis và JWT.
- Đóng gói: Dockerfile nhiều giai đoạn, Docker Compose gồm backend, MySQL và Redis. Xem [hướng dẫn Docker](docker.md).
- Tooling: Python, Locust 2.46.7 và notebook Kaggle. Đây là công cụ ngoài backend Java.
- Không sửa Business, API, authentication hoặc schema để làm đẹp kết quả.
- Không benchmark checkout, payment, giữ tồn kho hoặc nhiều người cùng sửa một giỏ.

## 2. Workload và dữ liệu

Mỗi virtual user có tài khoản và giỏ riêng. `prepare.py` tạo dữ liệu trước thời gian đo, chọn sản phẩm có stock ít nhất 2, đăng nhập và chuẩn bị giỏ. Password lấy từ environment hoặc Kaggle Secrets; manifest không chứa password hay JWT.

| Task | Weight | Kỳ vọng |
|---|---:|---|
| GET /api/products | 4 | HTTP 200 và danh sách JSON |
| GET /api/products/{id} | 3 | HTTP 200 và đúng product ID |
| GET /api/cart | 4 | HTTP 200, đúng userId và items |
| POST quantity 1, PUT quantity 2, DELETE item | 2 | Ba request thành công, không tích lũy item qua vòng sau |

Weight áp dụng theo task, không phải tỷ lệ request. Mỗi user nghỉ 0.5-1.5 giây giữa các task. Login và relogin nằm trong thống kê; preparation nằm ngoài. HTTP 4xx, 5xx, lỗi mạng hoặc response DTO sai đều được tính là failure. Workload không cố tình gửi input lỗi.

| Profile | Users | Spawn rate | Thời lượng cấu hình |
|---|---:|---:|---|
| Smoke | 1 | 1/s | 30 giây |
| Low | 10 | 2/s | 2 phút |
| Medium | 50 | 5/s | 3 phút |
| High | 100 | 10/s | 5 phút |

Các profile là cấu hình, không phải cam kết chịu tải. Chỉ chạy mức được chủ backend cho phép. Phép đo hiện dùng một process Locust; không chạy nhiều process chung manifest.

## 3. Kết quả local đã có

Nguồn: `load-testing/results/final-smoke/summary.json` và `load-testing/results/final-low/summary.json`, đối chiếu với artifacts cùng thư mục. Máy phát tải Windows; backend Docker chạy cùng host nên chia sẻ tài nguyên.

| Profile | Users | Thời lượng đo (s) | Requests | RPS | Avg (ms) | p95 (ms) | p99 (ms) | Failures |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Smoke | 1 | 29.953 | 34 | 1.1548 | 22.42 | 64 | 91 | 0 |
| Low | 10 | 119.891 | 1521 | 12.6951 | 17.66 | 50 | 110 | 0 |

Không suy ra Low luôn nhanh hơn Smoke: hai lượt có thể khác trạng thái warm-up/cache và tải nền. Không cộng RPS hoặc lấy trung bình percentile giữa các lượt. Medium/High chưa có kết quả trong phạm vi báo cáo này.

## 4. Kết quả Kaggle CPU đã có

Hai lượt Smoke chạy ngày 15/10/2026 từ Kaggle Interactive, Accelerator=None, Internet bật. Runtime ghi nhận Python 3.13.15, Locust 2.46.7, Linux, 4 logical CPUs và 33,658,318,848 byte RAM. Đây là thông số máy phát tải, không phải tài nguyên backend.

| Run ID | Users | Thời lượng đo (s) | Requests | RPS | Avg (ms) | p95 (ms) | p99 (ms) | Failures |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| smoke-20261015T042407-d168fdd5 | 1 | 29.843 | 23 | 0.7729 | 406.19 | 760 | 790 | 0 |
| smoke-20261015T042451-1c63356a | 1 | 29.909 | 24 | 0.8047 | 526.64 | 870 | 1200 | 0 |

Cả hai cấu hình 30 giây, spawn 1/s, execution COMPLETED, Locust exit code 0. Tổng cộng 47 request, tất cả HTTP 200 và không có failure. Không gộp hai lượt thành một phép đo 60 giây.

Trạng thái lịch sử: **READY TO RUN: PASS; EXECUTED: YES; VERIFIED: Kaggle CPU Smoke**. Kaggle Low/Medium/High: **NOT RUN**. Chi tiết bằng chứng và quy trình xem [Kaggle benchmark](kaggle-benchmark.md).

Latency Kaggle gồm đường Internet, proxy HTTPS và backend. Không so trực tiếp với local để kết luận Java hoặc Redis nhanh/chậm hơn. Cache hit/miss: **NOT MEASURED**.

## 5. Networking

`localhost` trong Kaggle là máy Kaggle. Backend cần HTTPS origin đang hoạt động, được phép nhận tải từ Kaggle và có chứng chỉ hợp lệ.

- Dùng backend test triển khai riêng hoặc reverse proxy/tunnel do chủ hệ thống thiết lập và phê duyệt.
- Chỉ expose API backend, không expose MySQL hoặc Redis.
- Không đưa password, JWT hoặc signing secret vào URL, notebook hay Git.
- Không tự đổi firewall, bind address hoặc tạo tunnel khi chưa được phép.
- Backend trong lượt lịch sử dùng HTTPS Cloudflare URL do người dùng cung cấp; URL tạm thời không được xem là địa chỉ ổn định cho lần chạy sau.

Preflight kiểm tra DNS, TLS, GET health và products. PASS không chứng minh database/cache luôn sẵn sàng hoặc toàn bộ write API đúng. Health hiện trả UP cố định.

## 6. Artifacts và tái kiểm tra

| Artifact | Ý nghĩa |
|---|---|
| final-stats.csv | Snapshot cuối, nguồn thống kê tổng và percentile |
| http-status.csv | Phân phối status và outcome theo request |
| run-metadata.json | Users, thời lượng, spawn rate và counters |
| summary.json | Kết quả tổng hợp, không phải bằng chứng nguồn Kaggle độc lập |
| execution.json | Trạng thái process và exit code |
| locust_stats_history.csv | Diễn biến concurrency và RPS |
| report.html, locust.log | Báo cáo và chẩn đoán |
| evidence/*/environment.json | Runtime, revision khai báo và script hashes |

Artifacts Kaggle local: `load-testing/results/kaggle-20261015/`. Kết quả đối chiếu lại: `load-testing/results/kaggle-reviewed-analysis/`. Các thư mục này bị Git ignore; clone repository mới không tự có dữ liệu thô. Cần lưu archive riêng nếu cần kiểm chứng trên máy khác.

Chạy tổng hợp trên artifacts đã tải, không phát tải mới:

```powershell
load-testing/.venv/Scripts/python load-testing/report.py --results load-testing/results/kaggle-20261015/results --output load-testing/results/review-new
```

`report.py` đối chiếu counters và metrics từ artifacts thật, báo invalid nếu thiếu hoặc lệch. Tool không tự xác nhận Kaggle VERIFIED chỉ từ biến môi trường. Không sửa raw summary để biến trạng thái tự động thành VERIFIED.

## 7. Giới hạn và hướng tiếp theo

- Chưa có căn cứ xác nhận SLA hoặc số người dùng tối đa trong đợt sale.
- Workload giỏ riêng không kiểm chứng lost update khi nhiều request cùng sửa một giỏ.
- Chưa tách cold/warm cache hoặc đo riêng lợi ích bật/tắt Redis.
- Không có telemetry CPU, GC, JDBC pool, SQL và cache cùng cửa sổ đo để xác định bottleneck.
- Backend revision của bằng chứng Kaggle ghi unknown; không gán kết quả cho commit backend mới hơn.
- Tài khoản test vẫn có thể còn trong database; script không tự xóa user bằng SQL.
- Cần integration tests cho transaction, concurrency, cache failure trước khi tăng tải hoặc đánh giá load balancing.

Xem [tooling và lệnh chạy](../load-testing/README.md), [kiểm thử ứng dụng](testing.md), [mục lục](README.md).

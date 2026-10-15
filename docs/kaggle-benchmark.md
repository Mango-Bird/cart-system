# Kaggle CPU — Quy trình chạy benchmark thực tế

## 1. Trạng thái bàn giao

| Mức | Hiện trạng |
|---|---|
| READY TO RUN | Notebook, scripts và hướng dẫn đã chuẩn bị; cần URL HTTPS được duyệt, Kaggle session CPU và Secret |
| EXECUTED | Các phép đo local trước đây đã chạy; chưa có phép đo Kaggle trong phiên này |
| VERIFIED | Tooling được kiểm tra bằng unit tests/schema; **Kaggle benchmark NOT VERIFIED / NOT RUN** |

**NOT EXECUTED — Kaggle benchmark requires an accessible backend and an actual Kaggle run.**

Không đổi Business, API hoặc authentication. Không tạo tunnel, không đổi firewall, DNS, reverse proxy hoặc public endpoint trong phiên chuẩn bị này. Bảng số liệu local trong [báo cáo](load-testing.md) không được đổi nhãn thành Kaggle.

## 2. Audit trước khi sửa

Các điểm đã tìm thấy trong bản notebook/scripts trước lượt hoàn thiện:

| Hạng mục | Hiện trạng / vấn đề |
|---|---|
| CPU và dependencies | Locust chạy subprocess, không monkey-patch kernel Jupyter; phiên bản đã pin. Chỉ được kiểm tra local Python 3.12, chưa chứng minh chạy Kaggle thực tế. |
| Secrets | Notebook dùng input/getpass; batch có thể chờ tương tác. Một số lỗi Kaggle Secrets không nằm trong nhóm exception đang bắt. |
| Target | Kiểm tra URL notebook thiếu path/query/fragment/port và localhost có dấu chấm; requests tự follow redirect. |
| JWT | Cơ chế Bearer đúng API; đọc exp chỉ để hẹn relogin, server xác minh token. Không cần signing secret trong máy phát tải. |
| Isolation | Prepare tạo tài khoản riêng, nhưng runner chỉ kiểm tra số lượng manifest; chưa từ chối identity trùng trong file được sửa thủ công. |
| Headless / timeout | Có HTTP timeout và giới hạn subprocess; thiếu trạng thái rõ khi timeout/mất artifacts, chưa từ chối timeout NaN/Infinity. |
| Reports | Có snapshot cuối và status counters; summary chưa tự đối chiếu counts, comparison chưa có duration/status distribution. |
| Cache | Chưa có application-scoped hit/miss measurement; không được suy ra cache hit từ GET latency. |

Đã bổ sung validation dùng chung, preflight GET-only, trạng thái execution, report validator, evidence theo từng run và notebook không cần nhập tương tác. Các thay đổi này chỉ thuộc tooling.

## 3. Phương án kết nối HTTPS — đề xuất, chưa triển khai

Phương án đề xuất là một VM test do nhóm kiểm soát, có DNS và chứng chỉ TLS hợp lệ. Chạy Docker Compose trên VM; reverse proxy trên host chuyển request đến backend loopback. Nếu đã có nền tảng triển khai HTTPS đáp ứng cùng điều kiện, có thể dùng endpoint đó.

```text
Kaggle CPU (load generator)
    -> HTTPS :443 -> DNS / TLS reverse proxy
    -> HTTP 127.0.0.1:18081 -> Spring Boot backend (system under test)
    -> Compose network -> MySQL / Redis (không published ports)
```

Chủ hạ tầng cần phê duyệt trước khi triển khai endpoint hoặc thay đổi firewall/DNS. Người vận hành sau khi được duyệt thực hiện:

1. Chọn máy test riêng; ghi CPU/RAM, giới hạn container, vùng mạng và revision/image.
2. Dùng [Compose hiện có](docker.md); giữ BACKEND_BIND_ADDRESS=127.0.0.1, chọn BACKEND_PORT=18081 nếu cổng mặc định bận. Giữ MySQL/Redis chỉ trong Compose network.
3. Cấu hình reverse proxy và chứng chỉ TLS hợp lệ cho hostname đã duyệt. Route giữ nguyên `/api/...`, không rewrite API, không loại bỏ Authorization và không chuyển sang HTTP qua redirect.
4. Xác định các port/nguồn truy cập được phép với chủ hạ tầng; không public 3306/6379/8081 để giải quyết kết nối Kaggle. Không tự tắt WAF/rate limit hoặc thay authentication.
5. Chuẩn bị database test và product có stock >= 2; không dùng dữ liệu người dùng thật. Chốt giới hạn users, thời lượng, khoảng thời gian chạy và điều kiện dừng.
6. Kiểm tra backend/DB/Redis tại host rồi chạy preflight từ Kaggle. TLS và kết nối từ máy Windows không chứng minh đường mạng Kaggle hoạt động.

Proxy/CDN, TLS, mạng Internet và rate limiting đều nằm trên đường đo. Ghi cấu hình của chúng khi diễn giải số liệu. Không khẳng định RPS/latency phản ánh riêng thời gian xử lý Java.

## 4. Hướng dẫn chạy từng bước

### Bước 1 — Tạo notebook CPU

Đăng nhập Kaggle, tạo Notebook Python. Trong Settings chọn Accelerator=None (CPU). Ghi nhận thông tin CPU/RAM thực tế do notebook thu thập; không dùng thông số quota hoặc phần cứng của một tài khoản khác.

### Bước 2 — Import notebook và scripts

Import [kaggle_load_test.ipynb](../load-testing/kaggle_load_test.ipynb). Tạo Dataset **private** chỉ chứa:

```text
load-testing/
  requirements.txt
  tooling.py
  check_connectivity.py
  prepare.py
  locustfile.py
  run.py
  report.py
```

Không upload cả working tree: nó có thể chứa `.env`, virtualenv, accounts hoặc artifacts cũ. Attach Dataset vào notebook rồi sửa SUITE_DIR đúng đường dẫn `/kaggle/input/...` thực tế. Notebook copy scripts sang `/kaggle/working` để có quyền ghi, lưu SHA256 của từng script và revision do người vận hành khai báo.

### Bước 3 — Bật Internet và cài dependencies

Bật Internet trong settings nếu tài khoản/phiên cho phép. Chạy cell cài requirements; có timeout 600 giây và ghi pip-freeze sau đó. Notebook yêu cầu Python >=3.11, bộ công cụ hiện được kiểm tra local trên 3.12. Phiên Kaggle thực tế vẫn cần xác nhận cài đặt thành công; package mirror, giới hạn Internet hoặc môi trường competition có thể khác.

Locust được import trong subprocess, không trong kernel notebook. Nếu cài lỗi thì dừng trước khi phát tải; không dùng số liệu của môi trường chưa cài đủ dependencies.

### Bước 4 — Cấp Kaggle Secret

Trong Add-ons → Secrets, tạo label `LOAD_TEST_PASSWORD` và cấp quyền cho notebook. Dùng password riêng cho tài khoản test, không dùng DB password hoặc tài khoản thật. Không cấp JWT_SECRET/MySQL/Redis credentials.

Notebook lấy secret qua UserSecretsClient và dừng với thông báo chung nếu thiếu quyền hoặc không đọc được. Không fallback sang input/getpass, không in giá trị hoặc exception nội bộ vào output. Không đặt password trực tiếp trong cell.

### Bước 5 — Cấu hình target và quyền phát tải

Điền cell cấu hình:

```python
TARGET_BASE_URL = "https://<hostname-backend-da-duyet>"
CPU_CONFIRMED = True             # sau khi chọn Accelerator=None
SOURCE_REVISION = "<revision-cua-suite>"
BACKEND_DESCRIPTION = "<CPU/RAM, image/revision, region, resource limits hoặc unknown>"
PROFILE = "smoke"
APPROVED_MAX_USERS = 1
```

URL chỉ là origin, không có `/api`, query, fragment, username hoặc password. Không dùng localhost, địa chỉ IP private hoặc Windows LAN IP. Biến LOAD_TEST_REQUIRE_HTTPS=1 được notebook đặt cho prepare/runner. Gán CPU_CONFIRMED hay origin chỉ là khai báo của người vận hành, không tự chứng minh một run đã VERIFIED.

### Bước 6 — Connectivity check từ Kaggle

Cell preflight chạy:

```python
subprocess.run([sys.executable, str(WORK / "check_connectivity.py"),
                "--output", str(WORK / "connectivity.json")], check=True, timeout=40)
```

Nó kiểm tra DNS trả IP public, chứng chỉ TLS, GET health và GET products, timeout hữu hạn và không follow redirect. Cần HTTP 200/JSON đúng, ít nhất một product stock >= 2. Health UP không phải readiness toàn bộ DB/cache; prepare tiếp tục kiểm tra login/giỏ.

| Lỗi | Cách xử lý |
|---|---|
| DNS / connection / timeout | Kiểm tra Internet Kaggle, hostname, routing và quyền truy cập với chủ hạ tầng |
| TLS | Sửa hostname/certificate chain; không dùng verify=False |
| 301/302/307/308 | Dùng origin cuối đã duyệt hoặc sửa proxy; không tự follow redirect với credentials |
| 401/403 trước login | Kiểm tra proxy/WAF/access policy; không sửa JWT hoặc tắt Security |
| Không có product | Nạp seed trên database test từ phía vận hành; không tạo API quản trị mới |

`--allow-local-http` chỉ phục vụ kiểm tra local script; không dùng trong Kaggle benchmark.

### Bước 7 — Smoke

Giữ PROFILE=smoke, APPROVED_MAX_USERS=1. Chạy cell tạo run/evidence, prepare và runner. Prepare có timeout 1200 giây, nằm ngoài phép đo; nó tạo user/cart riêng và có thể làm ấm cache qua GET cart. Không giả định cold-cache.

Smoke: 1 user, spawn 1/s, 30 giây, nghỉ 0.5–1.5s giữa task. Chỉ tiếp tục khi exit code 0, có summary/final CSV/status counters khớp, không có lỗi. Login/relogin tính vào metrics; prepare không tính. Mọi 4xx/5xx trong workload đều là failure; register 409 chỉ được chấp nhận ở preparation nếu login thành công.

### Bước 8 — Low

Sau smoke thành công và chủ backend cho phép, đổi PROFILE=low, APPROVED_MAX_USERS=10. Chạy lại từ cell tạo run/evidence → prepare → runner → report/plot/export. Low: 10 users, spawn 2/s, 2 phút. Mỗi run có ID/thư mục và manifest riêng, không ghi đè kết quả cũ.

### Bước 9 — Medium/High nếu đủ điều kiện

Chỉ tăng khi các mức trước thành công, không có dấu hiệu quá tải và có người theo dõi backend:

| Profile | Users | Spawn rate | Duration |
|---|---:|---:|---|
| Medium | 50 | 5/s | 3m |
| High | 100 | 10/s | 5m |

Đặt APPROVED_MAX_USERS tương ứng giới hạn được duyệt. Dừng khi lỗi, latency vượt ngưỡng đã thống nhất hoặc tài nguyên backend không còn an toàn. Nếu dừng sớm, giữ artifacts và ghi INCOMPLETE, không báo thành công cho đủ duration.

Runner giới hạn duration tối đa 55 phút; HTTP timeout trong (0,120] giây. Nó dùng timeout duration+60s để có thời gian graceful stop và xuất report; INCOMPLETE được ghi khi subprocess timeout/interruption. Đừng bọc thêm timeout ngắn hơn vào notebook, vì có thể cắt ngang bước xuất artifacts. Nếu Kaggle kernel bị hủy, không bảo đảm cleanup; giữ phiên đó là chưa hoàn tất.

### Bước 10 — Export và rà soát

Chạy cell report và export kể cả khi workload có lỗi. ZIP mới bao gồm results, evidence, analysis; không copy accounts hoặc environment secrets. Mỗi run có CSV snapshot cuối, HTTP status, metadata, summary, Locust HTML/log; comparison xuất CSV/HTML/JSON. Tải ZIP từ Output của Kaggle; trước khi chia sẻ, rà soát target, môi trường và logs. Không tự public notebook hoặc ZIP.

Save Version/Run All chỉ phù hợp sau khi điền biến cấu hình và cấp Secret. Để tránh tăng tải không có giám sát, mặc định notebook chỉ chạy một profile smoke. Sau đó chủ động duyệt từng mức tải.

## 5. Tổng hợp và phân tích kết quả

Sau khi tải artifacts về, chạy từ root repository:

```powershell
load-testing/.venv/Scripts/python load-testing/report.py --results <thu-muc-results> --output load-testing/results/kaggle-analysis
```

Công cụ đọc dữ liệu thật và đối chiếu final-stats.csv, http-status.csv, run-metadata.json, summary.json; đánh dấu invalid nếu thiếu hoặc không khớp. Nó không sửa summary cũ, không tạo hàng số liệu khi chưa chạy, không tự đánh dấu Kaggle VERIFIED.

| Metric | Nguồn / cách diễn giải |
|---|---|
| Concurrent users | configured_users và peak_observed_users; history CSV cho diễn biến |
| Total requests / RPS | Aggregated của snapshot cuối; không cộng RPS từng run |
| Average / p95 / p99 | Snapshot cuối; percentile theo histogram, không trung bình percentile của endpoint |
| Failure rate | Failure Count / Request Count; HTTP 200 nhưng sai DTO vẫn là lỗi |
| HTTP status distribution | http-status.csv theo endpoint, status và outcome; có transport_error |
| Duration | run-metadata.json; gồm ramp-up/login/graceful completion, không phải steady-state riêng |
| Cache hit/miss | NOT MEASURED trong công cụ hiện tại |

Không suy cache hit/miss từ latency hoặc Redis INFO tổng: các counters đó không giới hạn riêng Cart/workload/run và còn chịu request khác. Nếu có số đo app-scoped từ hệ quan sát hiện hữu, lưu riêng nguồn, cửa sổ thời gian, baseline và đơn vị; chưa đưa số đo đó vào bảng khi chưa xác minh. Không thêm instrumentation vào Business trong nhiệm vụ này.

So sánh các mức tải chỉ khi backend, revision, dữ liệu, proxy, vùng mạng và chính sách warm-up có thể đối chiếu. Chạy lặp để đánh giá biến động; không suy ra bottleneck Java từ p95 Internet. Muốn kết luận bottleneck cần dữ liệu CPU/RAM/GC/JDBC/SQL/cache cùng cửa sổ đo phía backend.

## 6. Điều kiện chuyển trạng thái

- **READY TO RUN:** tooling được kiểm tra và cấu hình vận hành đã sẵn sàng; không có nghĩa benchmark đã chạy.
- **EXECUTED:** có execution/log/CSV/HTML thật; ghi rõ local hay Kaggle. Một run lỗi có thể đã được thực thi nhưng không đạt workload.
- **VERIFIED (Kaggle CPU):** người review xác nhận runtime Kaggle CPU/settings, evidence môi trường/revision/target, đủ users/duration, artifacts nhất quán và lỗi đã phân tích. Không dùng riêng biến LOAD_TEST_ORIGIN hoặc CPU_CONFIRMED làm bằng chứng.

Tại thời điểm audit ban đầu chưa có URL HTTPS hoặc phiên Kaggle: trạng thái là **NOT RUN**. Lần thử sau khi được cung cấp quyền truy cập được ghi bên dưới. Unit tests, validate notebook hoặc tổng hợp lại dữ liệu local chỉ xác nhận tooling, không xác nhận benchmark Kaggle.

### Lần thử ban đầu ngày 15/10/2026 — lịch sử trước khi bật Internet

Người dùng đã cung cấp backend HTTPS và chỉ duyệt Smoke (1 user, spawn 1/s, 30 giây). Preflight HTTPS từ Windows đã PASS; kết quả này không xác nhận đường mạng từ Kaggle.

Đã mở notebook do người dùng cung cấp, import bundle scripts không có secrets, xác nhận Accelerator=None và khởi động Draft Session. Người dùng xác nhận đã thêm LOAD_TEST_PASSWORD và cấp quyền cho notebook. Bước cài Locust trong runtime Kaggle báo `Temporary failure in name resolution`.

Session options hiển thị **Internet off**, checkbox bị disabled và liên kết **Get phone verified**. Do đó tài khoản phải hoàn tất phone verification trước khi bật Internet. Người dùng tự thực hiện bước xác minh; không cung cấp số điện thoại hoặc mã SMS trong chat. Không thay firewall, không bypass hạn chế Kaggle và không chạy thay bằng máy local.

Đã hủy thực thi ở giai đoạn cài dependencies. Chưa chạy preflight từ Kaggle, prepare account hay Locust workload; chưa có CSV/HTML benchmark hoặc metrics mới. Trạng thái: **READY TO RUN — tooling; EXECUTED — runtime setup attempt only; Kaggle Smoke NOT RUN / NOT VERIFIED (account Internet prerequisite)**.

Editor cũng báo `Failed to save draft` và cảnh báo notebook có thể đã bị sửa ở nơi khác. Bundle local được giữ trong `load-testing/results/kaggle-smoke-ready.ipynb` (Git ignore); nếu refresh mất mã, import lại bundle sau khi xử lý xung đột phiên. Chỉ chạy tiếp khi Internet đã bật và notebook còn đúng cấu hình Smoke được duyệt.

## 7. Kết quả kiểm tra tooling trong phiên hoàn thiện

- Python 3.12.14: 16/16 unit tests PASS, gồm URL/TLS/redirect, identity isolation, timeout, đối chiếu metrics và không tự chứng nhận nguồn Kaggle.
- Notebook JSON schema, code-cell compilation và trạng thái chưa có execution/output: PASS cho notebook template trong Git. Notebook Smoke đã được thực thi sau đó trong Kaggle; xem kết quả bên dưới.
- Preflight read-only trên backend local HTTP: PASS (health/products); không phải kiểm tra đường HTTPS từ Kaggle.
- Tổng hợp artifacts local cũ: hai run có snapshot cuối được đọc hợp lệ; hai run cũ thiếu snapshot cuối bị đánh dấu INVALID/INCOMPLETE. Không sửa các artifacts hoặc số liệu cũ.
- Cache hit/miss: NOT MEASURED. Không suy diễn số liệu cache từ benchmark.

Hướng dẫn kết nối HTTPS ở trên là phương án chờ người vận hành phê duyệt, chưa triển khai. Maven/Docker benchmark kết quả trước đó vẫn được giữ tại load-testing.md; không chạy lại Maven trong lượt chỉ sửa tooling này.

Tham khảo chính thức: [Kaggle Notebook settings](https://www.kaggle.com/docs/notebooks), [Kaggle Secrets](https://github.com/Kaggle/kaggle-cli/blob/main/docs/kernels.md).

## 8. Kết quả Kaggle CPU thực tế — 15/10/2026

**READY TO RUN: PASS · EXECUTED: YES · VERIFIED: Kaggle CPU Smoke only.** Low/Medium/High: **NOT RUN trên Kaggle**.

Sau khi người dùng bật Internet và cho phép reload/khôi phục bản nháp, notebook chạy thành công. Artifact export chứa hai lượt Smoke liên tiếp; giữ và báo cáo cả hai, không chọn bỏ lượt nào. Không thực hiện thêm lượt benchmark sau khi nhận các kết quả này. Mỗi lượt dùng 1 user, spawn 1/s, cấu hình 30 giây; không có tải cao hơn.

Notebook: [Kaggle Smoke](https://www.kaggle.com/code/mangobird2710/notebook7c8920ab94/edit). UI được kiểm tra Accelerator **None**, Internet **on**. Runtime Interactive: Linux 6.18.48+, Python 3.13.15, Locust 2.46.7, 4 logical CPUs, memory 33,658,318,848 bytes (~31.35 GiB); CPU model chỉ ghi x86_64, không xác định SKU. Đây là máy phát tải; backend là hệ thống được kiểm thử, truy cập qua HTTPS Cloudflare URL do người dùng cung cấp. Không tạo tunnel mới, không expose MySQL/Redis.

| Run ID | Users peak | Duration đo (s) | Requests | RPS | Avg ms | p95 ms | p99 ms | Failures |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| smoke-20261015T042407-d168fdd5 | 1 | 29.843 | 23 | 0.7729 | 406.19 | 760 | 790 | 0 (0%) |
| smoke-20261015T042451-1c63356a | 1 | 29.909 | 24 | 0.8047 | 526.64 | 870 | 1200 | 0 (0%) |

Thời gian UTC: 04:24:13–04:24:45 và 04:24:55–04:25:27 (Bangkok UTC+7). Cấu hình Locust là 30 giây; cửa sổ đo thực tế ngắn hơn nhẹ, thời gian process gồm startup/shutdown dài hơn. Không gộp percentile hoặc cộng RPS giữa các lượt.

| Request | Run 1 HTTP 200 | Run 2 HTTP 200 |
|---|---:|---:|
| AUTH login | 1 | 1 |
| GET products | 9 | 3 |
| GET product detail | 5 | 6 |
| GET cart | 5 | 8 |
| POST cart item | 1 | 2 |
| PUT cart item | 1 | 2 |
| DELETE cart item | 1 | 2 |
| Total | 23 | 24 |

Tất cả HTTP status workload đều 200, DTO checks thành công. Preparation/register nằm ngoài số request đo. Mỗi run có tài khoản/cart riêng; không lưu password/JWT trong export. Preflight từ Kaggle PASS cho health/products, TLS verification bật và không follow redirect. Cả hai execution.json là COMPLETED, exit code 0. report.py chạy lại trên artifact tải về: 2 valid runs, 0 invalid/incomplete. Đã đối chiếu final-stats.csv, http-status.csv, run-metadata.json, summary.json và toàn bộ 7 script SHA256 khớp source local.

Source generator: `f384e4c+workingtree-e00b603dd34a` (snapshot chưa commit, không phải revision mới đã commit). Backend revision trong evidence là **unknown**, không tự khẳng định đang chạy commit nào. Không thay đổi raw summary: trường NOT VERIFIED do công cụ sinh tự động được giữ nguyên; kết luận VERIFIED ở báo cáo này dựa trên review UI và artifacts thực tế.

### Artifacts và khả năng kiểm tra lại

ZIP tải trực tiếp từ Kaggle: `cart-load-report-20261015T042451-1c63356a.zip`.
SHA256: `97eecf71737b0e2f5503c98e83a467327da49c511b33002594630e7f004b9ee9`.

Bản giải nén local: `load-testing/results/kaggle-20261015/` (Git ignored). Có CSV, Locust HTML/log, metrics.png, environment/connectivity/pip-freeze và comparison cho cả hai lượt. Ảnh bằng chứng UI: `kaggle-smoke-proof.jpg` trong cùng thư mục. Bản phân tích đối chiếu lại nằm ở `load-testing/results/kaggle-reviewed-analysis/`. Không public notebook/ZIP hoặc commit artifacts thô tự động; cần lưu archive riêng nếu chuyển máy vì các kết quả nằm ngoài Git.

### Giới hạn kết luận

Smoke xác nhận workload login/read/write hoạt động từ Kaggle CPU qua HTTPS dưới tải nhỏ. Không xác nhận SLA, capacity, database durability hay cache invalidation riêng biệt. Latency bao gồm Internet/Cloudflare và backend; số mẫu ít nên p95/p99 chỉ mô tả hai lượt này. Cache hit/miss **NOT MEASURED**. Không có telemetry backend CPU/RAM/GC/JDBC/Redis cùng cửa sổ; không suy luận bottleneck. Low/Medium/High chưa được duyệt và chưa chạy trên Kaggle. Test accounts vẫn còn trong database; không tự xóa bằng SQL.

Kiểm tra cuối sau khi tải artifacts: Python unit tests 16/16 PASS, notebook schema/code syntax PASS, git diff --check PASS. Lần gọi tests đầu gặp PermissionError ở Windows sandbox Temp; chạy lại với TEMP/TMP trỏ vào target/python-test-temp đã PASS, không đổi code để che lỗi. thread-dump.txt giữ nguyên SHA256. Không commit, push hoặc tạo PR.

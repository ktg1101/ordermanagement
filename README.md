# Order Management

Hệ thống quản lý đơn hàng. Java 21, Spring Boot 4.2.0, PostgreSQL, Flyway.

## Cấu trúc package

Mỗi module nghiệp vụ gồm 4 lớp: `api` / `application` / `domain` / `infrastructure`.

```
com.ordermanagement
├── OrderManagementApplication
├── user/            users, roles
├── inventory/       products, tồn kho
├── order/           orders, order_items
├── payment/         payments
└── shared/
    ├── config/      bean dùng chung (Clock)
    ├── exception/   ApiException + GlobalExceptionHandler (application/problem+json)
    ├── security/    mã hóa mật khẩu; chỗ đặt xác thực/phân quyền ở phase sau
    └── common/      phân trang, entity gốc có audit
```

| Lớp | Chứa gì | Được phụ thuộc vào |
|---|---|---|
| `api` | controller, request DTO | `application`, `domain` (enum) cùng module |
| `application` | use case (`*Service`), view/command trả ra ngoài, sự kiện | `domain`, `infrastructure` cùng module; `application` của module khác |
| `domain` | entity, state machine, quy tắc nghiệp vụ | chỉ `shared` |
| `infrastructure` | Spring Data JPA repository, listener sự kiện | `domain`, `application` cùng module |

Quy tắc giao tiếp giữa các module (được `ArchitectureTests` kiểm tra tự động):

- Module chỉ gọi module khác qua lớp `application` của nó, không đụng vào `domain`/`infrastructure`/`api`.
- Thứ tự phụ thuộc: `payment → order → (user, inventory)`. Không có phụ thuộc vòng.
- `order` không biết `payment`; khi hủy đơn nó chỉ phát `OrderCancelledEvent`, `payment` lắng nghe để hủy hoặc hoàn tiền.
- Sản phẩm (`products`) nằm trong module `inventory` vì cấu trúc dự án không có module `product` riêng.

## Môi trường (application.yml)

| Profile | Dùng cho | DB |
|---|---|---|
| `dev` (mặc định) | chạy từ IDE / `./mvnw` | `localhost:5432`, có giá trị mặc định |
| `prod` | Docker Compose / triển khai | bắt buộc `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |

Chọn profile bằng `SPRING_PROFILES_ACTIVE`. Schema do Flyway quản lý (`db/migration`), Hibernate chỉ `validate`.

## Chạy

```bash
# Chạy toàn bộ bằng Docker
cp .env.example .env
docker compose up --build
# Giao diện test:  http://localhost:3000   (cổng đổi bằng FRONTEND_PORT)
# API trực tiếp:   http://localhost:8080

# Chạy local (PostgreSQL bằng Docker, app bằng Maven)
docker compose up -d postgres
./mvnw spring-boot:run
```

Health: `GET /actuator/health`, `GET /actuator/health/readiness`.

> `compose.yaml` publish PostgreSQL ra cổng `${POSTGRES_PORT:-5431}`; `.env.example` đặt `5432` khớp với
> mặc định `dev`. Nếu dùng cổng 5431, đặt `DB_URL=jdbc:postgresql://localhost:5431/ordermanagement`
> (kèm `DB_USERNAME`, `DB_PASSWORD`) khi chạy local.

## Test

```bash
docker compose up -d postgres
./mvnw verify
```

| Test | Cần PostgreSQL | Kiểm tra |
|---|---|---|
| `DomainRulesTests` | không | state machine đơn hàng/thanh toán, tổng tiền, quy tắc domain |
| `ArchitectureTests` | không | quy tắc phụ thuộc giữa module và giữa các lớp |
| `OrderFlowIntegrationTests` | có | nghiệp vụ: đặt hàng, giữ/trả/xuất kho, thanh toán, hủy, hoàn tiền, idempotency, đặt song song không bán vượt tồn |
| `ApiContractTests` | có | REST qua HTTP: status code, lỗi `problem+json` có `code`, validation, luồng đầu-cuối |
| `HealthEndpointTests`, `OrderManagementApplicationTests` | có | health, kết nối DB, Flyway + Hibernate `validate` |

Integration test chỉ tạo và xóa dữ liệu có tiền tố riêng (`it-...@test.invalid`, SKU `IT-...`), không đụng dữ liệu dev có sẵn.

## REST API (`/api/v1`)

Lỗi luôn có dạng `application/problem+json` với `status`, `detail` và `code` máy đọc được
(ví dụ `INSUFFICIENT_STOCK`); lỗi validation có thêm `errors[{field, message}]`.

| Nhóm | Endpoint |
|---|---|
| User | `POST /users`, `GET /users`, `GET /users/{id}`, `PUT /users/{id}`, `DELETE /users/{id}` (khóa tài khoản), `POST /users/{id}/enable` |
| Product | `POST /products`, `GET /products`, `GET /products/{id}`, `PUT /products/{id}`, `DELETE /products/{id}` (ngừng bán) |
| Inventory | `GET /inventory/{productId}`, `POST /inventory/{productId}/adjustments` |
| Order | `POST /orders`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`, `POST /orders/{id}/complete` |
| Payment | `POST /orders/{orderId}/payments` (bắt buộc header `Idempotency-Key`), `GET /orders/{orderId}/payments`, `GET /payments/{id}`, `POST /payments/{id}/submit`, `/callback`, `/cancel` |

## Nghiệp vụ

**Đơn hàng:** `PENDING → CONFIRMED → COMPLETED`, hoặc `CANCELLED` từ `PENDING`/`CONFIRMED`.
Giá và tên sản phẩm được chụp vào dòng đơn lúc đặt nên đổi giá sau đó không ảnh hưởng đơn cũ.

**Tồn kho** (`available` / `reserved`):

| Sự kiện | Tác động |
|---|---|
| Tạo đơn | `available -= q`, `reserved += q` (thiếu hàng ⇒ 409 `INSUFFICIENT_STOCK`, rollback cả đơn) |
| Hủy đơn | `reserved -= q`, `available += q` |
| Hoàn tất đơn | `reserved -= q` (xuất kho) |

Mỗi thay đổi là một câu `UPDATE` có điều kiện trong `WHERE` nên không thể âm hay bán vượt khi nhiều request tranh nhau;
các dòng được khóa theo thứ tự `productId` để hai đơn nhiều sản phẩm không deadlock.

**Thanh toán:** `PENDING → PROCESSING → SUCCEEDED | FAILED`, `CANCELLED`, `REFUNDED`.
Mỗi đơn có tối đa một payment đang hoạt động và tối đa một `SUCCEEDED` (partial unique index ở DB).
Callback `SUCCEEDED` đưa payment và đơn sang `CONFIRMED` trong cùng một transaction; `FAILED` giữ đơn `PENDING`
và giữ hàng để khách thử lại bằng payment mới. Hủy đơn đã thanh toán sẽ chuyển payment sang `REFUNDED`.

**Transaction:** mỗi use case ghi là một transaction. Mọi thao tác đổi trạng thái đơn hoặc thanh toán đều khóa dòng
`orders` trước (`SELECT ... FOR UPDATE`) nên hủy đơn và callback thanh toán trên cùng một đơn chạy tuần tự.

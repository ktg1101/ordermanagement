INSERT INTO roles (id, code, name)
VALUES
    (
        '00000000-0000-0000-0000-000000000001',
        'CUSTOMER',
        'Customer'
    ),
    (
        '00000000-0000-0000-0000-000000000002',
        'ADMIN',
        'Administrator'
    )
    ON CONFLICT (code) DO NOTHING;

-- Tài khoản seed bị vô hiệu hóa, không thể dùng để đăng nhập.
INSERT INTO users (
    id,
    email,
    password_hash,
    full_name,
    status
)
VALUES (
           '00000000-0000-0000-0000-000000000010',
           'seed-disabled@example.invalid',
           '!seed-only-no-login!',
           'Seed Disabled User',
           'DISABLED'
       )
    ON CONFLICT (email) DO NOTHING;

-- Sản phẩm chỉ phục vụ dữ liệu tham chiếu local, không thể bán.
INSERT INTO products (
    id,
    sku,
    name,
    description,
    price,
    active
)
VALUES (
           '00000000-0000-0000-0000-000000000020',
           'SEED-DO-NOT-SELL',
           'Seed product',
           'Reference data for local development only.',
           0.00,
           false
       )
    ON CONFLICT (sku) DO NOTHING;

INSERT INTO inventory (
    product_id,
    available_quantity,
    reserved_quantity
)
VALUES (
           '00000000-0000-0000-0000-000000000020',
           0,
           0
       )
    ON CONFLICT (product_id) DO NOTHING;
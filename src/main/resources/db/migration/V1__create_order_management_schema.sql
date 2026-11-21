CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
                       id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                       email varchar(320) NOT NULL,
                       password_hash varchar(255) NOT NULL,
                       full_name varchar(150) NOT NULL,
                       status varchar(20) NOT NULL DEFAULT 'ACTIVE',
                       created_at timestamptz NOT NULL DEFAULT now(),
                       updated_at timestamptz NOT NULL DEFAULT now(),
                       CONSTRAINT uk_users_email UNIQUE (email),
                       CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE roles (
                       id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                       code varchar(50) NOT NULL,
                       name varchar(100) NOT NULL,
                       CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE user_roles (
                            user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                            role_id uuid NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
                            PRIMARY KEY (user_id, role_id)
);

CREATE TABLE products (
                          id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                          sku varchar(64) NOT NULL,
                          name varchar(200) NOT NULL,
                          description varchar(2000),
                          price numeric(19, 2) NOT NULL,
                          active boolean NOT NULL DEFAULT true,
                          created_at timestamptz NOT NULL DEFAULT now(),
                          updated_at timestamptz NOT NULL DEFAULT now(),
                          CONSTRAINT uk_products_sku UNIQUE (sku),
                          CONSTRAINT ck_products_price_nonnegative CHECK (price >= 0)
);

CREATE TABLE inventory (
                           product_id uuid PRIMARY KEY REFERENCES products(id) ON DELETE RESTRICT,
                           available_quantity integer NOT NULL DEFAULT 0,
                           reserved_quantity integer NOT NULL DEFAULT 0,
                           version bigint NOT NULL DEFAULT 0,
                           updated_at timestamptz NOT NULL DEFAULT now(),
                           CONSTRAINT ck_inventory_available_nonnegative
                               CHECK (available_quantity >= 0),
                           CONSTRAINT ck_inventory_reserved_nonnegative
                               CHECK (reserved_quantity >= 0)
);

CREATE TABLE orders (
                        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                        order_number varchar(40) NOT NULL,
                        user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                        status varchar(20) NOT NULL DEFAULT 'PENDING',
                        total_amount numeric(19, 2) NOT NULL,
                        currency char(3) NOT NULL DEFAULT 'VND',
                        created_at timestamptz NOT NULL DEFAULT now(),
                        updated_at timestamptz NOT NULL DEFAULT now(),

                        CONSTRAINT uk_orders_order_number UNIQUE (order_number),
                        CONSTRAINT ck_orders_status
                            CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
                        CONSTRAINT ck_orders_total_nonnegative
                            CHECK (total_amount >= 0),
                        CONSTRAINT ck_orders_currency_format
                            CHECK (currency ~ '^[A-Z]{3}$')
    );

CREATE TABLE order_items (
                             id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                             order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
                             product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,

                             product_sku varchar(64) NOT NULL,
                             product_name varchar(200) NOT NULL,
                             unit_price numeric(19, 2) NOT NULL,
                             quantity integer NOT NULL,

                             line_total numeric(19, 2)
                                 GENERATED ALWAYS AS (unit_price * quantity) STORED,

                             CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
                             CONSTRAINT ck_order_items_unit_price_nonnegative
                                 CHECK (unit_price >= 0),
                             CONSTRAINT ck_order_items_quantity_positive
                                 CHECK (quantity > 0)
);

CREATE TABLE payments (
                          id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                          order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
                          attempt_number integer NOT NULL,

                          status varchar(20) NOT NULL DEFAULT 'PENDING',
                          amount numeric(19, 2) NOT NULL,
                          currency char(3) NOT NULL DEFAULT 'VND',

                          provider varchar(50) NOT NULL,
                          provider_reference varchar(150),
                          idempotency_key varchar(100) NOT NULL,
                          failure_code varchar(100),

                          created_at timestamptz NOT NULL DEFAULT now(),
                          updated_at timestamptz NOT NULL DEFAULT now(),

                          CONSTRAINT uk_payments_order_attempt UNIQUE (order_id, attempt_number),
                          CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
                          CONSTRAINT ck_payments_status
                              CHECK (status IN (
                                                'PENDING',
                                                'PROCESSING',
                                                'SUCCEEDED',
                                                'FAILED',
                                                'CANCELLED',
                                                'REFUNDED'
                                  )),
                          CONSTRAINT ck_payments_amount_positive
                              CHECK (amount > 0),
                          CONSTRAINT ck_payments_currency_format
                              CHECK (currency ~ '^[A-Z]{3}$')
    );
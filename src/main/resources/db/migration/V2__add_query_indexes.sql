CREATE INDEX ix_user_roles_role_id
    ON user_roles(role_id);

CREATE INDEX ix_products_active_name
    ON products(active, name);

CREATE INDEX ix_orders_user_created_at
    ON orders(user_id, created_at DESC);

CREATE INDEX ix_orders_status_created_at
    ON orders(status, created_at DESC);

CREATE INDEX ix_order_items_order_id
    ON order_items(order_id);

CREATE INDEX ix_order_items_product_id
    ON order_items(product_id);

CREATE INDEX ix_payments_order_created_at
    ON payments(order_id, created_at DESC);

CREATE INDEX ix_payments_status_created_at
    ON payments(status, created_at DESC);

-- Mỗi order chỉ được có tối đa một payment thành công.
CREATE UNIQUE INDEX ux_payments_one_success_per_order
    ON payments(order_id)
    WHERE status = 'SUCCEEDED';
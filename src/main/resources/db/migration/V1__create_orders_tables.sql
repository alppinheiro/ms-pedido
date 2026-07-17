CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    order_date TIMESTAMP WITH TIME ZONE NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(19, 2) NOT NULL,
    CONSTRAINT fk_order_items_order_id FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);


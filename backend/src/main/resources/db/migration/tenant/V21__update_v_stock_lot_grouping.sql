DROP VIEW IF EXISTS v_stock;

CREATE VIEW v_stock AS
SELECT location_id,
       product_id,
       lot_number,
       SUM(qty) AS qty_stock
FROM stock_movements
GROUP BY location_id, product_id, lot_number
HAVING SUM(qty) <> 0;
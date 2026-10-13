#!/bin/bash
# One MySQL server, three databases: each service gets its own database and its own user, and no
# user can read another service's tables. Runs once, when the MySQL volume is first created.
set -e
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS buchhaus_users;
CREATE DATABASE IF NOT EXISTS buchhaus_catalog;
CREATE DATABASE IF NOT EXISTS buchhaus_orders;
CREATE USER IF NOT EXISTS 'user_svc'@'%' IDENTIFIED BY '${USERS_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'catalog_svc'@'%' IDENTIFIED BY '${CATALOG_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'order_svc'@'%' IDENTIFIED BY '${ORDERS_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON buchhaus_users.* TO 'user_svc'@'%';
GRANT ALL PRIVILEGES ON buchhaus_catalog.* TO 'catalog_svc'@'%';
GRANT ALL PRIVILEGES ON buchhaus_orders.* TO 'order_svc'@'%';
FLUSH PRIVILEGES;
SQL

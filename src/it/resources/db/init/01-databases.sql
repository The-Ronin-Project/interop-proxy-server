# tenant-db
CREATE DATABASE IF NOT EXISTS `tenant-db`;
CREATE USER 'ehr'@'%' IDENTIFIED BY 'ThePassword';
GRANT ALL PRIVILEGES ON `tenant-db`.* TO 'ehr'@'%';

# queue-db
CREATE DATABASE IF NOT EXISTS `queue-db`;
CREATE USER 'queueuser'@'%' IDENTIFIED BY 'ThePassword';
GRANT ALL PRIVILEGES ON `queue-db`.* TO 'queueuser'@'%';

# mockehr
CREATE DATABASE IF NOT EXISTS `mock-ehr-db`;
CREATE USER 'springuser'@'%' IDENTIFIED BY 'ThePassword';
GRANT ALL PRIVILEGES ON `mock-ehr-db`.* TO 'springuser'@'%';

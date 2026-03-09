use mysql;
create database if not exists TartanHome;
create user if not exists 'tartan'@'%' identified by 'tartan1234';
grant all on TartanHome.* to 'tartan'@'%';
flush privileges;
drop table if exists ada_handle;
create table ada_handle
(
    name                 varchar(100) not null,
    stake_address        varchar(255),
    primary key (name)
);

CREATE INDEX idx_adahandle_stake_address
    ON ada_handle(stake_address);

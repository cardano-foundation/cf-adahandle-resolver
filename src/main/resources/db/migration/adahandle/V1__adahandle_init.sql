drop table if exists ada_handle;
drop table if exists ada_handle_history;

create table ada_handle
(
    name                 varchar(100) not null,
    stake_address        varchar(255),
    payment_address      varchar(255) not null,
    primary key (name)
);

create table ada_handle_history_item
(
    id                   serial primary key,
    name                 varchar(100) not null,
    stake_address        varchar(255),
    payment_address      varchar(255) not null,
    slot                 bigint not null
);

CREATE INDEX idx_ada_handle_stake_address ON ada_handle(stake_address);
CREATE INDEX idx_ada_handle_payment_address ON ada_handle(payment_address);

CREATE INDEX idx_ada_handle_history_item_slot ON ada_handle_history_item(slot);

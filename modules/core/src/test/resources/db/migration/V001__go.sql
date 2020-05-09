create extension "uuid-ossp";

create table company
(
    id   UUID PRIMARY KEY,
    name TEXT NOT NULL
);

create table department
(
    id         UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    name       TEXT NOT NULL
);

create table employee
(
    id            UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES department (id),
    name          TEXT NOT NULL
);

create table spending
(
    id            UUID PRIMARY KEY,
    department_id UUID NOT NULL references department (id),
    amount        Int  NOT NULL
);

insert into company (id, name)
values ('00000000-0000-0000-0000-100000000000', 'Com1'),
       ('00000000-0000-0000-0000-200000000000', 'Com2');

insert into department (id, company_id, name)
values ('00000000-0000-0000-0000-101000000000', '00000000-0000-0000-0000-100000000000', 'Dep1'),
       ('00000000-0000-0000-0000-102000000000', '00000000-0000-0000-0000-100000000000', 'Dep2'),
       ('00000000-0000-0000-0000-203000000000', '00000000-0000-0000-0000-200000000000', 'Dep3');

insert into employee (id, department_id, name)
values ('00000000-0000-0000-0000-101010000000', '00000000-0000-0000-0000-101000000000', 'Emp1'),
       ('00000000-0000-0000-0000-101020000000', '00000000-0000-0000-0000-102000000000', 'Emp2'),
       ('00000000-0000-0000-0000-203030000000', '00000000-0000-0000-0000-203000000000', 'Emp3');

insert into spending (id, department_id, amount)
values ('00000000-0000-0000-0000-101000100000', '00000000-0000-0000-0000-101000000000', 1),
       ('00000000-0000-0000-0000-101000200000', '00000000-0000-0000-0000-101000000000', 2),
       ('00000000-0000-0000-0000-102000300000', '00000000-0000-0000-0000-102000000000', 3);



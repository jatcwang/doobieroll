CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE company (
  company_id UUID PRIMARY KEY,
  name TEXT NOT NULL
);

CREATE TABLE department (
  department_id UUID PRIMARY KEY,
  company_id UUID NOT NULL REFERENCES company(company_id),
  name TEXT NOT NULL
);

CREATE INDEX ON department(company_id);

CREATE TABLE employee (
  employee_id UUID PRIMARY KEY,
  department_id UUID NOT NULL REFERENCES department(department_id),
  name TEXT NOT NULL
);

CREATE INDEX ON employee(department_id);

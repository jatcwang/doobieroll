INSERT INTO company
(company_id, name)
SELECT
  ('00000000-0000-' || to_char(generate_series, 'fm0000') || '-0000-000000000000')::uuid,
  'Company ' || generate_series
FROM generate_series(1, 10);

INSERT INTO department
(company_id, department_id, name)
SELECT
  company_id,
  (substring(company_id::text for 19) || to_char(generate_series, 'fm0000') || '-000000000000')::uuid,
  company.name || ' Department ' || generate_series
FROM company, generate_series(1, 20);

INSERT INTO employee
(department_id, employee_id, name)
SELECT
  department_id,
  (substring(department_id::text for 24) || to_char(generate_series, 'fm000000000000'))::uuid,
  department.name || ' Employee ' || generate_series
FROM department, generate_series(1, 50);

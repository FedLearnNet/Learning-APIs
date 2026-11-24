-- clients connected at fire time; the result shows once all answered, at the latest at the deadline
alter table queries add column expected_answers integer;

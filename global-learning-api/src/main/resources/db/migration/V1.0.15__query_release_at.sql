-- query results stay hidden until this time, client answers after it are dropped
alter table queries add column release_at timestamp(6);

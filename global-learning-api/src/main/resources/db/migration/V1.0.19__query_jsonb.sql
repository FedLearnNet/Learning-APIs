-- the query tree is mapped as JSON, an empty string means no tree
alter table queries alter column query_string type jsonb using nullif(query_string, '')::jsonb;

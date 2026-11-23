-- clients send no reason with a rejected query, it could describe their data
alter table query_result drop column error;
alter table queries drop column error;

alter table ingredient_masters
    add column pyramid varchar(20),
    add column profile_json longtext,
    add column price_per_kg double,
    add column price_currency varchar(20),
    add column risk_tier integer;
